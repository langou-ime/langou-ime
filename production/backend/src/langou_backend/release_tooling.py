import base64
import hashlib
import os
import stat
from datetime import UTC, datetime
from pathlib import Path
from typing import Literal
from urllib.parse import unquote, urlsplit

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from langou_backend.release_signing import sign_manifest

ReleasePlatform = Literal["android", "windows"]


def generate_release_keypair(private_key_file: Path, public_key_file: Path) -> None:
    if private_key_file.exists():
        raise FileExistsError(private_key_file)
    if public_key_file.exists():
        raise FileExistsError(public_key_file)

    private_key = Ed25519PrivateKey.generate()
    _write_exclusive(
        private_key_file,
        base64.b64encode(private_key.private_bytes_raw()) + b"\n",
        mode=0o600,
    )
    try:
        _write_exclusive(
            public_key_file,
            base64.b64encode(private_key.public_key().public_bytes_raw()) + b"\n",
            mode=0o644,
        )
    except Exception:
        private_key_file.unlink(missing_ok=True)
        raise


def build_signed_artifact_manifest(
    *,
    platform: ReleasePlatform,
    artifact: Path,
    url: str,
    private_key_file: Path,
    published_at: datetime | None = None,
    version: str = "1.0.0",
    minimum_supported_version: str = "1.0.0",
    mandatory: bool = False,
) -> dict[str, object]:
    _require_artifact(platform, artifact, version=version, url=url)
    _require_private_permissions(private_key_file)
    private_key_base64 = private_key_file.read_text(encoding="ascii").strip()
    timestamp = published_at or datetime.now(UTC)
    if timestamp.tzinfo is None:
        raise ValueError("published_at must include a timezone")

    unsigned: dict[str, object] = {
        "platform": platform,
        "version": version,
        "minimum_supported_version": minimum_supported_version,
        "mandatory": mandatory,
        "url": url,
        "size": artifact.stat().st_size,
        "sha256": _sha256(artifact),
        "published_at": timestamp.astimezone(UTC),
    }
    return sign_manifest(unsigned, private_key_base64)


def _write_exclusive(path: Path, payload: bytes, *, mode: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(payload)
            output.flush()
            os.fsync(output.fileno())
    except Exception:
        path.unlink(missing_ok=True)
        raise
    os.chmod(path, mode)


def _require_artifact(
    platform: ReleasePlatform,
    artifact: Path,
    *,
    version: str,
    url: str,
) -> None:
    if not artifact.is_file():
        raise FileNotFoundError(artifact)
    required_suffix = ".apk" if platform == "android" else ".exe"
    if artifact.suffix.lower() != required_suffix:
        raise ValueError(f"{platform} release artifact must use {required_suffix}")
    expected_name = (
        f"langou-ime-android-v{version}.apk"
        if platform == "android"
        else f"langou-ime-windows-x64-v{version}.exe"
    )
    if artifact.name != expected_name:
        raise ValueError(f"{platform} release artifact must be named {expected_name}")
    download_name = Path(unquote(urlsplit(url).path)).name
    if download_name != expected_name:
        raise ValueError(f"download URL must end with {expected_name}")
    if artifact.stat().st_size <= 0:
        raise ValueError("release artifact must not be empty")


def _require_private_permissions(private_key_file: Path) -> None:
    mode = stat.S_IMODE(private_key_file.stat().st_mode)
    if mode & 0o077:
        raise PermissionError("release private key must not be group/world accessible")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
