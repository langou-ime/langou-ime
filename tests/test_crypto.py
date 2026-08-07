from importlib import import_module

import pytest
from cryptography.exceptions import InvalidTag


def load_crypto():
    try:
        return import_module("langou_backend.crypto")
    except ModuleNotFoundError:
        pytest.fail("langou_backend.crypto has not been implemented")


def test_history_cipher_round_trips_without_exposing_plaintext() -> None:
    crypto = load_crypto()
    cipher = crypto.HistoryCipher(b"k" * 32)
    payload = {"turns": [{"role": "other", "text": "这是隐私消息"}]}

    encrypted = cipher.encrypt("history_123", payload)

    assert "这是隐私消息" not in encrypted
    assert cipher.decrypt("history_123", encrypted) == payload


def test_history_cipher_rejects_tampering_and_record_swaps() -> None:
    crypto = load_crypto()
    cipher = crypto.HistoryCipher(b"k" * 32)
    encrypted = cipher.encrypt("history_123", {"suggestions": ["好的"]})

    with pytest.raises(InvalidTag):
        cipher.decrypt("history_456", encrypted)

