import re
import tomllib
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[1]


def normalize(name: str) -> str:
    return re.sub(r"[-_.]+", "-", name).lower()


def logical_requirements() -> list[str]:
    lock = (BACKEND_ROOT / "requirements.lock").read_text()
    return [
        block.strip()
        for block in re.split(r"\n(?=[A-Za-z0-9])", lock)
        if "==" in block and not block.lstrip().startswith("#")
    ]


def test_every_direct_production_dependency_is_exactly_locked() -> None:
    project = tomllib.loads((BACKEND_ROOT / "pyproject.toml").read_text())
    direct = {
        normalize(re.split(r"[<>=!~\[]", dependency, maxsplit=1)[0])
        for dependency in project["project"]["dependencies"]
    }
    locked = {
        normalize(requirement.split("==", maxsplit=1)[0]) for requirement in logical_requirements()
    }

    assert direct <= locked
    assert all("==" in requirement for requirement in logical_requirements())


def test_container_build_uses_the_exact_lock_and_no_deps_project_wheel() -> None:
    dockerfile = (BACKEND_ROOT / "Dockerfile").read_text()

    assert "pip wheel --no-cache-dir --index-url" in dockerfile
    assert "--wheel-dir /wheels -r requirements.lock" in dockerfile
    assert "--require-hashes" in dockerfile
    assert "pip wheel --no-cache-dir --no-deps --wheel-dir /wheels ." in dockerfile


def test_production_lock_pins_patched_cryptography_and_hashes_every_package() -> None:
    project = tomllib.loads((BACKEND_ROOT / "pyproject.toml").read_text())
    cryptography = next(
        dependency
        for dependency in project["project"]["dependencies"]
        if normalize(dependency).startswith("cryptography")
    )
    assert cryptography == "cryptography>=50,<51"

    requirements = logical_requirements()

    assert requirements
    assert any(block.startswith("cryptography==50.0.0") for block in requirements)
    assert all("--hash=sha256:" in block for block in requirements)
