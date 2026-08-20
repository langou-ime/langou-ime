import base64
from importlib import import_module

import pytest
from pydantic import ValidationError


def load_config():
    try:
        return import_module("langou_backend.config")
    except ModuleNotFoundError:
        pytest.fail("langou_backend.config has not been implemented")


def production_values() -> dict[str, str | list[str]]:
    return {
        "environment": "production",
        "database_url": "postgresql+asyncpg://langou:secret@127.0.0.1/langou",
        "redis_url": "redis://127.0.0.1:6379/0",
        "jwt_secret": "j" * 48,
        "sms_pepper": "s" * 48,
        "history_key": base64.urlsafe_b64encode(b"h" * 32).decode(),
        "phone_key": base64.urlsafe_b64encode(b"p" * 32).decode(),
        "mimo_api_base": "https://token-plan-cn.xiaomimimo.com/v1",
        "mimo_api_key": "configured-at-runtime",
        "aliyun_access_key_id": "configured-at-runtime",
        "aliyun_access_key_secret": "configured-at-runtime",
        "aliyun_sms_sign_name": "懒狗输入法",
        "aliyun_sms_template_code": "SMS_123456789",
        "cors_origins": ["https://api.langou.tech"],
    }


def test_production_config_requires_every_secret() -> None:
    config = load_config()

    with pytest.raises(ValidationError):
        config.Settings(environment="production")


def test_production_config_rejects_wildcard_cors() -> None:
    config = load_config()
    values = production_values() | {"cors_origins": ["*"]}

    with pytest.raises(ValidationError, match="cors_origins"):
        config.Settings(**values)


def test_production_config_accepts_secret_only_runtime_values() -> None:
    config = load_config()

    settings = config.Settings(**production_values())

    assert settings.mimo_primary_model == "mimo-v2.5"
    assert settings.mimo_fallback_model == "mimo-v2.5-pro"
