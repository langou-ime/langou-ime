import asyncio
from typing import Literal, Protocol


class DeviceRepository(Protocol):
    async def register(
        self,
        device_id: str,
        *,
        platform: Literal["android", "windows"],
        app_version: str,
    ) -> None: ...

    async def attach(self, device_id: str, user_id: str) -> None: ...


class InMemoryDeviceRepository:
    def __init__(self) -> None:
        self._devices: dict[str, dict[str, str | None]] = {}
        self._lock = asyncio.Lock()

    async def register(
        self,
        device_id: str,
        *,
        platform: Literal["android", "windows"],
        app_version: str,
    ) -> None:
        async with self._lock:
            existing_user = self._devices.get(device_id, {}).get("user_id")
            self._devices[device_id] = {
                "platform": platform,
                "app_version": app_version,
                "user_id": existing_user,
            }

    async def attach(self, device_id: str, user_id: str) -> None:
        async with self._lock:
            device = self._devices.setdefault(
                device_id,
                {"platform": None, "app_version": None, "user_id": None},
            )
            device["user_id"] = user_id
