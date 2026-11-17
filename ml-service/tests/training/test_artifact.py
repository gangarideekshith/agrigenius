"""The XGBoost label wrapper must survive calibration and a joblib round trip (SYNTHETIC data)."""

import joblib
import numpy as np
from sklearn.calibration import CalibratedClassifierCV
from xgboost import XGBClassifier

from training import synthetic
from training.artifact import EncodedLabelClassifier, sha256_file


def test_encoded_label_classifier_calibrated_round_trip(tmp_path):
    df = synthetic.generate(n_per_class=40, seed=2)
    x = df[["N", "P", "K", "temperature", "humidity", "ph", "rainfall"]].to_numpy(dtype=float)
    y = df["label"].to_numpy()

    model = CalibratedClassifierCV(
        EncodedLabelClassifier(XGBClassifier(n_estimators=20, random_state=0, n_jobs=1)),
        method="sigmoid",
        cv=5,
    ).fit(x, y)
    path = tmp_path / "model.joblib"
    joblib.dump(model, path)
    loaded = joblib.load(path)

    assert [str(c) for c in loaded.classes_] == sorted(set(y))
    proba = loaded.predict_proba(x[:3])
    assert proba.shape == (3, len(set(y)))
    np.testing.assert_allclose(proba.sum(axis=1), 1.0)
    assert set(loaded.predict(x[:10])) <= set(y)
    assert len(sha256_file(path)) == 64
