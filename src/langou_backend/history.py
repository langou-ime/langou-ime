import asyncio
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any, Protocol
from uuid import uuid4

from langou_backend.crypto import HistoryCipher


@dataclass(frozen=True)
class EncryptedHistoryRecord:
    id: str
    subject: str
    encrypted_payload: str
    created_at: datetime
    expires_at: datetime


class HistoryRepository(Protocol):
    async def add(self, record: EncryptedHistoryRecord) -> None: ...

    async def list_for_subject(
        self,
        subject: str,
        now: datetime,
    ) -> list[EncryptedHistoryRecord]: ...

    async def delete_for_subject(self, subject: str) -> int: ...

    async def delete_one(self, subject: str, record_id: str) -> int: ...

    async def merge_subject(self, source: str, target: str) -> int: ...


class InMemoryHistoryRepository:
    def __init__(self) -> None:
        self._records: dict[str, EncryptedHistoryRecord] = {}
        self._lock = asyncio.Lock()

    async def add(self, record: EncryptedHistoryRecord) -> None:
        async with self._lock:
            self._records[record.id] = record

    async def list_for_subject(
        self,
        subject: str,
        now: datetime,
    ) -> list[EncryptedHistoryRecord]:
        async with self._lock:
            self._records = {
                key: value for key, value in self._records.items() if value.expires_at > now
            }
            return sorted(
                (record for record in self._records.values() if record.subject == subject),
                key=lambda record: record.created_at,
                reverse=True,
            )

    async def delete_for_subject(self, subject: str) -> int:
        async with self._lock:
            matching = [key for key, value in self._records.items() if value.subject == subject]
            for key in matching:
                del self._records[key]
            return len(matching)

    async def delete_one(self, subject: str, record_id: str) -> int:
        async with self._lock:
            record = self._records.get(record_id)
            if record is None or record.subject != subject:
                return 0
            del self._records[record_id]
            return 1

    async def merge_subject(self, source: str, target: str) -> int:
        async with self._lock:
            matching = [
                (key, value)
                for key, value in self._records.items()
                if value.subject == source
            ]
            for key, record in matching:
                self._records[key] = EncryptedHistoryRecord(
                    id=record.id,
                    subject=target,
                    encrypted_payload=record.encrypted_payload,
                    created_at=record.created_at,
                    expires_at=record.expires_at,
                )
            return len(matching)


class HistoryService:
    RETENTION = timedelta(days=30)

    def __init__(
        self,
        *,
        repository: HistoryRepository,
        cipher: HistoryCipher,
    ) -> None:
        self._repository = repository
        self._cipher = cipher

    async def save(self, subject: str, payload: dict[str, Any]) -> str:
        record_id = f"hist_{uuid4().hex}"
        now = datetime.now(UTC)
        await self._repository.add(
            EncryptedHistoryRecord(
                id=record_id,
                subject=subject,
                encrypted_payload=self._cipher.encrypt(record_id, payload),
                created_at=now,
                expires_at=now + self.RETENTION,
            )
        )
        return record_id

    async def list(self, subject: str) -> list[dict[str, Any]]:
        records = await self._repository.list_for_subject(subject, datetime.now(UTC))
        return [
            {
                "id": record.id,
                "created_at": record.created_at,
                "expires_at": record.expires_at,
                **self._cipher.decrypt(record.id, record.encrypted_payload),
            }
            for record in records
        ]

    async def delete_all(self, subject: str) -> int:
        return await self._repository.delete_for_subject(subject)

    async def delete_one(self, subject: str, record_id: str) -> bool:
        return await self._repository.delete_one(subject, record_id) == 1

    async def merge(self, source: str, target: str) -> int:
        return await self._repository.merge_subject(source, target)
