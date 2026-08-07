import asyncio
import hashlib
import hmac
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Literal, Protocol
from uuid import uuid4

import jwt

from langou_backend.devices import DeviceRepository
from langou_backend.schemas import TokenPair
from langou_backend.sms import SmsChallengeService


class InvalidAccessToken(Exception):
    pass


class InvalidRefreshToken(Exception):
    pass


@dataclass(frozen=True)
class AccessPrincipal:
    subject: str
    subject_type: Literal["guest", "user"]


@dataclass(frozen=True)
class RefreshPrincipal:
    subject: str
    subject_type: Literal["guest", "user"]
    jti: str
    expires_at: datetime


class RefreshSessionRepository(Protocol):
    async def consume(self, principal: RefreshPrincipal) -> bool: ...


class AccountRepository(Protocol):
    async def get_or_create(self, phone_hash: str, phone: str) -> str: ...


class InMemoryRefreshSessionRepository:
    def __init__(self) -> None:
        self._consumed: dict[str, datetime] = {}
        self._lock = asyncio.Lock()

    async def consume(self, principal: RefreshPrincipal) -> bool:
        now = datetime.now(UTC)
        async with self._lock:
            self._consumed = {
                jti: expires_at
                for jti, expires_at in self._consumed.items()
                if expires_at > now
            }
            if principal.jti in self._consumed:
                return False
            self._consumed[principal.jti] = principal.expires_at
            return True


class InMemoryAccountRepository:
    def __init__(self) -> None:
        self._users: dict[str, str] = {}
        self._lock = asyncio.Lock()

    async def get_or_create(self, phone_hash: str, phone: str) -> str:
        del phone
        async with self._lock:
            return self._users.setdefault(phone_hash, f"user_{uuid4().hex}")


class TokenService:
    ACCESS_TTL = timedelta(minutes=15)
    REFRESH_TTL = timedelta(days=30)

    def __init__(self, secret: str, issuer: str = "langou-api") -> None:
        if len(secret.encode()) < 32:
            raise ValueError("JWT secret must contain at least 32 bytes")
        self._secret = secret
        self._issuer = issuer

    def issue_pair(self, subject: str, subject_type: str) -> TokenPair:
        now = datetime.now(UTC)
        access = self._encode(
            subject=subject,
            subject_type=subject_type,
            token_use="access",  # noqa: S106 - OAuth token class, not a credential
            issued_at=now,
            expires_at=now + self.ACCESS_TTL,
        )
        refresh = self._encode(
            subject=subject,
            subject_type=subject_type,
            token_use="refresh",  # noqa: S106 - OAuth token class, not a credential
            issued_at=now,
            expires_at=now + self.REFRESH_TTL,
        )
        return TokenPair(
            access_token=access,
            refresh_token=refresh,
            expires_in=int(self.ACCESS_TTL.total_seconds()),
            subject_type=subject_type,
        )

    def verify_access(self, encoded: str) -> AccessPrincipal:
        claims = self._decode(
            encoded,
            token_use="access",  # noqa: S106 - OAuth token class, not a credential
        )
        return AccessPrincipal(
            subject=claims["sub"],
            subject_type=claims["subject_type"],
        )

    def verify_refresh(self, encoded: str) -> RefreshPrincipal:
        try:
            claims = self._decode(
                encoded,
                token_use="refresh",  # noqa: S106 - OAuth token class, not a credential
            )
        except InvalidAccessToken as exc:
            raise InvalidRefreshToken from exc
        return RefreshPrincipal(
            subject=claims["sub"],
            subject_type=claims["subject_type"],
            jti=claims["jti"],
            expires_at=datetime.fromtimestamp(claims["exp"], UTC),
        )

    def _decode(self, encoded: str, *, token_use: str) -> dict:
        try:
            claims = jwt.decode(
                encoded,
                self._secret,
                algorithms=["HS256"],
                issuer=self._issuer,
                options={"require": ["sub", "subject_type", "token_use", "exp", "iat", "jti"]},
            )
        except jwt.PyJWTError as exc:
            raise InvalidAccessToken from exc
        if claims["token_use"] != token_use:
            raise InvalidAccessToken
        if claims["subject_type"] not in {"guest", "user"}:
            raise InvalidAccessToken
        return claims

    def _encode(
        self,
        *,
        subject: str,
        subject_type: str,
        token_use: str,
        issued_at: datetime,
        expires_at: datetime,
    ) -> str:
        return jwt.encode(
            {
                "sub": subject,
                "subject_type": subject_type,
                "token_use": token_use,
                "jti": uuid4().hex,
                "iat": issued_at,
                "exp": expires_at,
                "iss": self._issuer,
            },
            self._secret,
            algorithm="HS256",
        )


class AuthService:
    def __init__(
        self,
        *,
        token_service: TokenService,
        sms_service: SmsChallengeService,
        refresh_sessions: RefreshSessionRepository,
        accounts: AccountRepository,
        phone_pepper: bytes,
        devices: DeviceRepository,
    ) -> None:
        if len(phone_pepper) < 32:
            raise ValueError("phone pepper must contain at least 32 bytes")
        self._token_service = token_service
        self._sms_service = sms_service
        self._refresh_sessions = refresh_sessions
        self._accounts = accounts
        self._phone_pepper = phone_pepper
        self._devices = devices

    async def verify_sms(self, phone: str, code: str, device_id: str) -> TokenPair:
        await self._sms_service.verify(phone, code)
        phone_hash = hmac.new(self._phone_pepper, phone.encode(), hashlib.sha256).hexdigest()
        user_id = await self._accounts.get_or_create(phone_hash, phone)
        await self._devices.attach(device_id, user_id)
        return self._token_service.issue_pair(user_id, "user")

    async def refresh(self, encoded: str) -> TokenPair:
        principal = self._token_service.verify_refresh(encoded)
        if not await self._refresh_sessions.consume(principal):
            raise InvalidRefreshToken
        return self._token_service.issue_pair(principal.subject, principal.subject_type)

    async def claim_guest(self, encoded: str) -> str:
        principal = self._token_service.verify_refresh(encoded)
        if principal.subject_type != "guest":
            raise InvalidRefreshToken
        if not await self._refresh_sessions.consume(principal):
            raise InvalidRefreshToken
        return principal.subject
