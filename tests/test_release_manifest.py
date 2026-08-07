import json

from fastapi.testclient import TestClient

from langou_backend.main import create_app


def test_latest_release_manifest_is_validated_and_no_store(
    tmp_path,
    monkeypatch,
) -> None:
    manifest = {
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
    (tmp_path / "android.json").write_text(json.dumps(manifest), encoding="utf-8")
    monkeypatch.setenv("LANGOU_RELEASE_MANIFEST_DIR", str(tmp_path))
    client = TestClient(create_app(environment="test"))

    response = client.get("/v1/releases/android/latest")

    assert response.status_code == 200
    assert response.json()["version"] == "1.0.0"
    assert response.headers["cache-control"] == "public, max-age=300"
