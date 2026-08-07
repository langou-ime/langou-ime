import asyncio
import os
from datetime import UTC, datetime

from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from langou_backend.database import SqlHistoryRepository


async def purge_expired_history(database_url: str) -> int:
    engine = create_async_engine(database_url, pool_pre_ping=True)
    try:
        repository = SqlHistoryRepository(
            async_sessionmaker(engine, expire_on_commit=False)
        )
        return await repository.purge_expired(datetime.now(UTC))
    finally:
        await engine.dispose()


def main() -> None:
    database_url = os.environ.get("LANGOU_DATABASE_URL")
    if not database_url:
        raise RuntimeError("LANGOU_DATABASE_URL is required")
    deleted = asyncio.run(purge_expired_history(database_url))
    print(f"expired_history_deleted={deleted}")
