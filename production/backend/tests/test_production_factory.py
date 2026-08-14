import base64
from pathlib import Path

import sqlalchemy as sa
from fastapi.testclient import TestClient
from sqlalchemy.ext.asyncio import create_async_engine

from langou_backend.config import Settings
from langou_backend.database import Base
from langou_backend.production import create_production_app
from langou_backend.sms import NullSmsSender
from langou_backend.suggestions import DevelopmentSuggestionProvider


class UnusedRedis:
    async def ping(self) -> bool:
        return True


def test_production_factory_persists_settings_in_sql(tmp_path: Path) -> None:
    database_path = tmp_path / "production.db"
    sync_url = f"sqlite:///{database_path}"
    async_url = f"sqlite+aiosqlite:///{database_path}"
    schema_engine = sa.create_engine(sync_url)
    Base.metadata.create_all(schema_engine)
    schema_engine.dispose()
    engine = create_async_engine(async_url)
    settings = Settings(
        environment="production",
        database_url="postgresql+asyncpg://unused",
        redis_url="redis://unused",
        jwt_secret="j" * 48,
        sms_pepper="s" * 48,
        history_key=base64.urlsafe_b64encode(b"h" * 32).decode(),
        phone_key=base64.urlsafe_b64encode(b"p" * 32).decode(),
        mimo_api_base="https://token-plan-cn.xiaomimimo.com/v1",
        mimo_api_key="runtime-secret",
        aliyun_access_key_id="runtime-key-id",
        aliyun_access_key_secret="runtime-key-secret",  # noqa: S106 - test fixture
        aliyun_sms_sign_name="懒狗输入法",
        aliyun_sms_template_code="SMS_123456789",
        release_manifest_dir=str(tmp_path / "releases"),
    )
    app = create_production_app(
        settings,
        engine=engine,
        redis_client=UnusedRedis(),
        suggestion_provider=DevelopmentSuggestionProvider(),
        sms_sender=NullSmsSender(),
    )
    client = TestClient(app)
    readiness = client.get("/ready")
    assert readiness.status_code == 200
    assert readiness.json() == {"status": "ready", "database": "ok", "redis": "ok"}
    assert len(readiness.headers["x-request-id"]) >= 16
    device_id = "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP"
    guest = client.post(
        "/v1/devices/guest-session",
        json={
            "device_id": device_id,
            "platform": "android",
            "app_version": "1.0.0",
        },
    ).json()

    response = client.put(
        "/v1/settings",
        headers={"Authorization": f"Bearer {guest['access_token']}"},
        json={
            "theme": "soda",
            "auto_suggest": True,
            "save_history": False,
            "diagnostics": False,
        },
    )

    assert response.status_code == 200
    with sa.create_engine(sync_url).connect() as connection:
        stored_theme = connection.scalar(
            sa.select(Base.metadata.tables["client_settings"].c.theme)
        )
    assert stored_theme == "soda"
