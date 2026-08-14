import argparse
import json
import os
from datetime import datetime
from pathlib import Path
from typing import Any

from langou_backend.release_signing import verify_manifest
from langou_backend.release_tooling import (
    build_signed_artifact_manifest,
    generate_release_keypair,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Offline Langou release manifest tool")
    commands = parser.add_subparsers(dest="command", required=True)

    keygen = commands.add_parser("keygen")
    keygen.add_argument("--private-key-file", type=Path, required=True)
    keygen.add_argument("--public-key-file", type=Path, required=True)

    sign = commands.add_parser("sign")
    sign.add_argument("--platform", choices=("android", "windows"), required=True)
    sign.add_argument("--artifact", type=Path, required=True)
    sign.add_argument("--url", required=True)
    sign.add_argument("--private-key-file", type=Path, required=True)
    sign.add_argument("--output", type=Path, required=True)
    sign.add_argument("--published-at", type=datetime.fromisoformat)
    sign.add_argument("--version", default="1.0.0")
    sign.add_argument("--minimum-supported-version", default="1.0.0")
    sign.add_argument("--mandatory", action="store_true")

    verify = commands.add_parser("verify")
    verify.add_argument("--manifest", type=Path, required=True)
    verify.add_argument("--public-key-file", type=Path, required=True)

    arguments = parser.parse_args()
    if arguments.command == "keygen":
        generate_release_keypair(
            arguments.private_key_file,
            arguments.public_key_file,
        )
        return
    if arguments.command == "sign":
        manifest = build_signed_artifact_manifest(
            platform=arguments.platform,
            artifact=arguments.artifact,
            url=arguments.url,
            private_key_file=arguments.private_key_file,
            published_at=arguments.published_at,
            version=arguments.version,
            minimum_supported_version=arguments.minimum_supported_version,
            mandatory=arguments.mandatory,
        )
        _write_json_exclusive(arguments.output, manifest)
        return

    manifest = json.loads(arguments.manifest.read_text(encoding="utf-8"))
    public_key = arguments.public_key_file.read_text(encoding="ascii").strip()
    verify_manifest(manifest, public_key)


def _write_json_exclusive(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o644)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            json.dump(value, output, ensure_ascii=False, indent=2, sort_keys=True)
            output.write("\n")
            output.flush()
            os.fsync(output.fileno())
    except Exception:
        path.unlink(missing_ok=True)
        raise


if __name__ == "__main__":
    main()
