"""Training pipeline tests. All data here is SYNTHETIC (training.synthetic)."""

import json

import joblib
import numpy as np
import pandas as pd
import pytest

from training import synthetic
from training.artifact import FEATURES, METADATA_FILE, MODEL_FILE, sha256_file
from training.train_crop_model import TrainingError, load_dataset, main, split, train

FAST = {"n_estimators": 25, "xgb_n_estimators": 25}


@pytest.fixture
def synthetic_csv(tmp_path):
    path = tmp_path / "synthetic.csv"
    synthetic.generate(n_per_class=60, seed=1).to_csv(path, index=False)
    return path


def _train(csv, out, version="1.0.0", seed=42, min_f1=0.80, **kwargs):
    return train(csv, out, version, "SYNTHETIC_TEST_DATA", seed, min_f1, **FAST, **kwargs)


def test_end_to_end_writes_complete_artifact(synthetic_csv, tmp_path):
    result = _train(synthetic_csv, tmp_path / "models")

    artifact = tmp_path / "models" / "1.0.0"
    assert result.artifact_dir == artifact
    assert (artifact / MODEL_FILE).is_file()
    assert (artifact / METADATA_FILE).is_file()
    assert not [p for p in (tmp_path / "models").iterdir() if p.name.startswith(".staging")]

    meta = json.loads((artifact / METADATA_FILE).read_text(encoding="utf-8"))
    expected_keys = {
        "name", "version", "data_label", "features", "classes", "feature_ranges",
        "class_feature_percentiles", "metrics", "candidates", "trained_at", "dataset_sha256",
        "model_sha256", "library_versions", "random_seed", "n_train", "n_test",
    }
    assert set(meta) == expected_keys
    assert meta["name"] in {"crop-recommendation-rf", "crop-recommendation-xgb"}
    assert meta["version"] == "1.0.0"
    assert meta["data_label"] == "SYNTHETIC_TEST_DATA"
    assert meta["features"] == list(FEATURES)
    assert meta["classes"] == sorted(synthetic.DEFAULT_CLASSES)
    assert set(meta["feature_ranges"]) == set(FEATURES)
    assert all(r["min"] <= r["max"] for r in meta["feature_ranges"].values())
    assert set(meta["class_feature_percentiles"]) == set(meta["classes"])
    for per_feature in meta["class_feature_percentiles"].values():
        assert set(per_feature) == set(FEATURES)
        assert all(v["p05"] <= v["p95"] for v in per_feature.values())
    metrics = meta["metrics"]
    assert set(metrics) == {"macro_f1", "accuracy", "cv_macro_f1_mean", "cv_macro_f1_std",
                            "per_class_f1", "confusion_matrix"}
    assert 0.0 <= metrics["macro_f1"] <= 1.0
    assert set(metrics["per_class_f1"]) == set(meta["classes"])
    assert len(metrics["confusion_matrix"]) == len(meta["classes"])
    assert sum(map(sum, metrics["confusion_matrix"])) == meta["n_test"]
    assert set(meta["candidates"]) == {"rf", "xgb"}
    assert meta["trained_at"].endswith("Z")
    assert meta["model_sha256"] == sha256_file(artifact / MODEL_FILE)
    assert meta["dataset_sha256"] == sha256_file(synthetic_csv)
    assert set(meta["library_versions"]) == {"sklearn", "xgboost", "numpy", "pandas"}
    assert meta["random_seed"] == 42


def test_model_loads_and_predicts_string_labels_in_feature_order(synthetic_csv, tmp_path):
    _train(synthetic_csv, tmp_path / "models")
    artifact = tmp_path / "models" / "1.0.0"
    meta = json.loads((artifact / METADATA_FILE).read_text(encoding="utf-8"))
    model = joblib.load(artifact / MODEL_FILE)

    assert [str(c) for c in model.classes_] == meta["classes"]
    probe = np.array([[80, 48, 40, 23.5, 82, 6.4, 236]], dtype=float)  # synthetic "rice" centre
    proba = model.predict_proba(probe)
    assert proba.shape == (1, len(meta["classes"]))
    assert proba.sum() == pytest.approx(1.0)
    assert meta["classes"][int(np.argmax(proba))] == "rice"


def test_same_seed_is_reproducible(synthetic_csv, tmp_path):
    a = _train(synthetic_csv, tmp_path / "a")
    b = _train(synthetic_csv, tmp_path / "b")

    assert a.metadata["metrics"] == b.metadata["metrics"]
    assert a.metadata["candidates"] == b.metadata["candidates"]
    assert a.metadata["class_feature_percentiles"] == b.metadata["class_feature_percentiles"]

    probe = synthetic.generate(n_per_class=5, seed=99)[["N", "P", "K", "temperature", "humidity", "ph", "rainfall"]]
    probe = probe.to_numpy(dtype=float)
    model_a = joblib.load(a.artifact_dir / MODEL_FILE)
    model_b = joblib.load(b.artifact_dir / MODEL_FILE)
    np.testing.assert_array_equal(model_a.predict_proba(probe), model_b.predict_proba(probe))


def test_different_seed_runs(synthetic_csv, tmp_path):
    other = _train(synthetic_csv, tmp_path / "other", seed=7)
    assert other.metadata["random_seed"] == 7


def test_duplicates_removed_and_no_train_test_overlap(tmp_path):
    df = synthetic.generate(n_per_class=40, seed=3)
    with_dupes = pd.concat([df, df.iloc[:25]], ignore_index=True)
    csv = tmp_path / "dupes.csv"
    with_dupes.to_csv(csv, index=False)

    loaded = load_dataset(csv)
    assert len(loaded) == len(df)
    assert not loaded.duplicated().any()

    train_df, test_df = split(loaded, seed=42)
    train_rows = set(map(tuple, train_df.to_numpy()))
    test_rows = set(map(tuple, test_df.to_numpy()))
    assert train_rows.isdisjoint(test_rows)
    assert len(train_df) + len(test_df) == len(loaded)

    result = _train(csv, tmp_path / "models")
    assert result.metadata["n_train"] + result.metadata["n_test"] == len(df)


def test_quality_gate_failure_writes_nothing(synthetic_csv, tmp_path):
    out = tmp_path / "models"
    with pytest.raises(TrainingError, match="below the required minimum"):
        _train(synthetic_csv, out, min_f1=1.01)
    assert not (out / "1.0.0").exists()
    assert not out.exists() or not any(out.iterdir())


def test_cli_exit_codes(synthetic_csv, tmp_path, capsys):
    out = tmp_path / "cli"
    failing = main(["--csv", str(synthetic_csv), "--out", str(out), "--version", "9.9.9",
                    "--data-label", "SYNTHETIC_TEST_DATA", "--min-macro-f1", "1.01"])
    assert failing == 1
    assert "below the required minimum" in capsys.readouterr().err
    assert not (out / "9.9.9").exists()


def test_existing_version_is_not_overwritten_without_force(synthetic_csv, tmp_path):
    _train(synthetic_csv, tmp_path / "models")
    with pytest.raises(TrainingError, match="already exists"):
        _train(synthetic_csv, tmp_path / "models")
    _train(synthetic_csv, tmp_path / "models", force=True)


def test_missing_column_is_rejected(tmp_path):
    csv = tmp_path / "bad.csv"
    synthetic.generate(n_per_class=20).drop(columns=["rainfall"]).to_csv(csv, index=False)
    with pytest.raises(TrainingError, match="missing required columns.*rainfall"):
        load_dataset(csv)


def test_missing_values_are_rejected(tmp_path):
    df = synthetic.generate(n_per_class=20)
    df.loc[3, "ph"] = np.nan
    csv = tmp_path / "nan.csv"
    df.to_csv(csv, index=False)
    with pytest.raises(TrainingError, match="missing values.*ph"):
        load_dataset(csv)


def test_non_numeric_feature_is_rejected(tmp_path):
    df = synthetic.generate(n_per_class=20)
    df["N"] = df["N"].astype(str)
    df.loc[0, "N"] = "lots"
    csv = tmp_path / "text.csv"
    df.to_csv(csv, index=False)
    with pytest.raises(TrainingError, match="must be numeric"):
        load_dataset(csv)


def test_too_few_rows_per_class_is_rejected(tmp_path):
    csv = tmp_path / "small.csv"
    synthetic.generate(n_per_class=5).to_csv(csv, index=False)
    with pytest.raises(TrainingError, match="fewer than"):
        load_dataset(csv)


def test_synthetic_generator_is_deterministic_and_in_range():
    a = synthetic.generate(n_per_class=10, seed=5)
    b = synthetic.generate(n_per_class=10, seed=5)
    pd.testing.assert_frame_equal(a, b)
    assert list(a.columns) == synthetic.CSV_COLUMNS
    assert a["ph"].between(3.0, 10.0).all()
    assert a["humidity"].between(0, 100).all()
