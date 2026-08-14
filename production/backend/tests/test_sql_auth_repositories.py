from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from langou_backend.auth import RefreshPrincipal
from langou_backend.crypto import HistoryCipher
from langou_backend.database import (
    Base,
    SqlAccountRepository,
    SqlDeviceRepository,
    SqlRefreshSessionRepository,
    UserRow,
)


@pytest.mark.asyncio
async def test_refresh_session_consumption_is_atomic_and_persistent() -> None:
    engine = create_async_engine("sqlite+aiosqlite://")
    sessions = async_sessionmaker(engine, expire_on_commit=False)
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    repository = SqlRefreshSessionRepository(sessions)
    principal = RefreshPrincipal(
        subject="dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
        subject_type="guest",
        jti="refresh_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
        expires_at=datetime.now(UTC) + timedelta(days=30),
    )

    assert await repository.consume(principal) is True
    assert await repository.consume(principal) is False
    await engine.dispose()


@pytest.mark.asyncio
async def test_accounts_encrypt_phone_and_devices_attach_to_user() -> None:
    engine = create_async_engine("sqlite+aiosqlite://")
    sessions = async_sessionmaker(engine, expire_on_commit=False)
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    accounts = SqlAccountRepository(
        sessions,
        phone_cipher=HistoryCipher(b"a" * 32),
    )
    devices = SqlDeviceRepository(sessions)
    phone = "+8613800138000"

    user_id = await accounts.get_or_create("phone_hash", phone)
    same_user_id = await accounts.get_or_create("phone_hash", phone)
    await devices.register(
        "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
        platform="android",
        app_version="1.0.0",
    )
    await devices.attach("dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP", user_id)

    async with sessions() as session:
        user = await session.get(UserRow, user_id)
    assert same_user_id == user_id
    assert user is not None
    assert phone not in user.phone_encrypted
    assert phone not in user.phone_hash
    assert await devices.owner("dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP") == user_id
    await engine.dispose()
