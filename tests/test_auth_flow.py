from fastapi.testclient import TestClient

from langou_backend.main import create_app
from langou_backend.sms import SmsChallengeService


class CapturingSmsSender:
    def __init__(self) -> None:
        self.codes: list[str] = []

    async def send(self, phone: str, code: str) -> None:
        del phone
        self.codes.append(code)


def test_sms_verification_creates_user_session_without_echoing_the_code() -> None:
    sender = CapturingSmsSender()
    sms = SmsChallengeService(sender=sender, pepper=b"s" * 32)
    client = TestClient(create_app(environment="test", sms_service=sms))
    phone = "+8613800138000"

    sent = client.post("/v1/auth/sms/send", json={"phone": phone})
    invalid_code = "000000" if sender.codes[-1] != "000000" else "000001"
    invalid = client.post(
        "/v1/auth/sms/verify",
        json={
            "phone": phone,
            "code": invalid_code,
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
        },
    )
    verified = client.post(
        "/v1/auth/sms/verify",
        json={
            "phone": phone,
            "code": sender.codes[-1],
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
        },
    )

    assert sent.status_code == 202
    assert invalid.status_code == 401
    assert invalid.json()["detail"] == "invalid_sms_code"
    assert verified.status_code == 200
    assert verified.json()["subject_type"] == "user"
    assert sender.codes[-1] not in verified.text


def test_refresh_tokens_rotate_and_cannot_be_reused() -> None:
    client = TestClient(create_app(environment="test"))
    created = client.post(
        "/v1/devices/guest-session",
        json={
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
            "platform": "android",
            "app_version": "1.0.0",
        },
    ).json()

    rotated = client.post(
        "/v1/auth/token/refresh",
        json={"refresh_token": created["refresh_token"]},
    )
    replayed = client.post(
        "/v1/auth/token/refresh",
        json={"refresh_token": created["refresh_token"]},
    )

    assert rotated.status_code == 200
    assert rotated.json()["refresh_token"] != created["refresh_token"]
    assert replayed.status_code == 401
    assert replayed.json()["detail"] == "invalid_refresh_token"


def test_authenticated_user_can_merge_guest_history_and_settings_once() -> None:
    sender = CapturingSmsSender()
    sms = SmsChallengeService(sender=sender, pepper=b"s" * 32)
    client = TestClient(create_app(environment="test", sms_service=sms))
    device_id = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP"
    guest = client.post(
        "/v1/devices/guest-session",
        json={
            "device_id": device_id,
            "platform": "android",
            "app_version": "1.0.0",
        },
    ).json()
    guest_headers = {"Authorization": f"Bearer {guest['access_token']}"}
    client.put(
        "/v1/settings",
        headers=guest_headers,
        json={
            "theme": "moon",
            "auto_suggest": True,
            "save_history": True,
            "diagnostics": False,
        },
    )
    client.post(
        "/v1/ai/suggestions:stream",
        headers=guest_headers,
        json={
            "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
            "device_id": device_id,
            "application": "wechat",
            "locale": "zh-CN",
            "turns": [{"role": "other", "text": "周末去看电影吗？"}],
            "save_history": True,
        },
    )
    phone = "+8613800138000"
    client.post("/v1/auth/sms/send", json={"phone": phone})
    user = client.post(
        "/v1/auth/sms/verify",
        json={"phone": phone, "code": sender.codes[-1], "device_id": device_id},
    ).json()
    user_headers = {"Authorization": f"Bearer {user['access_token']}"}

    merged = client.post(
        "/v1/auth/sms/merge",
        headers=user_headers,
        json={"guest_refresh_token": guest["refresh_token"]},
    )
    replayed = client.post(
        "/v1/auth/sms/merge",
        headers=user_headers,
        json={"guest_refresh_token": guest["refresh_token"]},
    )

    assert merged.status_code == 200
    assert merged.json() == {"status": "merged"}
    assert client.get("/v1/settings", headers=user_headers).json()["theme"] == "moon"
    history = client.get("/v1/history", headers=user_headers).json()["items"]
    assert history[0]["turns"][0]["text"] == "周末去看电影吗？"
    assert replayed.status_code == 401
