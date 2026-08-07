import asyncio
import hashlib
import hmac
import json
import secrets
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, Protocol
from urllib.parse import quote

import httpx


class SmsCooldownError(Exception):
    pass


class SmsInvalidCodeError(Exception):
    pass


class SmsExpiredError(Exception):
    pass


class SmsLockedError(Exception):
    pass


class SmsDailyLimitError(Exception):
    pass


class SmsDeliveryError(Exception):
    pass


class SmsSender(Protocol):
    async def send(self, phone: str, code: str) -> None: ...


class NullSmsSender:
    """Development sender that intentionally discards the code."""

    async def send(self, phone: str, code: str) -> None:
        del phone, code


@dataclass
class StoredChallenge:
    code_digest: str
    expires_at: float
    next_send_at: float
    failed_attempts: int = 0


class SmsChallengeService:
    COOLDOWN_SECONDS = 60
    EXPIRY_SECONDS = 300
    MAX_FAILED_ATTEMPTS = 5

    def __init__(
        self,
        *,
        sender: SmsSender,
        pepper: bytes,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        if len(pepper) < 32:
            raise ValueError("SMS pepper must contain at least 32 bytes")
        self._sender = sender
        self._pepper = pepper
        self._clock = clock
        self._challenges: dict[str, StoredChallenge] = {}
        self._lock = asyncio.Lock()

    async def request_code(self, phone: str) -> int:
        async with self._lock:
            now = self._clock()
            existing = self._challenges.get(phone)
            if existing and existing.next_send_at > now:
                raise SmsCooldownError
            code = f"{secrets.randbelow(1_000_000):06d}"
            self._challenges[phone] = StoredChallenge(
                code_digest=self._digest(phone, code),
                expires_at=now + self.EXPIRY_SECONDS,
                next_send_at=now + self.COOLDOWN_SECONDS,
            )
        await self._sender.send(phone, code)
        return self.COOLDOWN_SECONDS

    async def verify(self, phone: str, code: str) -> None:
        async with self._lock:
            now = self._clock()
            challenge = self._challenges.get(phone)
            if challenge is None:
                raise SmsInvalidCodeError
            if challenge.expires_at <= now:
                del self._challenges[phone]
                raise SmsExpiredError
            if challenge.failed_attempts >= self.MAX_FAILED_ATTEMPTS:
                del self._challenges[phone]
                raise SmsLockedError
            if not hmac.compare_digest(challenge.code_digest, self._digest(phone, code)):
                challenge.failed_attempts += 1
                if challenge.failed_attempts >= self.MAX_FAILED_ATTEMPTS:
                    del self._challenges[phone]
                    raise SmsLockedError
                raise SmsInvalidCodeError
            del self._challenges[phone]

    def _digest(self, phone: str, code: str) -> str:
        return hmac.new(self._pepper, f"{phone}:{code}".encode(), hashlib.sha256).hexdigest()


class RedisSmsChallengeService:
    COOLDOWN_SECONDS = 60
    EXPIRY_SECONDS = 300
    MAX_FAILED_ATTEMPTS = 5
    DAILY_LIMIT = 20

    def __init__(self, *, redis: Any, sender: SmsSender, pepper: bytes) -> None:
        if len(pepper) < 32:
            raise ValueError("SMS pepper must contain at least 32 bytes")
        self._redis = redis
        self._sender = sender
        self._pepper = pepper

    async def request_code(self, phone: str) -> int:
        phone_hash = self._phone_hash(phone)
        cooldown_key = f"langou:sms:cooldown:{phone_hash}"
        challenge_key = f"langou:sms:challenge:{phone_hash}"
        daily_key = f"langou:sms:daily:{phone_hash}:{time.strftime('%Y-%m-%d', time.gmtime())}"
        acquired = await self._redis.set(
            cooldown_key,
            "1",
            ex=self.COOLDOWN_SECONDS,
            nx=True,
        )
        if not acquired:
            raise SmsCooldownError
        daily_count = await self._redis.incr(daily_key)
        if daily_count == 1:
            await self._redis.expire(daily_key, 172800)
        if daily_count > self.DAILY_LIMIT:
            await self._redis.delete(cooldown_key)
            raise SmsDailyLimitError
        code = f"{secrets.randbelow(1_000_000):06d}"
        await self._redis.hset(
            challenge_key,
            mapping={
                "digest": self._digest(phone, code),
                "attempts": "0",
            },
        )
        await self._redis.expire(challenge_key, self.EXPIRY_SECONDS)
        try:
            await self._sender.send(phone, code)
        except Exception:
            await self._redis.delete(challenge_key, cooldown_key)
            raise
        return self.COOLDOWN_SECONDS

    async def verify(self, phone: str, code: str) -> None:
        challenge_key = f"langou:sms:challenge:{self._phone_hash(phone)}"
        stored = _decode_hash(await self._redis.hgetall(challenge_key))
        if not stored:
            raise SmsExpiredError
        if not hmac.compare_digest(stored.get("digest", ""), self._digest(phone, code)):
            attempts = await self._redis.hincrby(challenge_key, "attempts", 1)
            if attempts >= self.MAX_FAILED_ATTEMPTS:
                await self._redis.delete(challenge_key)
                raise SmsLockedError
            raise SmsInvalidCodeError
        await self._redis.delete(challenge_key)

    def _phone_hash(self, phone: str) -> str:
        return hmac.new(self._pepper, phone.encode(), hashlib.sha256).hexdigest()

    def _digest(self, phone: str, code: str) -> str:
        return hmac.new(self._pepper, f"{phone}:{code}".encode(), hashlib.sha256).hexdigest()


def _decode_hash(value: dict[Any, Any]) -> dict[str, str]:
    return {
        key.decode() if isinstance(key, bytes) else str(key): (
            item.decode() if isinstance(item, bytes) else str(item)
        )
        for key, item in value.items()
    }


class AliyunSmsSender:
    HOST = "dysmsapi.aliyuncs.com"
    ACTION = "SendSms"
    VERSION = "2017-05-25"

    def __init__(
        self,
        *,
        access_key_id: str,
        access_key_secret: str,
        sign_name: str,
        template_code: str,
        client: httpx.AsyncClient,
        date_factory: Callable[[], str] | None = None,
        nonce_factory: Callable[[], str] | None = None,
    ) -> None:
        self._access_key_id = access_key_id
        self._access_key_secret = access_key_secret
        self._client = client
        self._sign_name = sign_name
        self._template_code = template_code
        self._date_factory = date_factory or _utc_timestamp
        self._nonce_factory = nonce_factory or (lambda: str(uuid.uuid4()))

    @classmethod
    def from_credentials(
        cls,
        *,
        access_key_id: str,
        access_key_secret: str,
        sign_name: str,
        template_code: str,
    ) -> "AliyunSmsSender":
        return cls(
            access_key_id=access_key_id,
            access_key_secret=access_key_secret,
            sign_name=sign_name,
            template_code=template_code,
            client=httpx.AsyncClient(
                timeout=httpx.Timeout(5.0, connect=3.0),
            ),
        )

    async def send(self, phone: str, code: str) -> None:
        domestic_phone = phone[3:] if phone.startswith("+86") else phone.lstrip("+")
        query = {
            "PhoneNumbers": domestic_phone,
            "SignName": self._sign_name,
            "TemplateCode": self._template_code,
            "TemplateParam": json.dumps(
                {"code": code},
                ensure_ascii=False,
                separators=(",", ":"),
            ),
        }
        payload = b""
        date = self._date_factory()
        nonce = self._nonce_factory()
        headers = {
            "host": self.HOST,
            "x-acs-action": self.ACTION,
            "x-acs-content-sha256": hashlib.sha256(payload).hexdigest(),
            "x-acs-date": date,
            "x-acs-signature-nonce": nonce,
            "x-acs-version": self.VERSION,
            "authorization": _build_acs3_authorization(
                access_key_id=self._access_key_id,
                access_key_secret=self._access_key_secret,
                method="POST",
                host=self.HOST,
                action=self.ACTION,
                version=self.VERSION,
                date=date,
                nonce=nonce,
                query=query,
                payload=payload,
            ),
        }
        url = f"https://{self.HOST}/?{_canonical_query(query)}"
        try:
            response = await self._client.post(url, headers=headers, content=payload)
            response.raise_for_status()
            result = response.json()
        except (httpx.HTTPError, ValueError):
            raise SmsDeliveryError from None
        if not isinstance(result, dict) or result.get("Code") != "OK":
            raise SmsDeliveryError


def _build_acs3_authorization(
    *,
    access_key_id: str,
    access_key_secret: str,
    method: str,
    host: str,
    action: str,
    version: str,
    date: str,
    nonce: str,
    query: dict[str, str],
    payload: bytes,
) -> str:
    algorithm = "ACS3-HMAC-SHA256"
    payload_hash = hashlib.sha256(payload).hexdigest()
    signed = {
        "host": host,
        "x-acs-action": action,
        "x-acs-content-sha256": payload_hash,
        "x-acs-date": date,
        "x-acs-signature-nonce": nonce,
        "x-acs-version": version,
    }
    signed_header_names = ";".join(sorted(signed))
    canonical_headers = "".join(f"{name}:{signed[name].strip()}\n" for name in sorted(signed))
    canonical_request = "\n".join(
        (
            method.upper(),
            "/",
            _canonical_query(query),
            canonical_headers,
            signed_header_names,
            payload_hash,
        )
    )
    string_to_sign = f"{algorithm}\n{hashlib.sha256(canonical_request.encode()).hexdigest()}"
    signature = hmac.new(
        access_key_secret.encode(),
        string_to_sign.encode(),
        hashlib.sha256,
    ).hexdigest()
    return (
        f"{algorithm} Credential={access_key_id},"
        f"SignedHeaders={signed_header_names},Signature={signature}"
    )


def _canonical_query(query: dict[str, str]) -> str:
    return "&".join(
        f"{quote(name, safe='-_.~')}={quote(value, safe='-_.~')}"
        for name, value in sorted(query.items())
    )


def _utc_timestamp() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
