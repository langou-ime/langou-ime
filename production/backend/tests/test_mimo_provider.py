import asyncio
import json
from importlib import import_module

import httpx
import pytest

from langou_backend.schemas import SuggestionRequest


class FragmentedOpenAiStream(httpx.AsyncByteStream):
    def __init__(self, chunks: list[str]) -> None:
        self._chunks = chunks

    async def __aiter__(self):
        for chunk in self._chunks:
            yield chunk.encode()


class DelayedOpenAiStream(httpx.AsyncByteStream):
    def __init__(self, delay_seconds: float, chunks: list[str]) -> None:
        self._delay_seconds = delay_seconds
        self._chunks = chunks

    async def __aiter__(self):
        await asyncio.sleep(self._delay_seconds)
        for chunk in self._chunks:
            yield chunk.encode()


@pytest.mark.asyncio
async def test_mimo_provider_ignores_terminal_usage_event_with_empty_choices() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        del request
        return httpx.Response(
            200,
            headers={"content-type": "text/event-stream"},
            stream=FragmentedOpenAiStream(
                [
                    'data: {"choices":[{"delta":{"content":null}}]}\n\n',
                    'data: {"choices":[{"delta":{"content":'
                    '"natural<TAB>好呀\\ngentle<TAB>可以呀～\\n'
                    'boundary<TAB>我先看看时间"}}]}\n\n',
                    'data: {"choices":[],"usage":{"total_tokens":42}}\n\n',
                    "data: [DONE]\n\n",
                ]
            ),
        )

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="https://token-plan-cn.xiaomimimo.com/v1/",
    )
    provider = import_module("langou_backend.suggestions").MimoSuggestionProvider(
        client=client,
        primary_model="mimo-v2.5-pro",
        fallback_model="mimo-v2.5",
    )
    request = SuggestionRequest.model_validate(
        {
            "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
            "application": "wechat",
            "locale": "zh-CN",
            "turns": [{"role": "other", "text": "今晚一起吃饭吗？"}],
            "save_history": False,
        }
    )

    result = [item async for item in provider.generate(request)]

    assert [item.text for item in result] == ["好呀", "可以呀～", "我先看看时间"]
    await client.aclose()


@pytest.mark.asyncio
async def test_mimo_provider_falls_back_and_normalizes_three_styles() -> None:
    try:
        suggestions = import_module("langou_backend.suggestions")
    except ModuleNotFoundError:
        pytest.fail("langou_backend.suggestions has not been implemented")
    assert hasattr(suggestions, "MimoSuggestionProvider"), "MiMo provider is not implemented"

    requested_models = []
    requested_payloads = []

    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        requested_models.append(payload["model"])
        requested_payloads.append(payload)
        if payload["model"] == "mimo-v2.5-pro":
            return httpx.Response(503, json={"error": "temporarily_unavailable"})
        return httpx.Response(
            200,
            headers={"content-type": "text/event-stream"},
            stream=FragmentedOpenAiStream(
                [
                    'data: {"choices":[{"delta":{"content":"natural\\t好呀，"}}]}\n\n',
                    'data: {"choices":[{"delta":{"content":"我安排一下时间\\n"}}]}\n\n',
                    'data: {"choices":[{"delta":{"content":'
                    '"gentle\\t当然可以，很期待见到你～\\n"}}]}\n\n',
                    'data: {"choices":[{"delta":{"content":'
                    '"boundary\\t今晚不方便，我们改天好吗？\\n"}}]}\n\n',
                    "data: [DONE]\n\n",
                ]
            ),
        )

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="https://token-plan-cn.xiaomimimo.com/v1/",
        headers={"Authorization": "Bearer runtime-secret"},
    )
    provider = suggestions.MimoSuggestionProvider(
        client=client,
        primary_model="mimo-v2.5-pro",
        fallback_model="mimo-v2.5",
    )
    request = SuggestionRequest.model_validate(
        {
            "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
            "application": "wechat",
            "locale": "zh-CN",
            "turns": [{"role": "other", "text": "今晚一起吃饭吗？"}],
            "memory_summary": "朋友；对方喜欢提前约时间。",
            "save_history": False,
        }
    )
    result = [item async for item in provider.generate(request)]

    assert requested_models == ["mimo-v2.5-pro", "mimo-v2.5"]
    assert all(payload["thinking"] == {"type": "disabled"} for payload in requested_payloads)
    assert all(payload["max_tokens"] == 192 for payload in requested_payloads)
    assert all(payload["stream"] is True for payload in requested_payloads)
    assert "朋友；对方喜欢提前约时间。" in requested_payloads[-1]["messages"][-1]["content"]
    assert "最近一条来自 other 的消息" in requested_payloads[-1]["messages"][0]["content"]
    assert [item.style for item in result] == ["natural", "gentle", "boundary"]
    assert [item.text for item in result] == [
        "好呀，我安排一下时间",
        "当然可以，很期待见到你～",
        "今晚不方便，我们改天好吗？",
    ]
    await client.aclose()


@pytest.mark.asyncio
async def test_mimo_provider_returns_a_compact_summary_with_fallback() -> None:
    requested_models = []

    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        requested_models.append(payload["model"])
        assert payload["stream"] is False
        assert payload["max_tokens"] == 192
        assert "最近还在确认周末时间" in payload["messages"][-1]["content"]
        if payload["model"] == "mimo-v2.5-pro":
            return httpx.Response(503, json={"error": "temporarily_unavailable"})
        return httpx.Response(
            200,
            json={
                "choices": [
                    {
                        "message": {
                            "content": "朋友；偏好简短自然的回复；仍在确认周末时间。"
                        }
                    }
                ]
            },
        )

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="https://token-plan-cn.xiaomimimo.com/v1/",
    )
    provider = import_module("langou_backend.suggestions").MimoSuggestionProvider(
        client=client,
        primary_model="mimo-v2.5-pro",
        fallback_model="mimo-v2.5",
    )
    request = SuggestionRequest.model_validate(
        {
            "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
            "application": "wechat",
            "locale": "zh-CN",
            "turns": [{"role": "other", "text": "最近还在确认周末时间"}],
            "save_history": False,
        }
    )

    summary = await provider.summarize(request)

    assert requested_models == ["mimo-v2.5-pro", "mimo-v2.5"]
    assert summary == "朋友；偏好简短自然的回复；仍在确认周末时间。"
    await client.aclose()


@pytest.mark.asyncio
async def test_mimo_provider_falls_back_when_primary_first_suggestion_is_too_slow() -> None:
    suggestions = import_module("langou_backend.suggestions")
    requested_models = []

    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        requested_models.append(payload["model"])
        if payload["model"] == "mimo-v2.5-pro":
            return httpx.Response(
                200,
                headers={"content-type": "text/event-stream"},
                stream=DelayedOpenAiStream(
                    0.05,
                    [
                        'data: {"choices":[{"delta":{"content":"natural\\t主模型太慢了\\n"}}]}\n\n',
                        "data: [DONE]\n\n",
                    ],
                ),
            )
        return httpx.Response(
            200,
            headers={"content-type": "text/event-stream"},
            stream=FragmentedOpenAiStream(
                [
                    'data: {"choices":[{"delta":{"content":"natural\\t备用模型先回你\\n"}}]}\n\n',
                    'data: {"choices":[{"delta":{"content":'
                    '"gentle\\t我先给你一个更快的建议\\n"}}]}\n\n',
                    'data: {"choices":[{"delta":{"content":'
                    '"boundary\\t稍等我再看看更完整的内容\\n"}}]}\n\n',
                    "data: [DONE]\n\n",
                ]
            ),
        )

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="https://token-plan-cn.xiaomimimo.com/v1/",
    )
    provider = suggestions.MimoSuggestionProvider(
        client=client,
        primary_model="mimo-v2.5-pro",
        fallback_model="mimo-v2.5",
        first_suggestion_timeout_seconds=0.01,
    )
    request = SuggestionRequest.model_validate(
        {
            "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
            "application": "wechat",
            "locale": "zh-CN",
            "turns": [{"role": "other", "text": "今晚一起吃饭吗？"}],
            "save_history": False,
        }
    )

    result = [item async for item in provider.generate(request)]

    assert requested_models == ["mimo-v2.5-pro", "mimo-v2.5"]
    assert [item.text for item in result] == [
        "备用模型先回你",
        "我先给你一个更快的建议",
        "稍等我再看看更完整的内容",
    ]
    await client.aclose()
