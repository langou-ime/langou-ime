import json

from fastapi.testclient import TestClient

from langou_backend.main import create_app


def test_latest_release_manifest_is_validated_and_no_store(
    tmp_path,
    monkeypatch,
) -> None:
    android_manifest = {
        "platform": "android",
        "version": "1.0.0",
        "minimum_supported_version": "1.0.0",
        "mandatory": False,
        "url": "https://api.langou.tech/downloads/langou-ime-android-v1.0.0.apk",
        "size": 123456789,
        "sha256": "a" * 64,
        "signature": "base64-signature",
        "published_at": "2026-07-26T00:00:00Z",
    }
    windows_manifest = {
        "platform": "windows",
        "version": "1.0.0",
        "minimum_supported_version": "1.0.0",
        "mandatory": False,
        "url": "https://api.langou.tech/downloads/langou-ime-windows-x64-v1.0.0.exe",
        "size": 987654321,
        "sha256": "b" * 64,
        "signature": "base64-signature",
        "published_at": "2026-07-26T00:00:00Z",
    }
    (tmp_path / "android.json").write_text(json.dumps(android_manifest), encoding="utf-8")
    (tmp_path / "windows.json").write_text(json.dumps(windows_manifest), encoding="utf-8")
    monkeypatch.setenv("LANGOU_RELEASE_MANIFEST_DIR", str(tmp_path))
    client = TestClient(create_app(environment="test"))

    android = client.get("/v1/releases/android/latest")
    windows = client.get("/v1/releases/windows/latest")

    assert android.status_code == 200
    assert android.json()["platform"] == "android"
    assert android.json()["version"] == "1.0.0"
    assert android.headers["cache-control"] == "public, max-age=300"

    assert windows.status_code == 200
    assert windows.json()["platform"] == "windows"
    assert windows.json()["url"].endswith(".exe")
    assert windows.headers["cache-control"] == "public, max-age=300"
