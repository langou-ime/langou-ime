import pytest

from langou_backend.guards import (
    BudgetCircuitOpen,
    DuplicateSuggestionRequest,
    RateLimitExceeded,
    RedisSuggestionGate,
)


class FakeRedis:
    def __init__(self) -> None:
        self.values: dict[str, int | str] = {}

    async def set(self, key: str, value: str, *, ex: int, nx: bool) -> bool:
        del ex
        if nx and key in self.values:
            return False
        self.values[key] = value
        return True

    async def incr(self, key: str) -> int:
        value = int(self.values.get(key, 0)) + 1
        self.values[key] = value
        return value

    async def expire(self, key: str, seconds: int) -> None:
        del key, seconds

    async def delete(self, *keys: str) -> None:
        for key in keys:
            self.values.pop(key, None)


@pytest.mark.asyncio
async def test_gate_deduplicates_and_limits_without_exposing_quota_values() -> None:
    redis = FakeRedis()
    gate = RedisSuggestionGate(redis=redis, per_minute_limit=2, daily_budget=3)

    await gate.acquire("dev_one", "req_one")
    with pytest.raises(DuplicateSuggestionRequest):
        await gate.acquire("dev_one", "req_one")
    await gate.acquire("dev_one", "req_two")
    with pytest.raises(RateLimitExceeded):
        await gate.acquire("dev_one", "req_three")
    await gate.acquire("dev_two", "req_four")
    with pytest.raises(BudgetCircuitOpen):
        await gate.acquire("dev_three", "req_five")

    assert all("dev_one" not in key for key in redis.values)
