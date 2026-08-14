from fastapi.testclient import TestClient

from langou_backend.main import create_app


def test_sms_send_never_returns_verification_code_and_rate_limits_repeats() -> None:
    client = TestClient(create_app(environment="test"))
    payload = {"phone": "+8613800138000"}

    first = client.post("/v1/auth/sms/send", json=payload)
    second = client.post("/v1/auth/sms/send", json=payload)

    assert first.status_code == 202
    assert first.json() == {"status": "sent", "retry_after": 60}
    assert "code" not in first.text.lower()
    assert second.status_code == 429
    assert second.json()["detail"] == "sms_cooldown"

