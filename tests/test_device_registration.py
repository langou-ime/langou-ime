from fastapi.testclient import TestClient

from langou_backend.main import create_app


class RecordingDeviceRepository:
    def __init__(self) -> None:
        self.registered: list[tuple[str, str, str]] = []

    async def register(
        self,
        device_id: str,
        *,
        platform: str,
        app_version: str,
    ) -> None:
        self.registered.append((device_id, platform, app_version))

    async def attach(self, device_id: str, user_id: str) -> None:
        del device_id, user_id


def test_guest_session_registers_platform_and_version() -> None:
    devices = RecordingDeviceRepository()
    client = TestClient(create_app(environment="test", device_repository=devices))
    device_id = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP"

    response = client.post(
        "/v1/devices/guest-session",
        json={
            "device_id": device_id,
            "platform": "windows",
            "app_version": "1.0.0",
        },
    )

    assert response.status_code == 201
    assert devices.registered == [(device_id, "windows", "1.0.0")]
