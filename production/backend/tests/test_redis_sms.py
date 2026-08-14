from collections.abc import Mapping

import pytest

from langou_backend.sms import (
    RedisSmsChallengeService,
    SmsCooldownError,
    SmsInvalidCodeError,
)


class FakeRedis:
    def __init__(self) -> None:
        self.values: dict[str, str | dict[str, str]] = {}

    async def set(self, key: str, value: str, *, ex: int, nx: bool) -> bool:
        del ex
        if nx and key in self.values:
            return False
        self.values[key] = value
        return True

    async def hset(self, key: str, mapping: Mapping[str, str]) -> None:
        self.values[key] = dict(mapping)

    async def expire(self, key: str, seconds: int) -> None:
        del key, seconds

    async def incr(self, key: str) -> int:
        value = int(self.values.get(key, "0")) + 1
        self.values[key] = str(value)
        return value

    async def delete(self, *keys: str) -> None:
        for key in keys:
            self.values.pop(key, None)

    async def hgetall(self, key: str) -> dict[str, str]:
        value = self.values.get(key, {})
        return dict(value) if isinstance(value, dict) else {}

    async def hincrby(self, key: str, field: str, increment: int) -> int:
        value = self.values[key]
        assert isinstance(value, dict)
        current = int(value.get(field, "0")) + increment
        value[field] = str(current)
        return current


class CapturingAsyncSender:
    def __init__(self) -> None:
        self.code = ""

    async def send(self, phone: str, code: str) -> None:
        del phone
        self.code = code


@pytest.mark.asyncio
async def test_redis_sms_uses_hashed_keys_and_enforces_shared_cooldown() -> None:
    redis = FakeRedis()
    sender = CapturingAsyncSender()
    service = RedisSmsChallengeService(
        redis=redis,
        sender=sender,
        pepper=b"r" * 32,
    )
    phone = "+8613800138000"

    assert await service.request_code(phone) == 60
    with pytest.raises(SmsCooldownError):
        await service.request_code(phone)
    with pytest.raises(SmsInvalidCodeError):
        await service.verify(phone, "000000" if sender.code != "000000" else "000001")
    await service.verify(phone, sender.code)

    assert all(phone not in key for key in redis.values)
    assert all(sender.code not in str(value) for value in redis.values.values())
