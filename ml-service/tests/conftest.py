import os
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.core.config import AppEnvironment, Settings, get_settings
from app.main import create_app
from tests.model_factory import build_synthetic_artifact

# Test-only key (>= 32 chars). Never used outside tests.
TEST_API_KEY = "test-only-internal-api-key-0123456789abcdef"
API_KEY_HEADER = {"X-Internal-Api-Key": TEST_API_KEY}


@pytest.fixture(autouse=True)
def isolated_environment(monkeypatch: pytest.MonkeyPatch, tmp_path_factory: pytest.TempPathFactory):
    """Keep tests independent of the developer's shell, local .env file and real model artifacts."""
    for key in list(os.environ):
        if key.upper().startswith("ML_"):
            monkeypatch.delenv(key, raising=False)
    monkeypatch.setenv("ML_INTERNAL_API_KEY", TEST_API_KEY)
    # Point at an empty directory so no real model is loaded unless a test builds one.
    monkeypatch.setenv("ML_MODEL_DIR", str(tmp_path_factory.mktemp("no-models")))
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


@pytest.fixture(scope="session")
def synthetic_model_root(tmp_path_factory: pytest.TempPathFactory) -> Path:
    """A small, clearly synthetic crop model artifact (version 1.0.0) shared by API tests."""
    root = tmp_path_factory.mktemp("models")
    build_synthetic_artifact(root, version="1.0.0")
    return root


@pytest.fixture
def model_settings(synthetic_model_root: Path) -> Settings:
    return Settings(
        _env_file=None,
        app_env=AppEnvironment.TEST,
        model_dir=synthetic_model_root,
        crop_model_version="1.0.0",
    )


@pytest.fixture
def model_client(model_settings: Settings):
    with TestClient(create_app(model_settings)) as test_client:
        yield test_client
