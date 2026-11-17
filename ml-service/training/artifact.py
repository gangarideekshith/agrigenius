"""Artifact helpers shared by training and (when unpickling) the ML service.

Keep this module small, dependency-light and backwards compatible: a ``model.joblib``
file may contain an :class:`EncodedLabelClassifier` and is unpickled by reference to
``training.artifact.EncodedLabelClassifier``.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import numpy as np
from sklearn.base import BaseEstimator, ClassifierMixin, clone
from sklearn.preprocessing import LabelEncoder

FEATURES: tuple[str, ...] = (
    "nitrogen",
    "phosphorus",
    "potassium",
    "temperature_c",
    "humidity_pct",
    "ph",
    "rainfall_mm",
)

MODEL_FILE = "model.joblib"
METADATA_FILE = "metadata.json"


def sha256_file(path: Path) -> str:
    """Hex SHA-256 of a file's bytes."""
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class EncodedLabelClassifier(ClassifierMixin, BaseEstimator):
    """Wraps a classifier that needs integer labels (e.g. XGBoost) so it accepts and
    returns string labels: ``classes_`` are the original strings (sorted) and
    ``predict``/``predict_proba`` follow that order.

    Only used when XGBoost wins model selection. Random Forest artifacts do not need it.
    """

    def __init__(self, estimator=None):
        self.estimator = estimator

    def fit(self, X, y, **fit_params):
        self.label_encoder_ = LabelEncoder()
        encoded = self.label_encoder_.fit_transform(np.asarray(y))
        self.classes_ = self.label_encoder_.classes_
        self.estimator_ = clone(self.estimator)
        self.estimator_.fit(X, encoded, **fit_params)
        return self

    def predict_proba(self, X):
        return self.estimator_.predict_proba(X)

    def predict(self, X):
        return self.label_encoder_.inverse_transform(np.argmax(self.predict_proba(X), axis=1))
