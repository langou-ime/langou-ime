#!/bin/sh
set -eu

alembic upgrade head
exec uvicorn langou_backend.entrypoint:app \
  --host 0.0.0.0 \
  --port 8000 \
  --workers "${LANGOU_WORKERS:-2}" \
  --proxy-headers \
  --forwarded-allow-ips "${LANGOU_FORWARDED_ALLOW_IPS:-127.0.0.1}"
