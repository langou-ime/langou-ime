import stat
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[1]
INFRA_ROOT = BACKEND_ROOT / "infra"


def test_single_server_stack_is_isolated_private_and_non_root() -> None:
    compose = (INFRA_ROOT / "single-server/compose.production.yaml").read_text()

    assert "name: langou-v1" in compose
    assert 'user: "10001:10001"' in compose
    assert "127.0.0.1:18000:8000" in compose
    assert "5432:5432" not in compose
    assert "6379:6379" not in compose
    assert "read_only: true" in compose
    assert "/opt/langou/production-v1/data/postgres" in compose
    assert "/opt/langou/production-v1/data/redis" in compose
    assert (
        "/opt/langou/production-v1/infra/single-server/redis-entrypoint.sh:"
        "/opt/langou/redis-entrypoint.sh:ro"
    ) in compose
    assert "/etc/langou/production-v1.env" in compose
    assert (
        'image: "langou/backend:${LANGOU_BACKEND_IMAGE_TAG:?set '
        'LANGOU_BACKEND_IMAGE_TAG}"'
    ) in compose
    assert "PIP_INDEX_URL: https://mirrors.cloud.tencent.com/pypi/simple" in compose
    assert "mem_limit:" in compose
    assert "cap_drop:" in compose


def test_api_virtual_host_is_local_sse_safe_and_tls_only() -> None:
    nginx = (INFRA_ROOT / "single-server/nginx/api.langou.tech.conf").read_text()

    assert "server_name api.langou.tech;" in nginx
    assert "proxy_pass http://127.0.0.1:18000;" in nginx
    assert "proxy_buffering off;" in nginx
    assert "client_max_body_size 256k;" in nginx
    assert "ssl_certificate /etc/letsencrypt/live/api.langou.tech/fullchain.pem;" in nginx
    assert "return 301 https://$host$request_uri;" in nginx
    assert "10.77.0." not in nginx


def test_retired_topology_is_absent() -> None:
    retired = [
        INFRA_ROOT / "hong-kong",
        INFRA_ROOT / "wireguard",
        INFRA_ROOT / "backup",
        INFRA_ROOT / "systemd/langou-backup.service",
        INFRA_ROOT / "systemd/langou-backup.timer",
        INFRA_ROOT / "systemd/langou-restore-check.service",
        INFRA_ROOT / "systemd/langou-restore-check.timer",
    ]

    assert not [path for path in retired if path.exists()]


def test_local_healthcheck_covers_api_disk_memory_and_tls_without_webhook() -> None:
    healthcheck = (INFRA_ROOT / "monitoring/healthcheck.sh").read_text()

    assert "http://127.0.0.1:18000/ready" in healthcheck
    assert "/opt/langou/production-v1" in healthcheck
    assert "MemAvailable" in healthcheck
    assert "api.langou.tech" in healthcheck
    assert "LANGOU_ALERT_WEBHOOK_URL" not in healthcheck


def test_operational_units_cover_health_and_log_rotation_only() -> None:
    required = {
        "systemd/langou-healthcheck.service",
        "systemd/langou-healthcheck.timer",
        "monitoring/healthcheck.sh",
        "logrotate/langou",
    }

    missing = sorted(path for path in required if not (INFRA_ROOT / path).is_file())
    assert not missing
    service = (INFRA_ROOT / "systemd/langou-healthcheck.service").read_text()
    timer = (INFRA_ROOT / "systemd/langou-healthcheck.timer").read_text()
    assert (
        "ExecStart=/opt/langou/production-v1/infra/monitoring/healthcheck.sh"
        in service
    )
    assert "Unit=langou-healthcheck.service" in timer


def test_container_and_monitoring_scripts_are_executable() -> None:
    scripts = [
        INFRA_ROOT / "single-server/redis-entrypoint.sh",
        INFRA_ROOT / "monitoring/healthcheck.sh",
    ]

    assert not [
        path for path in scripts if not path.stat().st_mode & stat.S_IXUSR
    ]
