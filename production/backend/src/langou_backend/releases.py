from pathlib import Path

import orjson

from langou_backend.schemas import ReleaseManifest


class ReleaseNotFound(Exception):
    pass


class FileReleaseRepository:
    def __init__(self, directory: Path | None) -> None:
        self._directory = directory

    def latest(self, platform: str) -> ReleaseManifest:
        if self._directory is None:
            raise ReleaseNotFound
        path = self._directory / f"{platform}.json"
        if not path.is_file():
            raise ReleaseNotFound
        return ReleaseManifest.model_validate(orjson.loads(path.read_bytes()))

