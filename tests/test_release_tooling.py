import base64
import json
import stat
from datetime import UTC, datetime

import pytest

from langou_backend.release_signing import verify_manifest
from langou_backend.release_tooling import (
    build_signed_artifact_manifest,
    generate_release_keypair,
)


def test_build_manifest_uses_the_actual_artifact_hash_and_size(tmp_path) -> None:
    artifact = tmp_path / "langou-ime-android-v1.0.0.apk"
    artifact.write_bytes(b"real signed apk bytes")
    private_key_file = tmp_path / "release-private.key"
    public_key_file = tmp_path / "release-public.key"
    generate_release_keypair(private_key_file, public_key_file)

    manifest = build_signed_artifact_manifest(
        platform="android",
        artifact=artifact,
        url="https://api.langou.tech/downloads/langou-ime-android-v1.0.0.apk",
        private_key_file=private_key_file,
        published_at=datetime(2026, 7, 26, tzinfo=UTC),
    )

    assert manifest["size"] == len(b"real signed apk bytes")
    assert manifest["sha256"] == (
        "aa01c37919b3db286b59e32f10547d3be380e9286e2c3f0fe7e5ec525a5882e9"
    )
    assert verify_manifest(manifest, public_key_file.read_text().strip())


def test_key_generation_is_private_and_refuses_overwrite(tmp_path) -> None:
    private_key_file = tmp_path / "release-private.key"
    public_key_file = tmp_path / "release-public.key"

    generate_release_keypair(private_key_file, public_key_file)

    assert stat.S_IMODE(private_key_file.stat().st_mode) == 0o600
    assert len(base64.b64decode(private_key_file.read_text().strip())) == 32
    assert len(base64.b64decode(public_key_file.read_text().strip())) == 32
    with pytest.raises(FileExistsError):
        generate_release_keypair(private_key_file, public_key_file)


def test_signed_manifest_is_json_serializable(tmp_path) -> None:
    artifact = tmp_path / "langou-ime-windows-x64-v1.0.0.msi"
    artifact.write_bytes(b"signed msi")
    private_key_file = tmp_path / "release-private.key"
    public_key_file = tmp_path / "release-public.key"
    generate_release_keypair(private_key_file, public_key_file)

    manifest = build_signed_artifact_manifest(
        platform="windows",
        artifact=artifact,
        url="https://api.langou.tech/downloads/langou-ime-windows-x64-v1.0.0.msi",
        private_key_file=private_key_file,
        published_at=datetime(2026, 7, 26, tzinfo=UTC),
    )

    json.dumps(manifest)


def test_manifest_tool_rejects_noncanonical_or_mismatched_public_filename(
    tmp_path,
) -> None:
    artifact = tmp_path / "renamed.apk"
    artifact.write_bytes(b"signed apk")
    private_key_file = tmp_path / "release-private.key"
    public_key_file = tmp_path / "release-public.key"
    generate_release_keypair(private_key_file, public_key_file)

    with pytest.raises(ValueError, match="langou-ime-android-v1.0.0.apk"):
        build_signed_artifact_manifest(
            platform="android",
            artifact=artifact,
            url="https://api.langou.tech/downloads/renamed.apk",
            private_key_file=private_key_file,
        )

    artifact.rename(tmp_path / "langou-ime-android-v1.0.0.apk")
    with pytest.raises(ValueError, match="download URL"):
        build_signed_artifact_manifest(
            platform="android",
            artifact=tmp_path / "langou-ime-android-v1.0.0.apk",
            url="https://api.langou.tech/downloads/wrong.apk",
            private_key_file=private_key_file,
        )
