import base64
import os
import secrets
from pathlib import Path
from typing import Annotated, Any, Literal, Protocol

import orjson
from fastapi import Depends, FastAPI, HTTPException, Query, Response
from fastapi.responses import ORJSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from starlette.responses import StreamingResponse

from langou_backend import __version__
from langou_backend.auth import (
    AccessPrincipal,
    AuthService,
    InMemoryAccountRepository,
    InMemoryRefreshSessionRepository,
    InvalidAccessToken,
    InvalidRefreshToken,
    TokenService,
)
from langou_backend.crypto import HistoryCipher
from langou_backend.devices import (
    DeviceRepository,
    InMemoryDeviceRepository,
)
from langou_backend.guards import (
    BudgetCircuitOpen,
    DuplicateSuggestionRequest,
    NoopSuggestionGate,
    RateLimitExceeded,
    SuggestionGate,
)
from langou_backend.history import HistoryService, InMemoryHistoryRepository
from langou_backend.privacy import sanitize_suggestion_request
from langou_backend.releases import FileReleaseRepository, ReleaseNotFound
from langou_backend.schemas import (
    ClientSettings,
    GuestMergeRequest,
    GuestSessionRequest,
    MergeResponse,
    RefreshTokenRequest,
    ReleaseManifest,
    SmsSendRequest,
    SmsSendResponse,
    SmsVerifyRequest,
    SuggestionRequest,
    TokenPair,
)
from langou_backend.settings import InMemorySettingsRepository, SettingsRepository
from langou_backend.sms import (
    NullSmsSender,
    SmsChallengeService,
    SmsCooldownError,
    SmsDailyLimitError,
    SmsExpiredError,
    SmsInvalidCodeError,
    SmsLockedError,
)
from langou_backend.suggestions import (
    DevelopmentSuggestionProvider,
    SuggestionProvider,
    SuggestionUnavailable,
)


class ReadinessChecker(Protocol):
    async def check(self) -> dict[str, str]: ...


def create_app(
    environment: Literal["development", "test", "production"] = "production",
    *,
    token_service: TokenService | None = None,
    sms_service: SmsChallengeService | None = None,
    auth_service: AuthService | None = None,
    suggestion_provider: SuggestionProvider | None = None,
    suggestion_gate: SuggestionGate | None = None,
    history_service: HistoryService | None = None,
    settings_repository: SettingsRepository | None = None,
    release_repository: FileReleaseRepository | None = None,
    readiness_checker: ReadinessChecker | None = None,
    device_repository: DeviceRepository | None = None,
) -> FastAPI:
    expose_docs = environment != "production" and environment != "test"
    app = FastAPI(
        title="Langou API",
        version=__version__,
        default_response_class=ORJSONResponse,
        docs_url="/docs" if expose_docs else None,
        redoc_url=None,
        openapi_url="/openapi.json" if expose_docs else None,
    )
    if token_service is None:
        token_service = TokenService(_jwt_secret(environment))
    if device_repository is None:
        device_repository = InMemoryDeviceRepository()
    sms_pepper = (
        _sms_pepper(environment)
        if sms_service is None or auth_service is None
        else None
    )
    if sms_service is None:
        assert sms_pepper is not None
        sms_service = SmsChallengeService(
            sender=NullSmsSender(),
            pepper=sms_pepper,
        )
    if auth_service is None:
        assert sms_pepper is not None
        auth_service = AuthService(
            token_service=token_service,
            sms_service=sms_service,
            refresh_sessions=InMemoryRefreshSessionRepository(),
            accounts=InMemoryAccountRepository(),
            phone_pepper=sms_pepper,
            devices=device_repository,
        )
    if suggestion_provider is None:
        suggestion_provider = DevelopmentSuggestionProvider()
    if suggestion_gate is None:
        suggestion_gate = NoopSuggestionGate()
    if history_service is None:
        history_service = HistoryService(
            repository=InMemoryHistoryRepository(),
            cipher=HistoryCipher(_history_key(environment)),
        )
    if settings_repository is None:
        settings_repository = InMemorySettingsRepository()
    if release_repository is None:
        manifest_directory = os.getenv("LANGOU_RELEASE_MANIFEST_DIR")
        release_repository = FileReleaseRepository(
            Path(manifest_directory) if manifest_directory else None
        )
    bearer = HTTPBearer(auto_error=False)

    async def access_principal(
        credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    ) -> AccessPrincipal:
        if credentials is None or credentials.scheme.lower() != "bearer":
            raise HTTPException(status_code=401, detail="invalid_access_token")
        try:
            return token_service.verify_access(credentials.credentials)
        except InvalidAccessToken as exc:
            raise HTTPException(status_code=401, detail="invalid_access_token") from exc

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {
            "service": "langou-api",
            "status": "ok",
            "version": __version__,
        }

    if readiness_checker is not None:

        @app.get("/ready")
        async def readiness(response: Response) -> dict[str, Any]:
            result = await readiness_checker.check()
            if result.get("status") != "ready":
                response.status_code = 503
            return result

    @app.post("/v1/devices/guest-session", status_code=201, response_model=TokenPair)
    async def create_guest_session(request: GuestSessionRequest) -> TokenPair:
        await device_repository.register(
            request.device_id,
            platform=request.platform,
            app_version=request.app_version,
        )
        return token_service.issue_pair(request.device_id, "guest")

    @app.post("/v1/auth/sms/send", status_code=202, response_model=SmsSendResponse)
    async def send_sms(request: SmsSendRequest) -> SmsSendResponse:
        try:
            retry_after = await sms_service.request_code(request.phone)
        except (SmsCooldownError, SmsDailyLimitError) as exc:
            raise HTTPException(status_code=429, detail="sms_cooldown") from exc
        return SmsSendResponse(retry_after=retry_after)

    @app.post("/v1/auth/sms/verify", response_model=TokenPair)
    async def verify_sms(request: SmsVerifyRequest) -> TokenPair:
        try:
            return await auth_service.verify_sms(
                request.phone,
                request.code,
                request.device_id,
            )
        except (SmsInvalidCodeError, SmsExpiredError, SmsLockedError) as exc:
            raise HTTPException(status_code=401, detail="invalid_sms_code") from exc

    @app.post("/v1/auth/token/refresh", response_model=TokenPair)
    async def refresh_token(request: RefreshTokenRequest) -> TokenPair:
        try:
            return await auth_service.refresh(request.refresh_token)
        except InvalidRefreshToken as exc:
            raise HTTPException(status_code=401, detail="invalid_refresh_token") from exc

    @app.post("/v1/auth/sms/merge", response_model=MergeResponse)
    async def merge_guest(
        request: GuestMergeRequest,
        principal: Annotated[AccessPrincipal, Depends(access_principal)],
    ) -> MergeResponse:
        if principal.subject_type != "user":
            raise HTTPException(status_code=403, detail="user_session_required")
        try:
            guest_subject = await auth_service.claim_guest(request.guest_refresh_token)
        except InvalidRefreshToken as exc:
            raise HTTPException(status_code=401, detail="invalid_refresh_token") from exc
        await history_service.merge(guest_subject, principal.subject)
        await settings_repository.merge_subject(guest_subject, principal.subject)
        return MergeResponse()

    @app.post("/v1/ai/suggestions:stream")
    async def stream_suggestions(
        request: SuggestionRequest,
        principal: Annotated[AccessPrincipal, Depends(access_principal)],
    ) -> StreamingResponse:
        if principal.subject_type == "guest" and principal.subject != request.device_id:
            raise HTTPException(status_code=403, detail="device_mismatch")
        sanitized_request = sanitize_suggestion_request(request)

        async def events():
            yield _sse("meta", {"request_id": request.request_id})
            try:
                await suggestion_gate.acquire(principal.subject, request.request_id)
            except DuplicateSuggestionRequest:
                yield _sse(
                    "error",
                    {"code": "duplicate_request", "retryable": False},
                )
                return
            except RateLimitExceeded:
                yield _sse(
                    "error",
                    {"code": "rate_limited", "retryable": True},
                )
                return
            except BudgetCircuitOpen:
                yield _sse(
                    "error",
                    {"code": "service_busy", "retryable": True},
                )
                return
            try:
                suggestions = await suggestion_provider.generate(sanitized_request)
            except SuggestionUnavailable:
                yield _sse(
                    "error",
                    {"code": "suggestion_unavailable", "retryable": True},
                )
                return
            for index, suggestion in enumerate(suggestions[:3]):
                yield _sse(
                    "suggestion",
                    {
                        "index": index,
                        "style": suggestion.style,
                        "text": suggestion.text,
                    },
                )
            settings = await settings_repository.get(principal.subject)
            if sanitized_request.save_history and settings.save_history:
                await history_service.save(
                    principal.subject,
                    {
                        "application": sanitized_request.application,
                        "turns": [
                            turn.model_dump(mode="json")
                            for turn in sanitized_request.turns
                        ],
                        "suggestions": [
                            {"style": suggestion.style, "text": suggestion.text}
                            for suggestion in suggestions[:3]
                        ],
                    },
                )
            yield _sse("done", {"count": min(len(suggestions), 3)})

        return StreamingResponse(
            events(),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"},
        )

    @app.get("/v1/history")
    async def list_history(
        principal: Annotated[AccessPrincipal, Depends(access_principal)],
    ) -> dict:
        return {"items": await history_service.list(principal.subject)}

    @app.delete("/v1/history", status_code=204)
    async def delete_history(
        principal: Annotated[AccessPrincipal, Depends(access_principal)],
        history_id: Annotated[
            str | None,
            Query(alias="id", min_length=8, max_length=64),
        ] = None,
    ) -> None:
        if history_id is None:
            await history_service.delete_all(principal.subject)
            return
        if not await history_service.delete_one(principal.subject, history_id):
            raise HTTPException(status_code=404, detail="history_not_found")

    @app.get("/v1/settings", response_model=ClientSettings)
    async def get_settings(
        principal: Annotated[AccessPrincipal, Depends(access_principal)],
    ) -> ClientSettings:
        return await settings_repository.get(principal.subject)

    @app.put("/v1/settings", response_model=ClientSettings)
    async def put_settings(
        request: ClientSettings,
        principal: Annotated[AccessPrincipal, Depends(access_principal)],
    ) -> ClientSettings:
        return await settings_repository.put(principal.subject, request)

    @app.get("/v1/releases/{platform}/latest", response_model=ReleaseManifest)
    async def latest_release(platform: Literal["android", "windows"], response: Response):
        try:
            manifest = release_repository.latest(platform)
        except ReleaseNotFound as exc:
            raise HTTPException(status_code=404, detail="release_not_found") from exc
        response.headers["Cache-Control"] = "public, max-age=300"
        return manifest

    return app


def _jwt_secret(environment: str) -> str:
    configured = os.getenv("LANGOU_JWT_SECRET")
    if configured:
        return configured
    if environment == "production":
        raise RuntimeError("LANGOU_JWT_SECRET is required in production")
    if environment == "test":
        return "test-only-jwt-secret-not-for-production"
    return secrets.token_urlsafe(48)


def _sms_pepper(environment: str) -> bytes:
    configured = os.getenv("LANGOU_SMS_PEPPER")
    if configured:
        return configured.encode()
    if environment == "production":
        raise RuntimeError("LANGOU_SMS_PEPPER is required in production")
    if environment == "test":
        return b"test-only-sms-pepper-not-production"
    return secrets.token_bytes(32)


def _history_key(environment: str) -> bytes:
    configured = os.getenv("LANGOU_HISTORY_KEY")
    if configured:
        try:
            decoded = base64.urlsafe_b64decode(configured)
        except ValueError as exc:
            raise RuntimeError("LANGOU_HISTORY_KEY must be URL-safe base64") from exc
        if len(decoded) != 32:
            raise RuntimeError("LANGOU_HISTORY_KEY must decode to 32 bytes")
        return decoded
    if environment == "production":
        raise RuntimeError("LANGOU_HISTORY_KEY is required in production")
    if environment == "test":
        return b"test-only-history-key-32-bytes!!"
    return secrets.token_bytes(32)


def _sse(event: str, payload: dict) -> bytes:
    return b"event: " + event.encode() + b"\ndata: " + orjson.dumps(payload) + b"\n\n"


app = create_app(os.getenv("LANGOU_ENV", "development"))
