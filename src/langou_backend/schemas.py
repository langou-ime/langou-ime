from datetime import datetime
from typing import Annotated, Literal

from pydantic import AnyUrl, BaseModel, ConfigDict, Field, field_validator

Identifier = Annotated[str, Field(min_length=8, max_length=64, pattern=r"^[A-Za-z0-9_-]+$")]
SemVer = Annotated[
    str,
    Field(pattern=r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$"),
]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ConversationTurn(StrictModel):
    role: Literal["self", "other"]
    text: Annotated[str, Field(min_length=1, max_length=2000)]


class SuggestionRequest(StrictModel):
    request_id: Identifier
    device_id: Identifier
    application: Literal[
        "wechat",
        "qq",
        "wecom",
        "dingtalk",
        "feishu",
        "whatsapp",
        "telegram",
        "discord",
        "generic",
    ]
    locale: Annotated[str, Field(min_length=2, max_length=16)]
    turns: Annotated[list[ConversationTurn], Field(min_length=1, max_length=12)]
    draft: Annotated[str | None, Field(max_length=1000)] = None
    save_history: bool = True


class GuestSessionRequest(StrictModel):
    device_id: Identifier
    platform: Literal["android", "windows"]
    app_version: SemVer


class TokenPair(StrictModel):
    access_token: str
    refresh_token: str
    token_type: Literal["bearer"] = "bearer"  # noqa: S105 - OAuth scheme name
    expires_in: int
    subject_type: Literal["guest", "user"]


class SmsSendRequest(StrictModel):
    phone: Annotated[str, Field(pattern=r"^\+[1-9]\d{7,14}$")]


class SmsSendResponse(StrictModel):
    status: Literal["sent"] = "sent"
    retry_after: int = 60


class SmsVerifyRequest(StrictModel):
    phone: Annotated[str, Field(pattern=r"^\+[1-9]\d{7,14}$")]
    code: Annotated[str, Field(pattern=r"^\d{6}$")]
    device_id: Identifier


class RefreshTokenRequest(StrictModel):
    refresh_token: Annotated[str, Field(min_length=40, max_length=4096)]


class GuestMergeRequest(StrictModel):
    guest_refresh_token: Annotated[str, Field(min_length=40, max_length=4096)]


class MergeResponse(StrictModel):
    status: Literal["merged"] = "merged"


class ClientSettings(StrictModel):
    theme: Literal["cream", "soda", "moon"] = "cream"
    auto_suggest: bool = True
    save_history: bool = True
    diagnostics: bool = False


class ReleaseManifest(StrictModel):
    platform: Literal["android", "windows"]
    version: SemVer
    minimum_supported_version: SemVer
    mandatory: bool
    url: AnyUrl
    size: Annotated[int, Field(gt=0)]
    sha256: Annotated[str, Field(pattern=r"^[a-f0-9]{64}$")]
    signature: Annotated[str, Field(min_length=8, max_length=4096)]
    published_at: datetime

    @field_validator("url")
    @classmethod
    def require_https(cls, value: AnyUrl) -> AnyUrl:
        if value.scheme != "https":
            raise ValueError("release URL must use HTTPS")
        return value
