import re
import time
from typing import Any
from uuid import uuid4

import structlog
from fastapi import FastAPI, Request
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine

_REQUEST_ID = re.compile(r"^[A-Za-z0-9_-]{8,64}$")


class DatabaseRedisReadiness:
    def __init__(self, *, engine: AsyncEngine, redis: Any) -> None:
        self._engine = engine
        self._redis = redis

    async def check(self) -> dict[str, str]:
        database_status = "ok"
        redis_status = "ok"
        try:
            async with self._engine.connect() as connection:
                await connection.execute(text("SELECT 1"))
        except Exception:
            database_status = "unavailable"
        try:
            if not await self._redis.ping():
                redis_status = "unavailable"
        except Exception:
            redis_status = "unavailable"
        status = (
            "ready"
            if database_status == "ok" and redis_status == "ok"
            else "not_ready"
        )
        return {
            "status": status,
            "database": database_status,
            "redis": redis_status,
        }


def install_request_observability(app: FastAPI) -> None:
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.TimeStamper(fmt="iso", utc=True),
            structlog.processors.add_log_level,
            structlog.processors.JSONRenderer(),
        ],
        cache_logger_on_first_use=True,
    )
    logger = structlog.get_logger("langou.http")

    @app.middleware("http")
    async def request_observability(request: Request, call_next):
        supplied_id = request.headers.get("x-request-id", "")
        request_id = supplied_id if _REQUEST_ID.fullmatch(supplied_id) else uuid4().hex
        started = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception:
            logger.exception(
                "http_request_failed",
                request_id=request_id,
                method=request.method,
                path=request.url.path,
            )
            raise
        response.headers["X-Request-ID"] = request_id
        logger.info(
            "http_request",
            request_id=request_id,
            method=request.method,
            path=request.url.path,
            status_code=response.status_code,
            latency_ms=round((time.perf_counter() - started) * 1000),
        )
        return response
