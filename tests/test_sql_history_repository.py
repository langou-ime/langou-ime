from datetime import UTC, datetime, timedelta
from importlib import import_module

import pytest
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from langou_backend.history import EncryptedHistoryRecord


def test_metadata_contains_production_tables() -> None:
    database = import_module("langou_backend.database")

    assert {
        "users",
        "devices",
        "client_settings",
        "history_records",
        "refresh_sessions",
        "sms_challenges",
        "usage_events",
    }.issubset(database.Base.metadata.tables)


@pytest.mark.asyncio
async def test_sql_history_repository_prunes_expired_records_and_scopes_subjects() -> None:
    try:
        database = import_module("langou_backend.database")
    except ModuleNotFoundError:
        pytest.fail("langou_backend.database has not been implemented")

    engine = create_async_engine("sqlite+aiosqlite://")
    async with engine.begin() as connection:
        await connection.run_sync(database.Base.metadata.create_all)
    repository = database.SqlHistoryRepository(async_sessionmaker(engine, expire_on_commit=False))
    now = datetime.now(UTC)
    await repository.add(
        EncryptedHistoryRecord(
            id="hist_active",
            subject="dev_one",
            encrypted_payload="v1.active",
            created_at=now,
            expires_at=now + timedelta(days=1),
        )
    )
    await repository.add(
        EncryptedHistoryRecord(
            id="hist_expired",
            subject="dev_one",
            encrypted_payload="v1.expired",
            created_at=now - timedelta(days=31),
            expires_at=now - timedelta(seconds=1),
        )
    )
    await repository.add(
        EncryptedHistoryRecord(
            id="hist_other",
            subject="dev_two",
            encrypted_payload="v1.other",
            created_at=now,
            expires_at=now + timedelta(days=1),
        )
    )

    records = await repository.list_for_subject("dev_one", now)

    assert [record.id for record in records] == ["hist_active"]
    await engine.dispose()


@pytest.mark.asyncio
async def test_sql_history_repository_daily_purge_hard_deletes_all_expired_rows() -> None:
    database = import_module("langou_backend.database")
    engine = create_async_engine("sqlite+aiosqlite://")
    sessions = async_sessionmaker(engine, expire_on_commit=False)
    async with engine.begin() as connection:
        await connection.run_sync(database.Base.metadata.create_all)
    repository = database.SqlHistoryRepository(sessions)
    now = datetime.now(UTC)
    for subject in ("dev_one", "dev_two"):
        await repository.add(
            EncryptedHistoryRecord(
                id=f"hist_{subject}",
                subject=subject,
                encrypted_payload="encrypted",
                created_at=now - timedelta(days=31),
                expires_at=now - timedelta(seconds=1),
            )
        )

    assert await repository.purge_expired(now) == 2
    assert await repository.purge_expired(now) == 0
    await engine.dispose()
