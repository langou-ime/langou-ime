from pathlib import Path

import orjson

from langou_backend.main import create_app


def main() -> None:
    destination = Path(__file__).parents[1] / "contracts" / "openapi-v1.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(
        orjson.dumps(
            create_app(environment="development").openapi(),
            option=orjson.OPT_INDENT_2 | orjson.OPT_SORT_KEYS,
        )
        + b"\n"
    )


if __name__ == "__main__":
    main()
