import base64
import os
from typing import Any

import orjson
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class HistoryCipher:
    VERSION = "v1"

    def __init__(self, key: bytes) -> None:
        if len(key) != 32:
            raise ValueError("history encryption key must contain exactly 32 bytes")
        self._cipher = AESGCM(key)

    def encrypt(self, record_id: str, payload: dict[str, Any]) -> str:
        nonce = os.urandom(12)
        plaintext = orjson.dumps(payload, option=orjson.OPT_SORT_KEYS)
        ciphertext = self._cipher.encrypt(nonce, plaintext, self._associated_data(record_id))
        encoded = base64.urlsafe_b64encode(nonce + ciphertext).decode("ascii")
        return f"{self.VERSION}.{encoded}"

    def decrypt(self, record_id: str, encrypted: str) -> dict[str, Any]:
        version, separator, encoded = encrypted.partition(".")
        if separator != "." or version != self.VERSION:
            raise ValueError("unsupported encrypted history format")
        sealed = base64.urlsafe_b64decode(encoded.encode("ascii"))
        nonce, ciphertext = sealed[:12], sealed[12:]
        plaintext = self._cipher.decrypt(nonce, ciphertext, self._associated_data(record_id))
        decoded = orjson.loads(plaintext)
        if not isinstance(decoded, dict):
            raise ValueError("encrypted history payload must be an object")
        return decoded

    @classmethod
    def _associated_data(cls, record_id: str) -> bytes:
        return f"langou-history:{cls.VERSION}:{record_id}".encode()

