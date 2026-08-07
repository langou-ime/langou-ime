from datetime import UTC, datetime
from typing import Literal
from uuid import uuid4

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text, delete, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from langou_backend.auth import RefreshPrincipal
from langou_backend.crypto import HistoryCipher
from langou_backend.history import EncryptedHistoryRecord
from langou_backend.schemas import ClientSettings


class Base(DeclarativeBase):
    pass


class HistoryRow(Base):
    __tablename__ = "history_records"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    subject: Mapped[str] = mapped_column(String(64), index=True)
    encrypted_payload: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)


class UserRow(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    phone_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    phone_encrypted: Mapped[str] = mapped_column(Text)
    plan: Mapped[str] = mapped_column(String(32), default="free")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class DeviceRow(Base):
    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[str | None] = mapped_column(
        String(64),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    platform: Mapped[str] = mapped_column(String(16))
    app_version: Mapped[str] = mapped_column(String(32))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class ClientSettingsRow(Base):
    __tablename__ = "client_settings"

    subject: Mapped[str] = mapped_column(String(64), primary_key=True)
    theme: Mapped[str] = mapped_column(String(16), default="cream")
    auto_suggest: Mapped[bool] = mapped_column(Boolean, default=True)
    save_history: Mapped[bool] = mapped_column(Boolean, default=True)
    diagnostics: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class RefreshSessionRow(Base):
    __tablename__ = "refresh_sessions"

    jti: Mapped[str] = mapped_column(String(64), primary_key=True)
    subject: Mapped[str] = mapped_column(String(64), index=True)
    subject_type: Mapped[str] = mapped_column(String(16))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    consumed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class SmsChallengeRow(Base):
    __tablename__ = "sms_challenges"

    phone_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    code_digest: Mapped[str] = mapped_column(String(64))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    next_send_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    failed_attempts: Mapped[int] = mapped_column(Integer, default=0)
    daily_sent_count: Mapped[int] = mapped_column(Integer, default=0)
    day_bucket: Mapped[str] = mapped_column(String(10))


class UsageEventRow(Base):
    __tablename__ = "usage_events"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    subject: Mapped[str] = mapped_column(String(64), index=True)
    request_id: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    model: Mapped[str | None] = mapped_column(String(64), nullable=True)
    status: Mapped[str] = mapped_column(String(32))
    suggestion_count: Mapped[int] = mapped_column(Integer, default=0)
    latency_ms: Mapped[int] = mapped_column(Integer)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)


class SqlHistoryRepository:
    def __init__(self, sessions: async_sessionmaker[AsyncSession]) -> None:
        self._sessions = sessions

    async def add(self, record: EncryptedHistoryRecord) -> None:
        async with self._sessions() as session:
            session.add(
                HistoryRow(
                    id=record.id,
                    subject=record.subject,
                    encrypted_payload=record.encrypted_payload,
                    created_at=record.created_at,
                    expires_at=record.expires_at,
                )
            )
            await session.commit()

    async def list_for_subject(
        self,
        subject: str,
        now: datetime,
    ) -> list[EncryptedHistoryRecord]:
        async with self._sessions() as session:
            await session.execute(delete(HistoryRow).where(HistoryRow.expires_at <= now))
            rows = (
                await session.scalars(
                    select(HistoryRow)
                    .where(HistoryRow.subject == subject)
                    .order_by(HistoryRow.created_at.desc())
                )
            ).all()
            await session.commit()
        return [
            EncryptedHistoryRecord(
                id=row.id,
                subject=row.subject,
                encrypted_payload=row.encrypted_payload,
                created_at=row.created_at,
                expires_at=row.expires_at,
            )
            for row in rows
        ]

    async def delete_for_subject(self, subject: str) -> int:
        async with self._sessions() as session:
            result = await session.execute(delete(HistoryRow).where(HistoryRow.subject == subject))
            await session.commit()
            return result.rowcount or 0

    async def delete_one(self, subject: str, record_id: str) -> int:
        async with self._sessions() as session:
            result = await session.execute(
                delete(HistoryRow).where(
                    HistoryRow.subject == subject,
                    HistoryRow.id == record_id,
                )
            )
            await session.commit()
            return result.rowcount or 0

    async def merge_subject(self, source: str, target: str) -> int:
        async with self._sessions() as session:
            result = await session.execute(
                update(HistoryRow).where(HistoryRow.subject == source).values(subject=target)
            )
            await session.commit()
            return result.rowcount or 0

    async def purge_expired(self, now: datetime) -> int:
        async with self._sessions() as session:
            result = await session.execute(
                delete(HistoryRow).where(HistoryRow.expires_at <= now)
            )
            await session.commit()
            return result.rowcount or 0


class SqlSettingsRepository:
    def __init__(self, sessions: async_sessionmaker[AsyncSession]) -> None:
        self._sessions = sessions

    async def get(self, subject: str) -> ClientSettings:
        async with self._sessions() as session:
            row = await session.get(ClientSettingsRow, subject)
        if row is None:
            return ClientSettings()
        return ClientSettings(
            theme=row.theme,
            auto_suggest=row.auto_suggest,
            save_history=row.save_history,
            diagnostics=row.diagnostics,
        )

    async def put(self, subject: str, settings: ClientSettings) -> ClientSettings:
        async with self._sessions() as session:
            row = await session.get(ClientSettingsRow, subject)
            if row is None:
                row = ClientSettingsRow(
                    subject=subject,
                    updated_at=datetime.now(UTC),
                )
                session.add(row)
            row.theme = settings.theme
            row.auto_suggest = settings.auto_suggest
            row.save_history = settings.save_history
            row.diagnostics = settings.diagnostics
            row.updated_at = datetime.now(UTC)
            await session.commit()
        return settings.model_copy()

    async def merge_subject(self, source: str, target: str) -> None:
        async with self._sessions() as session:
            source_row = await session.get(ClientSettingsRow, source)
            target_row = await session.get(ClientSettingsRow, target)
            if source_row is None:
                return
            if target_row is None:
                target_row = ClientSettingsRow(
                    subject=target,
                    updated_at=datetime.now(UTC),
                )
                session.add(target_row)
            target_row.theme = source_row.theme
            target_row.auto_suggest = source_row.auto_suggest
            target_row.save_history = source_row.save_history
            target_row.diagnostics = source_row.diagnostics
            target_row.updated_at = datetime.now(UTC)
            await session.delete(source_row)
            await session.commit()


class SqlRefreshSessionRepository:
    def __init__(self, sessions: async_sessionmaker[AsyncSession]) -> None:
        self._sessions = sessions

    async def consume(self, principal: RefreshPrincipal) -> bool:
        now = datetime.now(UTC)
        async with self._sessions() as session:
            await session.execute(
                delete(RefreshSessionRow).where(RefreshSessionRow.expires_at <= now)
            )
            session.add(
                RefreshSessionRow(
                    jti=principal.jti,
                    subject=principal.subject,
                    subject_type=principal.subject_type,
                    expires_at=principal.expires_at,
                    consumed_at=now,
                )
            )
            try:
                await session.commit()
            except IntegrityError:
                await session.rollback()
                return False
            return True


class SqlAccountRepository:
    def __init__(
        self,
        sessions: async_sessionmaker[AsyncSession],
        *,
        phone_cipher: HistoryCipher,
    ) -> None:
        self._sessions = sessions
        self._phone_cipher = phone_cipher

    async def get_or_create(self, phone_hash: str, phone: str) -> str:
        async with self._sessions() as session:
            existing = await session.scalar(
                select(UserRow).where(UserRow.phone_hash == phone_hash)
            )
            if existing is not None:
                return existing.id
            now = datetime.now(UTC)
            user_id = f"user_{uuid4().hex}"
            session.add(
                UserRow(
                    id=user_id,
                    phone_hash=phone_hash,
                    phone_encrypted=self._phone_cipher.encrypt(user_id, {"phone": phone}),
                    plan="free",
                    created_at=now,
                    updated_at=now,
                )
            )
            try:
                await session.commit()
                return user_id
            except IntegrityError:
                await session.rollback()
                existing = await session.scalar(
                    select(UserRow).where(UserRow.phone_hash == phone_hash)
                )
                if existing is None:
                    raise
                return existing.id


class SqlDeviceRepository:
    def __init__(self, sessions: async_sessionmaker[AsyncSession]) -> None:
        self._sessions = sessions

    async def register(
        self,
        device_id: str,
        *,
        platform: Literal["android", "windows"],
        app_version: str,
    ) -> None:
        async with self._sessions() as session:
            row = await session.get(DeviceRow, device_id)
            now = datetime.now(UTC)
            if row is None:
                row = DeviceRow(
                    id=device_id,
                    platform=platform,
                    app_version=app_version,
                    created_at=now,
                    last_seen_at=now,
                )
                session.add(row)
            else:
                row.platform = platform
                row.app_version = app_version
                row.last_seen_at = now
            await session.commit()

    async def attach(self, device_id: str, user_id: str) -> None:
        async with self._sessions() as session:
            row = await session.get(DeviceRow, device_id)
            if row is None:
                return
            row.user_id = user_id
            row.last_seen_at = datetime.now(UTC)
            await session.commit()

    async def owner(self, device_id: str) -> str | None:
        async with self._sessions() as session:
            row = await session.get(DeviceRow, device_id)
            return row.user_id if row is not None else None
