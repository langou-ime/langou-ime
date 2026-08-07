#!/usr/bin/env bash
set -Eeuo pipefail

readonly api_ready_url="${LANGOU_READY_URL:-http://127.0.0.1:18000/ready}"
readonly disk_mount="${LANGOU_DISK_MOUNT:-/opt/langou/production-v1}"
readonly disk_limit="${LANGOU_DISK_PERCENT_LIMIT:-85}"
readonly memory_limit="${LANGOU_MEMORY_PERCENT_LIMIT:-90}"
readonly tls_host="${LANGOU_TLS_HOST:-api.langou.tech}"
readonly tls_days="${LANGOU_TLS_MIN_DAYS:-14}"

failures=()

if ! curl --fail --silent --show-error --max-time 5 "${api_ready_url}" >/dev/null; then
    failures+=("API readiness failed")
fi

disk_used="$(df -P "${disk_mount}" | awk 'NR == 2 {gsub("%", "", $5); print $5}')"
if [[ ! "${disk_used}" =~ ^[0-9]+$ ]] || (( disk_used >= disk_limit )); then
    failures+=("disk usage is ${disk_used:-unknown}%")
fi

memory_total="$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)"
memory_available="$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)"
if [[ "${memory_total}" =~ ^[0-9]+$ && "${memory_available}" =~ ^[0-9]+$ ]]; then
    memory_used=$((100 - (memory_available * 100 / memory_total)))
    if (( memory_used >= memory_limit )); then
        failures+=("memory usage is ${memory_used}%")
    fi
else
    failures+=("memory metrics unavailable")
fi

if [[ "${LANGOU_CHECK_TLS:-1}" = "1" ]]; then
    certificate="$(openssl s_client -connect "${tls_host}:443" -servername "${tls_host}" </dev/null 2>/dev/null |
        openssl x509 -outform PEM 2>/dev/null || true)"
    if [[ -z "${certificate}" ]] ||
        ! openssl x509 -checkend "$((tls_days * 86400))" -noout <<<"${certificate}" >/dev/null; then
        failures+=("TLS certificate is missing or expires within ${tls_days} days")
    fi
fi

if ((${#failures[@]} > 0)); then
    message="Langou production alert: $(IFS='; '; printf '%s' "${failures[*]}")"
    logger --tag langou-healthcheck -- "${message}"
    printf '%s\n' "${message}" >&2
    exit 1
fi
