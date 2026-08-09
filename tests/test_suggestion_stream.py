import json

from fastapi.testclient import TestClient

from langou_backend.guards import RateLimitExceeded
from langou_backend.main import create_app
from langou_backend.suggestions import SuggestionUnavailable


def guest_headers(client: TestClient) -> dict[str, str]:
    response = client.post(
        "/v1/devices/guest-session",
        json={
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
            "platform": "android",
            "app_version": "1.0.0",
        },
    )
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def suggestion_payload() -> dict:
    return {
        "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
        "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
        "application": "wechat",
        "locale": "zh-CN",
        "turns": [{"role": "other", "text": "今晚有空一起吃饭吗？"}],
        "save_history": False,
    }


def parse_sse(body: str) -> list[tuple[str, dict]]:
    events = []
    for block in body.strip().split("\n\n"):
        lines = block.splitlines()
        event = lines[0].removeprefix("event: ")
        data = json.loads(lines[1].removeprefix("data: "))
        events.append((event, data))
    return events


def test_suggestion_stream_requires_access_token() -> None:
    client = TestClient(create_app(environment="test"))

    response = client.post("/v1/ai/suggestions:stream", json=suggestion_payload())

    assert response.status_code == 401


def test_suggestion_stream_emits_meta_three_suggestions_and_done() -> None:
    client = TestClient(create_app(environment="test"))

    response = client.post(
        "/v1/ai/suggestions:stream",
        json=suggestion_payload(),
        headers=guest_headers(client),
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    events = parse_sse(response.text)
    assert [name for name, _ in events] == [
        "meta",
        "suggestion",
        "suggestion",
        "suggestion",
        "done",
    ]
    assert events[0][1]["request_id"] == suggestion_payload()["request_id"]
    assert [item[1]["style"] for item in events[1:4]] == [
        "natural",
        "gentle",
        "boundary",
    ]


def test_suggestion_stream_returns_memory_after_replies_without_blocking_first() -> None:
    class SummarizingProvider:
        async def generate(self, request):
            del request
            from langou_backend.suggestions import Suggestion

            yield Suggestion(style="natural", text="好呀")
            yield Suggestion(style="gentle", text="听起来不错")

        async def summarize(self, request):
            assert request.conversation_id == "conv_0123456789abcdef"
            return "朋友；喜欢轻松简短的回复；仍在确认今晚时间。"

    payload = suggestion_payload()
    payload["conversation_id"] = "conv_0123456789abcdef"
    client = TestClient(
        create_app(environment="test", suggestion_provider=SummarizingProvider())
    )

    response = client.post(
        "/v1/ai/suggestions:stream",
        json=payload,
        headers=guest_headers(client),
    )

    events = parse_sse(response.text)
    assert [name for name, _ in events] == [
        "meta",
        "suggestion",
        "suggestion",
        "memory",
        "done",
    ]
    assert events[-2][1] == {
        "conversation_id": "conv_0123456789abcdef",
        "summary": "朋友；喜欢轻松简短的回复；仍在确认今晚时间。",
    }


def test_summary_failure_does_not_turn_successful_replies_into_an_error() -> None:
    class FailingSummaryProvider:
        async def generate(self, request):
            del request
            from langou_backend.suggestions import Suggestion

            yield Suggestion(style="natural", text="好呀")

        async def summarize(self, request):
            del request
            raise SuggestionUnavailable

    payload = suggestion_payload()
    payload["conversation_id"] = "conv_0123456789abcdef"
    client = TestClient(
        create_app(environment="test", suggestion_provider=FailingSummaryProvider())
    )

    response = client.post(
        "/v1/ai/suggestions:stream",
        json=payload,
        headers=guest_headers(client),
    )

    assert [name for name, _ in parse_sse(response.text)] == [
        "meta",
        "suggestion",
        "done",
    ]


def test_suggestion_meta_does_not_claim_the_development_model() -> None:
    class ProductionLikeProvider:
        async def generate(self, request):
            del request
            if False:
                yield

    client = TestClient(
        create_app(
            environment="test",
            suggestion_provider=ProductionLikeProvider(),
        )
    )

    response = client.post(
        "/v1/ai/suggestions:stream",
        json=suggestion_payload(),
        headers=guest_headers(client),
    )

    meta = parse_sse(response.text)[0][1]
    assert meta == {"request_id": suggestion_payload()["request_id"]}


def test_suggestion_stream_returns_retryable_error_without_breaking_transport() -> None:
    class FailingProvider:
        async def generate(self, request):
            del request
            if False:
                yield
            raise SuggestionUnavailable

    client = TestClient(
        create_app(environment="test", suggestion_provider=FailingProvider())
    )

    response = client.post(
        "/v1/ai/suggestions:stream",
        json=suggestion_payload(),
        headers=guest_headers(client),
    )

    assert response.status_code == 200
    assert [name for name, _ in parse_sse(response.text)] == ["meta", "error"]
    assert parse_sse(response.text)[-1][1] == {
        "code": "suggestion_unavailable",
        "retryable": True,
    }


def test_suggestion_stream_applies_abuse_guard_without_showing_quota() -> None:
    class ClosedGate:
        async def acquire(self, subject, request_id):
            del subject, request_id
            raise RateLimitExceeded

    client = TestClient(create_app(environment="test", suggestion_gate=ClosedGate()))

    response = client.post(
        "/v1/ai/suggestions:stream",
        json=suggestion_payload(),
        headers=guest_headers(client),
    )

    events = parse_sse(response.text)
    assert [name for name, _ in events] == ["meta", "error"]
    assert events[-1][1] == {"code": "rate_limited", "retryable": True}
    assert "quota" not in response.text
    assert "limit" not in response.text.replace("rate_limited", "")
