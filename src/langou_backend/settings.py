import asyncio
from typing import Protocol

from langou_backend.schemas import ClientSettings


class SettingsRepository(Protocol):
    async def get(self, subject: str) -> ClientSettings: ...

    async def put(self, subject: str, settings: ClientSettings) -> ClientSettings: ...

    async def merge_subject(self, source: str, target: str) -> None: ...


class InMemorySettingsRepository:
    def __init__(self) -> None:
        self._settings: dict[str, ClientSettings] = {}
        self._lock = asyncio.Lock()

    async def get(self, subject: str) -> ClientSettings:
        async with self._lock:
            return self._settings.get(subject, ClientSettings()).model_copy()

    async def put(self, subject: str, settings: ClientSettings) -> ClientSettings:
        async with self._lock:
            self._settings[subject] = settings.model_copy()
            return settings.model_copy()

    async def merge_subject(self, source: str, target: str) -> None:
        async with self._lock:
            source_settings = self._settings.pop(source, None)
            if source_settings is not None:
                self._settings[target] = source_settings.model_copy()
