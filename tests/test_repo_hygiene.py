from pathlib import Path


def test_root_gitignore_blocks_release_binaries_and_local_secrets() -> None:
    gitignore = (Path(__file__).parents[3] / ".gitignore").read_text(encoding="utf-8")

    assert "*.apk" in gitignore
    assert "*.exe" in gitignore
    assert "*.msi" in gitignore
    assert "*.sha256" in gitignore
    assert "*.jks" in gitignore
    assert "*.keystore" in gitignore
    assert "*.pfx" in gitignore
    assert "*.key" in gitignore
    assert ".env" in gitignore
    assert ".env.*" in gitignore
    assert ".gradle/" in gitignore
    assert "production/releases/" in gitignore
    assert "production/windows/output/assistant-runtime/" in gitignore
