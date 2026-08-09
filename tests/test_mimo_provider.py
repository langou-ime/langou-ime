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
    assert all(payload["max_tokens"] == 256 for payload in requested_payloads)
    assert all(payload["stream"] is True for payload in requested_payloads)
    assert "朋友；对方喜欢提前约时间。" in requested_payloads[-1]["messages"][-1]["content"]
    assert [item.style for item in result] == ["natural", "gentle", "boundary"]
    assert [item.text for item in result] == [
        "好呀，我安排一下时间",
        "当然可以，很期待见到你～",
        "今晚不方便，我们改天好吗？",
    ]
    await client.aclose()
