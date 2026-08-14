import pytest
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from langou_backend import database
from langou_backend.schemas import ClientSettings


@pytest.mark.asyncio
async def test_sql_settings_repository_persists_defaults_and_updates() -> None:
    assert hasattr(database, "SqlSettingsRepository"), "SQL settings repository is missing"
    engine = create_async_engine("sqlite+aiosqlite://")
    sessions = async_sessionmaker(engine, expire_on_commit=False)
    async with engine.begin() as connection:
        await connection.run_sync(database.Base.metadata.create_all)
    repository = database.SqlSettingsRepository(sessions)

    defaults = await repository.get("dev_one")
    updated = await repository.put(
        "dev_one",
        ClientSettings(theme="moon", auto_suggest=True, save_history=False),
    )
    reloaded = await repository.get("dev_one")

    assert defaults == ClientSettings()
    assert updated.theme == "moon"
    assert reloaded == updated
    await engine.dispose()
