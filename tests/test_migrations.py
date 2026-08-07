from pathlib import Path

import sqlalchemy as sa
from alembic.config import Config

from alembic import command
from langou_backend.database import Base


def test_alembic_upgrade_builds_current_schema(tmp_path: Path, monkeypatch) -> None:
    database_path = tmp_path / "langou.db"
    monkeypatch.setenv("LANGOU_DATABASE_URL", f"sqlite+aiosqlite:///{database_path}")
    config = Config("alembic.ini")

    command.upgrade(config, "head")

    engine = sa.create_engine(f"sqlite:///{database_path}")
    inspector = sa.inspect(engine)
    assert set(inspector.get_table_names()) == {
        "alembic_version",
        *Base.metadata.tables,
    }
    engine.dispose()
