"""Model artifact loading: checksum verification happens before deserialisation."""

import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.core.config import AppEnvironment, Settings
from app.crop_recommendation import model_loader
from app.crop_recommendation.model_loader import load_model
from app.main import create_app
from tests.conftest import API_KEY_HEADER
from tests.model_factory import build_synthetic_artifact


@pytest.fixture
def artifact_root(tmp_path: Path) -> Path:
    build_synthetic_artifact(tmp_path, version="1.0.0")
    return tmp_path


def test_valid_artifact_loads(artifact_root: Path):
    loaded = load_model(artifact_root, "1.0.0")

    assert loaded is not None
    assert loaded.metadata.classes == ["chickpea", "jute", "maize", "rice"]
    assert len(loaded.sha256) == 64


def test_missing_directory_is_not_loaded(tmp_path: Path):
    assert load_model(tmp_path, "1.0.0") is None


def test_wrong_version_is_not_loaded(artifact_root: Path):
    assert load_model(artifact_root, "2.0.0") is None


def test_tampered_artifact_is_never_deserialised(artifact_root: Path, monkeypatch: pytest.MonkeyPatch):
    model_file = artifact_root / "1.0.0" / "model.joblib"
    model_file.write_bytes(model_file.read_bytes() + b"tampered")
    calls = []
    monkeypatch.setattr(model_loader.joblib, "load", lambda *a, **k: calls.append(a))

    assert load_model(artifact_root, "1.0.0") is None
    assert calls == [], "joblib.load must not run on an artifact whose checksum does not match"


def test_metadata_version_mismatch_is_not_loaded(artifact_root: Path):
    metadata_file = artifact_root / "1.0.0" / "metadata.json"
    metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
    metadata["version"] = "9.9.9"
    metadata_file.write_text(json.dumps(metadata), encoding="utf-8")

    assert load_model(artifact_root, "1.0.0") is None


def test_unexpected_feature_list_is_not_loaded(artifact_root: Path):
    metadata_file = artifact_root / "1.0.0" / "metadata.json"
    metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
    metadata["features"] = list(reversed(metadata["features"]))
    metadata_file.write_text(json.dumps(metadata), encoding="utf-8")

    assert load_model(artifact_root, "1.0.0") is None


def test_class_mismatch_is_not_loaded(artifact_root: Path):
    metadata_file = artifact_root / "1.0.0" / "metadata.json"
    metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
    metadata["classes"] = ["a", "b", "c", "d"]
    metadata_file.write_text(json.dumps(metadata), encoding="utf-8")

    assert load_model(artifact_root, "1.0.0") is None


def test_corrupt_metadata_is_not_loaded(artifact_root: Path):
    (artifact_root / "1.0.0" / "metadata.json").write_text("{not json", encoding="utf-8")

    assert load_model(artifact_root, "1.0.0") is None


def test_service_with_tampered_model_answers_503(artifact_root: Path):
    model_file = artifact_root / "1.0.0" / "model.joblib"
    model_file.write_bytes(model_file.read_bytes() + b"x")
    settings = Settings(_env_file=None, app_env=AppEnvironment.TEST, model_dir=artifact_root)

    with TestClient(create_app(settings)) as client:
        assert client.get("/health/ready").status_code == 503
        response = client.post(
            "/v1/crop-recommendation/predict",
            json={
                "request_id": "8b0c2f1e-4a5d-4c3b-9e7f-1a2b3c4d5e6f",
                "features": {
                    "nitrogen": 90, "phosphorus": 42, "potassium": 43, "temperature_c": 20.9,
                    "humidity_pct": 82, "ph": 6.5, "rainfall_mm": 203,
                },
            },
            headers=API_KEY_HEADER,
        )
        assert response.status_code == 503
        assert response.json()["code"] == "MODEL_NOT_LOADED"
