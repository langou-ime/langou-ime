from fastapi.testclient import TestClient

from langou_backend.main import create_app


def authorize(client: TestClient) -> tuple[str, dict[str, str]]:
    device_id = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP"
    response = client.post(
        "/v1/devices/guest-session",
        json={"device_id": device_id, "platform": "android", "app_version": "1.0.0"},
    )
    return device_id, {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_settings_default_to_auto_ai_and_allow_disabling_cloud_history() -> None:
    client = TestClient(create_app(environment="test"))
    device_id, headers = authorize(client)

    defaults = client.get("/v1/settings", headers=headers)
    updated = client.put(
        "/v1/settings",
        headers=headers,
        json={
            "theme": "moon",
            "auto_suggest": True,
            "save_history": False,
            "diagnostics": False,
        },
    )
    client.post(
        "/v1/ai/suggestions:stream",
        headers=headers,
        json={
            "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
            "device_id": device_id,
            "application": "wechat",
            "locale": "zh-CN",
            "turns": [{"role": "other", "text": "晚点聊"}],
            "save_history": True,
        },
    )

    assert defaults.status_code == 200
    assert defaults.json() == {
        "theme": "cream",
        "auto_suggest": True,
        "save_history": True,
        "diagnostics": False,
    }
    assert updated.status_code == 200
    assert updated.json()["theme"] == "moon"
    assert client.get("/v1/history", headers=headers).json()["items"] == []

