"""Loads the crop recommendation model artifact safely.

joblib files can execute code when loaded, so the artifact's SHA-256 is checked against
``metadata.json`` *before* deserialising, and the bytes that were hashed are the bytes loaded.
Any problem leaves the model unloaded (the API then answers 503) instead of crashing startup.
"""

import hashlib
import hmac
import io
import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib

from app.crop_recommendation.schemas import FEATURES, ModelMetadata

log = logging.getLogger(__name__)

MODEL_FILE = "model.joblib"
METADATA_FILE = "metadata.json"


@dataclass(frozen=True)
class LoadedModel:
    estimator: Any
    metadata: ModelMetadata
    sha256: str


def load_model(model_dir: Path, version: str) -> LoadedModel | None:
    artifact_dir = model_dir / version
    try:
        metadata = ModelMetadata.model_validate(
            json.loads((artifact_dir / METADATA_FILE).read_text(encoding="utf-8"))
        )
        if metadata.version != version:
            log.warning("Crop model not loaded: metadata version does not match the configured version")
            return None
        if tuple(metadata.features) != FEATURES:
            log.warning("Crop model not loaded: unexpected feature list in metadata")
            return None

        data = (artifact_dir / MODEL_FILE).read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if not hmac.compare_digest(digest, metadata.model_sha256):
            log.warning("Crop model not loaded: model.joblib checksum does not match metadata (possible tampering)")
            return None

        estimator = joblib.load(io.BytesIO(data))
        if not hasattr(estimator, "predict_proba") or not hasattr(estimator, "classes_"):
            log.warning("Crop model not loaded: artifact is not a probabilistic classifier")
            return None
        if [str(c) for c in estimator.classes_] != list(metadata.classes):
            log.warning("Crop model not loaded: model classes do not match metadata")
            return None
    except FileNotFoundError:
        log.warning("Crop model not loaded: artifact not found for version %s", version)
        return None
    except Exception as exc:  # noqa: BLE001 - never crash startup on a bad artifact
        log.warning("Crop model not loaded: %s", type(exc).__name__)
        return None

    log.info("Crop model loaded name=%s version=%s sha256=%s", metadata.name, metadata.version, digest[:12])
    return LoadedModel(estimator=estimator, metadata=metadata, sha256=digest)
