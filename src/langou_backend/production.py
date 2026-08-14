import base64
from pathlib import Path
from typing import Any

import httpx
import redis.asyncio as redis
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.ext.asyncio import AsyncEngine, async_sessionmaker, create_async_engine

from langou_backend.auth import AuthService, TokenService
from langou_backend.config import Settings
from langou_backend.crypto import HistoryCipher
from langou_backend.database import (
    SqlAccountRepository,
    SqlDeviceRepository,
    SqlHistoryRepository,
    SqlRefreshSessionRepository,
    SqlSettingsRepository,
)
from langou_backend.guards import RedisSuggestionGate
from langou_backend.history import HistoryService
from langou_backend.main import create_app
from langou_backend.observability import (
    DatabaseRedisReadiness,
    install_request_observability,
)
from langou_backend.releases import FileReleaseRepository
from langou_backend.sms import AliyunSmsSender, RedisSmsChallengeService, SmsSender
from langou_backend.suggestions import MimoSuggestionProvider, SuggestionProvider


def create_production_app(
    settings: Settings,
    *,
    engine: AsyncEngine | None = None,
    redis_client: Any | None = None,
    suggestion_provider: SuggestionProvider | None = None,
    sms_sender: SmsSender | None = None,
):
    if settings.environment != "production":
        raise ValueError("production settings are required")
    if settings.database_url is None or settings.redis_url is None:
        raise ValueError("database and Redis URLs are required")
    database_engine = engine or create_async_engine(
        settings.database_url,
        pool_pre_ping=True,
        pool_recycle=1800,
    )
    sessions = async_sessionmaker(database_engine, expire_on_commit=False)
    shared_redis = redis_client or redis.from_url(
        settings.redis_url,
        encoding="utf-8",
        decode_responses=True,
    )
    history_cipher = HistoryCipher(_decode_key(settings.history_key, "history_key"))
    phone_cipher = HistoryCipher(_decode_key(settings.phone_key, "phone_key"))
    token_service = TokenService(_required(settings.jwt_secret, "jwt_secret"))
    if sms_sender is None:
        sms_sender = AliyunSmsSender.from_credentials(
            access_key_id=_required(settings.aliyun_access_key_id, "aliyun_access_key_id"),
            access_key_secret=_required(
                settings.aliyun_access_key_secret,
                "aliyun_access_key_secret",
            ),
            sign_name=_required(settings.aliyun_sms_sign_name, "aliyun_sms_sign_name"),
            template_code=_required(
                settings.aliyun_sms_template_code,
                "aliyun_sms_template_code",
            ),
        )
    sms_service = RedisSmsChallengeService(
        redis=shared_redis,
        sender=sms_sender,
        pepper=_required(settings.sms_pepper, "sms_pepper").encode(),
    )
    device_repository = SqlDeviceRepository(sessions)
    auth_service = AuthService(
        token_service=token_service,
        sms_service=sms_service,
        refresh_sessions=SqlRefreshSessionRepository(sessions),
        accounts=SqlAccountRepository(sessions, phone_cipher=phone_cipher),
        phone_pepper=_required(settings.sms_pepper, "sms_pepper").encode(),
        devices=device_repository,
    )
    if suggestion_provider is None:
        mimo_client = httpx.AsyncClient(
            base_url=f"{str(settings.mimo_api_base).rstrip('/')}/",
            headers={
                "Authorization": f"Bearer {_required(settings.mimo_api_key, 'mimo_api_key')}"
            },
            timeout=httpx.Timeout(12.0, connect=3.0),
        )
        suggestion_provider = MimoSuggestionProvider(
            client=mimo_client,
            primary_model=settings.mimo_primary_model,
            fallback_model=settings.mimo_fallback_model,
            first_suggestion_timeout_seconds=settings.mimo_first_suggestion_timeout_seconds,
        )
    app = create_app(
        environment="production",
        token_service=token_service,
        sms_service=sms_service,
        auth_service=auth_service,
        suggestion_provider=suggestion_provider,
        suggestion_gate=RedisSuggestionGate(
            redis=shared_redis,
            per_minute_limit=settings.ai_per_minute_limit,
            daily_budget=settings.ai_daily_budget,
        ),
        history_service=HistoryService(
            repository=SqlHistoryRepository(sessions),
            cipher=history_cipher,
        ),
        settings_repository=SqlSettingsRepository(sessions),
        release_repository=FileReleaseRepository(
            Path(settings.release_manifest_dir) if settings.release_manifest_dir else None
        ),
        readiness_checker=DatabaseRedisReadiness(
            engine=database_engine,
            redis=shared_redis,
        ),
        device_repository=device_repository,
    )
    if settings.cors_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origins,
            allow_credentials=False,
            allow_methods=["GET", "POST", "PUT", "DELETE"],
            allow_headers=["Authorization", "Content-Type"],
        )
    app.state.database_engine = database_engine
    app.state.redis = shared_redis
    app.state.sessions = sessions
    install_request_observability(app)
    return app


def _decode_key(encoded: str | None, name: str) -> bytes:
    return base64.urlsafe_b64decode(_required(encoded, name))


def _required(value: str | None, name: str) -> str:
    if not value:
        raise ValueError(f"{name} is required")
    return value
