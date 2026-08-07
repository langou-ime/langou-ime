import base64
from typing import Literal, Self

from pydantic import AnyHttpUrl, Field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="LANGOU_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    environment: Literal["development", "test", "production"] = "development"
    database_url: str | None = None
    redis_url: str | None = None
    jwt_secret: str | None = Field(default=None, min_length=32)
    sms_pepper: str | None = Field(default=None, min_length=32)
    history_key: str | None = None
    phone_key: str | None = None
    mimo_api_base: AnyHttpUrl | None = None
    mimo_api_key: str | None = None
    mimo_primary_model: str = "mimo-v2.5-pro"
    mimo_fallback_model: str = "mimo-v2.5"
    ai_per_minute_limit: int = Field(default=20, ge=1, le=300)
    ai_daily_budget: int = Field(default=100000, ge=1)
    cors_origins: list[str] = []
    release_manifest_dir: str | None = None
    aliyun_access_key_id: str | None = None
    aliyun_access_key_secret: str | None = None
    aliyun_sms_sign_name: str | None = None
    aliyun_sms_template_code: str | None = None

    @field_validator("mimo_api_base")
    @classmethod
    def require_https_mimo(cls, value: AnyHttpUrl | None) -> AnyHttpUrl | None:
        if value is not None and value.scheme != "https":
            raise ValueError("mimo_api_base must use HTTPS")
        return value

    @field_validator("cors_origins")
    @classmethod
    def reject_wildcard_cors(cls, value: list[str]) -> list[str]:
        if "*" in value:
            raise ValueError("cors_origins must not contain a wildcard")
        return value

    @field_validator("history_key", "phone_key")
    @classmethod
    def require_aes256_key(cls, value: str | None) -> str | None:
        if value is None:
            return None
        try:
            decoded = base64.urlsafe_b64decode(value)
        except ValueError as exc:
            raise ValueError("encryption key must be URL-safe base64") from exc
        if len(decoded) != 32:
            raise ValueError("encryption key must decode to 32 bytes")
        return value

    @model_validator(mode="after")
    def require_production_values(self) -> Self:
        if self.environment != "production":
            return self
        required = (
            "database_url",
            "redis_url",
            "jwt_secret",
            "sms_pepper",
            "history_key",
            "phone_key",
            "mimo_api_base",
            "mimo_api_key",
            "aliyun_access_key_id",
            "aliyun_access_key_secret",
            "aliyun_sms_sign_name",
            "aliyun_sms_template_code",
        )
        missing = [name for name in required if not getattr(self, name)]
        if missing:
            raise ValueError(f"missing production settings: {', '.join(missing)}")
        return self
