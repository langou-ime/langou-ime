FROM python:3.12-slim AS builder

ARG PIP_INDEX_URL=https://pypi.org/simple
ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_DEFAULT_TIMEOUT=120 \
    PIP_RETRIES=10
WORKDIR /build
COPY pyproject.toml requirements.lock README.md ./
COPY src ./src
COPY alembic ./alembic
COPY alembic.ini ./
RUN python -m pip wheel --no-cache-dir --index-url "${PIP_INDEX_URL}" \
        --require-hashes \
        --wheel-dir /wheels -r requirements.lock \
    && python -m pip wheel --no-cache-dir --no-deps --wheel-dir /wheels .

FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH=/opt/langou/bin:$PATH
RUN groupadd --gid 10001 langou \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin langou
COPY --from=builder /wheels /wheels
RUN python -m venv /opt/langou \
    && /opt/langou/bin/pip install --no-cache-dir /wheels/* \
    && rm -rf /wheels
WORKDIR /app
COPY alembic ./alembic
COPY alembic.ini ./
COPY docker/entrypoint.sh /usr/local/bin/langou-entrypoint
RUN chmod 0555 /usr/local/bin/langou-entrypoint \
    && mkdir -p /srv/releases \
    && chown -R 10001:10001 /app /srv/releases
USER 10001:10001
EXPOSE 8000
ENTRYPOINT ["langou-entrypoint"]
