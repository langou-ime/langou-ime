from base64 import b64encode
from datetime import UTC, datetime

import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from langou_backend.release_signing import sign_manifest, verify_manifest


def test_release_manifest_signature_covers_all_update_fields() -> None:
    private_key = Ed25519PrivateKey.generate()
    public_key = private_key.public_key()
    unsigned = {
        "platform": "android",
        "version": "1.0.0",
        "minimum_supported_version": "1.0.0",
        "mandatory": False,
        "url": "https://api.langou.tech/releases/langou-1.0.0.apk",
        "size": 12345678,
        "sha256": "a" * 64,
        "published_at": datetime(2026, 7, 26, 12, 0, tzinfo=UTC),
    }

    signed = sign_manifest(
        unsigned,
        b64encode(private_key.private_bytes_raw()).decode(),
    )

    assert verify_manifest(signed, b64encode(public_key.public_bytes_raw()).decode())
    with pytest.raises(ValueError, match="signature"):
        verify_manifest(
            signed | {"url": "https://evil.example/app.apk"},
            b64encode(public_key.public_bytes_raw()).decode(),
        )
