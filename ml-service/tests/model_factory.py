"""Builds a tiny SYNTHETIC crop model artifact in the format the training pipeline writes.

Labelled ``SYNTHETIC_TEST_DATA``: generated numbers, not agronomic data. Used only by tests so
the API suite does not depend on the real dataset or trained model.
"""

import hashlib
import json
from pathlib import Path

import joblib
import numpy as np
import sklearn
from sklearn.ensemble import RandomForestClassifier

from app.crop_recommendation.schemas import FEATURES

SEED = 7

# Class centres per feature, roughly echoing the fixture crops (rice/jute/maize/chickpea).
_CENTRES = {
    "rice": [80, 45, 40, 23, 82, 6.4, 230],
    "jute": [78, 46, 40, 25, 80, 6.7, 175],
    "maize": [78, 48, 20, 22, 65, 6.2, 85],
    "chickpea": [40, 68, 80, 19, 17, 7.3, 80],
}
_SPREAD = [8, 6, 5, 1.5, 3, 0.3, 15]


def build_synthetic_artifact(root: Path, version: str = "1.0.0", rows_per_class: int = 60) -> Path:
    rng = np.random.default_rng(SEED)
    classes = sorted(_CENTRES)
    xs, ys = [], []
    for crop in classes:
        xs.append(rng.normal(_CENTRES[crop], _SPREAD, size=(rows_per_class, len(FEATURES))))
        ys += [crop] * rows_per_class
    x = np.clip(np.vstack(xs), 0, None)
    y = np.array(ys)

    model = RandomForestClassifier(n_estimators=25, random_state=SEED).fit(x, y)

    artifact_dir = root / version
    artifact_dir.mkdir(parents=True, exist_ok=True)
    model_path = artifact_dir / "model.joblib"
    joblib.dump(model, model_path)
    model_sha256 = hashlib.sha256(model_path.read_bytes()).hexdigest()

    metadata = {
        "name": "crop-recommendation-test",
        "version": version,
        "data_label": "SYNTHETIC_TEST_DATA",
        "features": list(FEATURES),
        "classes": [str(c) for c in model.classes_],
        "feature_ranges": {
            f: {"min": float(x[:, i].min()), "max": float(x[:, i].max())} for i, f in enumerate(FEATURES)
        },
        "class_feature_percentiles": {
            crop: {
                f: {
                    "p05": float(np.percentile(x[y == crop, i], 5)),
                    "p95": float(np.percentile(x[y == crop, i], 95)),
                }
                for i, f in enumerate(FEATURES)
            }
            for crop in classes
        },
        "metrics": {"macro_f1": 1.0, "accuracy": 1.0, "cv_macro_f1_mean": 1.0, "cv_macro_f1_std": 0.0},
        "trained_at": "2026-11-17T00:00:00Z",
        "dataset_sha256": hashlib.sha256(x.tobytes()).hexdigest(),
        "model_sha256": model_sha256,
        "library_versions": {"sklearn": sklearn.__version__, "xgboost": "n/a", "numpy": np.__version__},
        "random_seed": SEED,
    }
    (artifact_dir / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    return artifact_dir
