from importlib import import_module

import pytest
from pydantic import ValidationError


def load_schemas():
    try:
        return import_module("langou_backend.schemas")
    except ModuleNotFoundError:
        pytest.fail("langou_backend.schemas has not been implemented")


def valid_request() -> dict:
    return {
        "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
        "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
        "application": "wechat",
        "locale": "zh-CN",
        "turns": [
            {"role": "other", "text": "今晚有空一起吃饭吗？"},
            {"role": "self", "text": "我看看安排"},
        ],
        "save_history": True,
    }


def test_suggestion_request_forbids_screenshots() -> None:
    schemas = load_schemas()
    payload = valid_request() | {"screenshot": "base64-image-data"}

    with pytest.raises(ValidationError, match="screenshot"):
        schemas.SuggestionRequest.model_validate(payload)


def test_suggestion_request_limits_context_size() -> None:
    schemas = load_schemas()
    payload = valid_request()
    payload["turns"] = [{"role": "other", "text": "x"}] * 13

    with pytest.raises(ValidationError, match="turns"):
        schemas.SuggestionRequest.model_validate(payload)


def test_release_manifest_requires_https_and_sha256() -> None:
    schemas = load_schemas()

    with pytest.raises(ValidationError):
        schemas.ReleaseManifest.model_validate(
            {
                "platform": "android",
                "version": "1.0.0",
                "minimum_supported_version": "1.0.0",
                "mandatory": False,
                "url": "http://api.langou.tech/downloads/langou.apk",
                "size": 123,
                "sha256": "not-a-digest",
                "signature": "test",
                "published_at": "2026-07-26T00:00:00Z",
            }
        )

