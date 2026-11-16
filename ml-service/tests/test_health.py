from fastapi.testclient import TestClient

from app.core.config import AppEnvironment, Settings
from app.main import create_app


def test_health_returns_ok_with_service_metadata(client: TestClient):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/json")
    assert response.json() == {
        "status": "ok",
        "service": "agrigenius-ml-service-test",
        "version": "9.9.9-test",
        "environment": "test",
    }


def test_health_rejects_unsupported_method(client: TestClient):
    assert client.post("/health").status_code == 405


def test_unknown_route_returns_404(client: TestClient):
    assert client.get("/does-not-exist").status_code == 404


def test_docs_available_outside_production(client: TestClient):
    assert client.get("/openapi.json").status_code == 200
    assert client.get("/docs").status_code == 200


def test_docs_disabled_in_production_by_default():
    settings = Settings(_env_file=None, app_env=AppEnvironment.PRODUCTION)
    with TestClient(create_app(settings)) as client:
        assert client.get("/health").status_code == 200
        assert client.get("/docs").status_code == 404
        assert client.get("/redoc").status_code == 404
        assert client.get("/openapi.json").status_code == 404
