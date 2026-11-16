import os

import pytest
from fastapi.testclient import TestClient

from app.core.config import AppEnvironment, Settings, get_settings
from app.main import create_app


@pytest.fixture(autouse=True)
def isolated_environment(monkeypatch: pytest.MonkeyPatch):
    """Keep tests independent of the developer's shell and local .env file."""
    for key in list(os.environ):
        if key.upper().startswith("ML_"):
            monkeypatch.delenv(key, raising=False)
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


@pytest.fixture
def test_settings() -> Settings:
    return Settings(
        _env_file=None,
        app_env=AppEnvironment.TEST,
        service_name="agrigenius-ml-service-test",
        service_version="9.9.9-test",
    )


@pytest.fixture
def client(test_settings: Settings):
    with TestClient(create_app(test_settings)) as test_client:
        yield test_client
