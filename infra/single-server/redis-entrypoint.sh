#!/bin/sh
set -eu

test -n "${LANGOU_REDIS_PASSWORD:-}"

config_file=/run/redis-langou.conf
umask 077
printf '%s\n' \
    'bind 0.0.0.0' \
    'protected-mode yes' \
    'appendonly yes' \
    'appendfsync everysec' \
    "requirepass ${LANGOU_REDIS_PASSWORD}" > "${config_file}"
unset LANGOU_REDIS_PASSWORD

exec redis-server "${config_file}"
