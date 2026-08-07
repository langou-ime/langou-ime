from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = BACKEND_ROOT / ".github" / "workflows" / "ci.yml"


def test_ci_uses_locked_dependencies_and_minimum_permissions() -> None:
    workflow = WORKFLOW.read_text()

    assert "permissions:\n  contents: read" in workflow
    assert "requirements.lock" in workflow
    assert "requirements-dev.lock" in workflow
    assert "pip install --require-hashes --requirement requirements.lock" in workflow
    assert "pip install --no-deps ." in workflow
    assert "pytest -q tests" in workflow
    assert "ruff check src tests scripts" in workflow
    assert "pip-audit==2.10.1" in workflow
    assert "pip-audit --requirement requirements.lock --disable-pip" in workflow


def test_ci_pins_third_party_actions_to_full_commit_shas() -> None:
    workflow = WORKFLOW.read_text()

    action_lines = [
        line.strip()
        for line in workflow.splitlines()
        if line.strip().startswith("uses:")
    ]
    assert action_lines
    for line in action_lines:
        ref = line.split("@", maxsplit=1)[1].split()[0]
        assert len(ref) == 40
        assert all(character in "0123456789abcdef" for character in ref)
