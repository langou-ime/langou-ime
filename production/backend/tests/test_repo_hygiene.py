from pathlib import Path

REPOSITORY_ROOT = Path(__file__).parents[3]


def test_root_gitignore_blocks_release_binaries_and_local_secrets() -> None:
    gitignore = (REPOSITORY_ROOT / ".gitignore").read_text(encoding="utf-8")

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


def test_monorepo_ci_runs_all_product_surfaces_from_their_component_roots() -> None:
    workflow = (REPOSITORY_ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")

    for component in ("android", "windows", "backend", "website"):
        assert f"production/{component}" in workflow
    assert "langou-ime-windows-x64-v1.0.0.exe" in workflow
    assert ".msi" not in workflow.lower()


def test_one_tag_builds_both_signed_installers_and_one_draft_release() -> None:
    workflow = (REPOSITORY_ROOT / ".github/workflows/release.yml").read_text(
        encoding="utf-8"
    )

    assert '"v1.0.0"' in workflow
    assert "ANDROID_KEYSTORE_BASE64" in workflow
    assert "signpath/github-action-submit-signing-request@" in workflow
    assert "langou-ime-android-v1.0.0.apk" in workflow
    assert "langou-ime-windows-x64-v1.0.0.exe" in workflow
    assert "gh release create" in workflow
    assert "--draft" in workflow
    assert ".msi" not in workflow.lower()
