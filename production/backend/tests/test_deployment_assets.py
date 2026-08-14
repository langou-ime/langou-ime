from pathlib import Path

import yaml

ROOT = Path(__file__).parents[1]


def test_compose_keeps_datastores_private_and_api_non_root() -> None:
    compose = yaml.safe_load((ROOT / "compose.yaml").read_text())
    services = compose["services"]

    assert "ports" not in services["postgres"]
    assert "ports" not in services["redis"]
    assert services["api"]["user"] == "10001:10001"
    assert services["api"]["read_only"] is True
    assert services["api"]["ports"] == [
        "${LANGOU_BIND_IP:-127.0.0.1}:8000:8000"
    ]
    assert services["api"]["volumes"][0].endswith(":/srv/releases:ro")


def test_environment_example_contains_placeholders_not_runtime_credentials() -> None:
    example = (ROOT / ".env.example").read_text()

    assert "LANGOU_ENVIRONMENT=production" in example
    assert "replace-with-" in example
    assert "SMS_123456789" not in example
    assert "configured-at-runtime" not in example


def test_docker_build_tolerates_slow_package_metadata_downloads() -> None:
    dockerfile = (ROOT / "Dockerfile").read_text()

    assert "ARG PIP_INDEX_URL=https://pypi.org/simple" in dockerfile
    assert '--index-url "${PIP_INDEX_URL}"' in dockerfile
    assert "PIP_DEFAULT_TIMEOUT=120" in dockerfile
    assert "PIP_RETRIES=10" in dockerfile
