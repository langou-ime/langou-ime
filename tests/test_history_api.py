from fastapi.testclient import TestClient

from langou_backend.main import create_app


def create_guest(client: TestClient, device_id: str) -> dict[str, str]:
    response = client.post(
        "/v1/devices/guest-session",
        json={
            "device_id": device_id,
            "platform": "android",
            "app_version": "1.0.0",
        },
    )
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_saved_history_is_scoped_to_guest_and_can_be_deleted() -> None:
    client = TestClient(create_app(environment="test"))
    first_device = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP"
    second_device = "dev_01JZQ83S2R83NNQ2E3FEV1V61B"
    first_headers = create_guest(client, first_device)
    second_headers = create_guest(client, second_device)
    request = {
        "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
        "device_id": first_device,
        "application": "wechat",
        "locale": "zh-CN",
        "turns": [{"role": "other", "text": "今晚有空一起吃饭吗？"}],
        "save_history": True,
    }

    stream = client.post("/v1/ai/suggestions:stream", json=request, headers=first_headers)

    assert stream.status_code == 200
    first_history = client.get("/v1/history", headers=first_headers)
    assert first_history.status_code == 200
    assert first_history.json()["items"][0]["application"] == "wechat"
    assert first_history.json()["items"][0]["turns"][0]["text"] == "今晚有空一起吃饭吗？"
    assert len(first_history.json()["items"][0]["suggestions"]) == 3
    assert client.get("/v1/history", headers=second_headers).json()["items"] == []

    deleted = client.delete("/v1/history", headers=first_headers)

    assert deleted.status_code == 204
    assert client.get("/v1/history", headers=first_headers).json()["items"] == []


def test_single_history_delete_is_scoped_to_authenticated_subject() -> None:
    client = TestClient(create_app(environment="test"))
    first_device = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP"
    second_device = "dev_01JZQ83S2R83NNQ2E3FEV1V61B"
    first_headers = create_guest(client, first_device)
    second_headers = create_guest(client, second_device)

    for index, text in enumerate(("第一条", "第二条")):
        client.post(
            "/v1/ai/suggestions:stream",
            headers=first_headers,
            json={
                "request_id": f"req_01JZQ6K3EP7EZAW4ZK2B7J1X{index}M",
                "device_id": first_device,
                "application": "wechat",
                "locale": "zh-CN",
                "turns": [{"role": "other", "text": text}],
                "save_history": True,
            },
        )
    history = client.get("/v1/history", headers=first_headers).json()["items"]

    other_delete = client.delete(
        f"/v1/history?id={history[0]['id']}",
        headers=second_headers,
    )
    own_delete = client.delete(
        f"/v1/history?id={history[0]['id']}",
        headers=first_headers,
    )

    assert other_delete.status_code == 404
    assert own_delete.status_code == 204
    remaining = client.get("/v1/history", headers=first_headers).json()["items"]
    assert [item["id"] for item in remaining] == [history[1]["id"]]
