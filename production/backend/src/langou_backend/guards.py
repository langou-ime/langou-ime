import hashlib
from datetime import UTC, datetime
from typing import Any, Protocol


class DuplicateSuggestionRequest(Exception):
    pass


class RateLimitExceeded(Exception):
    pass


class BudgetCircuitOpen(Exception):
    pass


class SuggestionGate(Protocol):
    async def acquire(self, subject: str, request_id: str) -> None: ...


class NoopSuggestionGate:
    async def acquire(self, subject: str, request_id: str) -> None:
        del subject, request_id


class RedisSuggestionGate:
    def __init__(
        self,
        *,
        redis: Any,
        per_minute_limit: int,
        daily_budget: int,
    ) -> None:
        if per_minute_limit < 1 or daily_budget < 1:
            raise ValueError("suggestion limits must be positive")
        self._redis = redis
        self._per_minute_limit = per_minute_limit
        self._daily_budget = daily_budget

    async def acquire(self, subject: str, request_id: str) -> None:
        now = datetime.now(UTC)
        subject_hash = hashlib.sha256(subject.encode()).hexdigest()
        request_hash = hashlib.sha256(request_id.encode()).hexdigest()
        request_key = f"langou:ai:request:{request_hash}"
        acquired = await self._redis.set(request_key, "1", ex=600, nx=True)
        if not acquired:
            raise DuplicateSuggestionRequest

        minute_key = f"langou:ai:minute:{subject_hash}:{now.strftime('%Y%m%d%H%M')}"
        minute_count = await self._redis.incr(minute_key)
        if minute_count == 1:
            await self._redis.expire(minute_key, 120)
        if minute_count > self._per_minute_limit:
            await self._redis.delete(request_key)
            raise RateLimitExceeded

        budget_key = f"langou:ai:budget:{now.strftime('%Y%m%d')}"
        budget_count = await self._redis.incr(budget_key)
        if budget_count == 1:
            await self._redis.expire(budget_key, 172800)
        if budget_count > self._daily_budget:
            raise BudgetCircuitOpen
