from importlib import import_module

import pytest
from fastapi.testclient import TestClient


def load_app():
    try:
        module = import_module("langou_backend.main")
    except ModuleNotFoundError:
        pytest.fail("langou_backend.main has not been implemented")
    return module.create_app(environment="test")


def test_health_reports_service_without_exposing_docs() -> None:
    app = load_app()
    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "service": "langou-api",
        "status": "ok",
        "version": "1.0.0",
    }
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404

