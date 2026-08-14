import base64
import binascii
from typing import Any

import orjson
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)

from langou_backend.schemas import ReleaseManifest


def sign_manifest(unsigned: dict[str, Any], private_key_base64: str) -> dict[str, Any]:
    manifest = ReleaseManifest.model_validate(
        unsigned | {"signature": "pending-signature"}
    )
    private_key = Ed25519PrivateKey.from_private_bytes(
        _decode_key(private_key_base64, "private")
    )
    signature = private_key.sign(_canonical(manifest))
    return manifest.model_dump(mode="json", exclude={"signature"}) | {
        "signature": base64.b64encode(signature).decode()
    }


def verify_manifest(signed: dict[str, Any], public_key_base64: str) -> bool:
    try:
        manifest = ReleaseManifest.model_validate(signed)
        public_key = Ed25519PublicKey.from_public_bytes(
            _decode_key(public_key_base64, "public")
        )
        public_key.verify(base64.b64decode(manifest.signature), _canonical(manifest))
    except (ValueError, binascii.Error, InvalidSignature) as exc:
        raise ValueError("invalid release signature") from exc
    return True


def _canonical(manifest: ReleaseManifest) -> bytes:
    payload = manifest.model_dump(mode="json", exclude={"signature"})
    return orjson.dumps(payload, option=orjson.OPT_SORT_KEYS)


def _decode_key(encoded: str, kind: str) -> bytes:
    try:
        decoded = base64.b64decode(encoded, validate=True)
    except binascii.Error as exc:
        raise ValueError(f"invalid {kind} release key") from exc
    if len(decoded) != 32:
        raise ValueError(f"invalid {kind} release key")
    return decoded
