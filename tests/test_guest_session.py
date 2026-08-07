from fastapi.testclient import TestClient

from langou_backend.main import create_app


def test_guest_session_returns_short_lived_access_and_rotating_refresh_tokens() -> None:
    client = TestClient(create_app(environment="test"))

    response = client.post(
        "/v1/devices/guest-session",
        json={
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
            "platform": "android",
            "app_version": "1.0.0",
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["token_type"] == "bearer"  # noqa: S105 - OAuth scheme name
    assert body["expires_in"] == 900
    assert body["subject_type"] == "guest"
    assert body["access_token"] != body["refresh_token"]
    assert len(body["access_token"]) > 40
    assert len(body["refresh_token"]) > 40
