"""Train the crop-recommendation model and write a versioned, checksummed artifact.

Usage (from ml-service/):
    python -m training.train_crop_model --csv PATH --out models/crop_recommendation \
        --version 1.0.0 --data-label DEMO_PUBLIC_DATASET --seed 42 --min-macro-f1 0.90

Pipeline: validate CSV -> drop exact duplicates (leakage control) -> stratified 80/20 split ->
compare Random Forest and XGBoost by 5-fold stratified CV macro-F1 on the training split
(tie -> RF) -> calibrate the winner (sigmoid, 5-fold) -> evaluate on the holdout -> refuse to
write anything if holdout macro-F1 is below the threshold -> write model.joblib + metadata.json
atomically. No data rows are ever printed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
import xgboost
from sklearn.calibration import CalibratedClassifierCV
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score
from sklearn.model_selection import StratifiedKFold, cross_val_score, train_test_split
from xgboost import XGBClassifier

from training.artifact import FEATURES, METADATA_FILE, MODEL_FILE, EncodedLabelClassifier, sha256_file

CSV_TO_FEATURE = {
    "N": "nitrogen",
    "P": "phosphorus",
    "K": "potassium",
    "temperature": "temperature_c",
    "humidity": "humidity_pct",
    "ph": "ph",
    "rainfall": "rainfall_mm",
}
LABEL_COLUMN = "label"
MIN_ROWS_PER_CLASS = 10
CV_FOLDS = 5
TEST_SIZE = 0.2


class TrainingError(Exception):
    """Input or quality problem that must stop training without writing an artifact."""


@dataclass
class TrainingResult:
    artifact_dir: Path
    metadata: dict


def load_dataset(csv_path: Path) -> pd.DataFrame:
    """Read and validate the CSV; return features (renamed) + label, exact duplicates removed."""
    try:
        raw = pd.read_csv(csv_path)
    except (OSError, pd.errors.ParserError, pd.errors.EmptyDataError) as exc:
        raise TrainingError(f"Cannot read dataset CSV: {exc}") from exc

    required = [*CSV_TO_FEATURE, LABEL_COLUMN]
    missing = [c for c in required if c not in raw.columns]
    if missing:
        raise TrainingError(f"Dataset is missing required columns: {missing}")

    df = raw[required].rename(columns=CSV_TO_FEATURE)
    for feature in FEATURES:
        if not pd.api.types.is_numeric_dtype(df[feature]):
            raise TrainingError(f"Column for '{feature}' must be numeric")
    if df.isna().any().any():
        bad = [c for c in df.columns if df[c].isna().any()]
        raise TrainingError(f"Dataset has missing values in columns: {bad}")

    df[LABEL_COLUMN] = df[LABEL_COLUMN].astype(str).str.strip()
    if (df[LABEL_COLUMN] == "").any():
        raise TrainingError("Dataset has empty labels")

    df = df.drop_duplicates().reset_index(drop=True)

    counts = df[LABEL_COLUMN].value_counts()
    if len(counts) < 2:
        raise TrainingError("Dataset needs at least two crop classes")
    too_small = sorted(counts[counts < MIN_ROWS_PER_CLASS].index)
    if too_small:
        raise TrainingError(f"Classes with fewer than {MIN_ROWS_PER_CLASS} unique rows: {too_small}")
    return df


def split(df: pd.DataFrame, seed: int) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Stratified 80/20 split (deterministic for a given seed)."""
    train, test = train_test_split(df, test_size=TEST_SIZE, stratify=df[LABEL_COLUMN], random_state=seed)
    return train.reset_index(drop=True), test.reset_index(drop=True)


def _candidates(seed: int, n_estimators: int, xgb_n_estimators: int) -> dict:
    return {
        "rf": RandomForestClassifier(n_estimators=n_estimators, random_state=seed, n_jobs=1),
        "xgb": EncodedLabelClassifier(
            XGBClassifier(
                n_estimators=xgb_n_estimators,
                max_depth=6,
                learning_rate=0.1,
                tree_method="hist",
                eval_metric="mlogloss",
                random_state=seed,
                n_jobs=1,
            )
        ),
    }


def train(
    csv_path: Path,
    out_dir: Path,
    version: str,
    data_label: str,
    seed: int = 42,
    min_macro_f1: float = 0.90,
    *,
    n_estimators: int = 300,
    xgb_n_estimators: int = 200,
    force: bool = False,
) -> TrainingResult:
    csv_path = Path(csv_path)
    out_dir = Path(out_dir)
    target = out_dir / version
    if target.exists() and not force:
        raise TrainingError(f"Artifact directory already exists: {target} (use --force to replace)")

    df = load_dataset(csv_path)
    train_df, test_df = split(df, seed)
    x_train = train_df[list(FEATURES)].to_numpy(dtype=float)
    y_train = train_df[LABEL_COLUMN].to_numpy()
    x_test = test_df[list(FEATURES)].to_numpy(dtype=float)
    y_test = test_df[LABEL_COLUMN].to_numpy()

    cv = StratifiedKFold(n_splits=CV_FOLDS, shuffle=True, random_state=seed)
    candidates = _candidates(seed, n_estimators, xgb_n_estimators)
    cv_scores = {
        name: cross_val_score(estimator, x_train, y_train, cv=cv, scoring="f1_macro")
        for name, estimator in candidates.items()
    }
    winner = "rf" if cv_scores["rf"].mean() >= cv_scores["xgb"].mean() else "xgb"

    model = CalibratedClassifierCV(candidates[winner], method="sigmoid", cv=CV_FOLDS)
    model.fit(x_train, y_train)

    classes = [str(c) for c in model.classes_]
    predicted = model.predict(x_test)
    macro_f1 = float(f1_score(y_test, predicted, average="macro"))
    if macro_f1 < min_macro_f1:
        raise TrainingError(
            f"Holdout macro-F1 {macro_f1:.4f} is below the required minimum {min_macro_f1:.4f}; no artifact written"
        )

    per_class = f1_score(y_test, predicted, average=None, labels=classes)
    metadata = {
        "name": f"crop-recommendation-{winner}",
        "version": version,
        "data_label": data_label,
        "features": list(FEATURES),
        "classes": classes,
        "feature_ranges": {
            f: {"min": float(train_df[f].min()), "max": float(train_df[f].max())} for f in FEATURES
        },
        "class_feature_percentiles": {
            crop: {
                f: {
                    "p05": float(np.percentile(group[f], 5)),
                    "p95": float(np.percentile(group[f], 95)),
                }
                for f in FEATURES
            }
            for crop, group in train_df.groupby(LABEL_COLUMN)
        },
        "metrics": {
            "macro_f1": macro_f1,
            "accuracy": float(accuracy_score(y_test, predicted)),
            "cv_macro_f1_mean": float(cv_scores[winner].mean()),
            "cv_macro_f1_std": float(cv_scores[winner].std()),
            "per_class_f1": {crop: float(score) for crop, score in zip(classes, per_class)},
            "confusion_matrix": confusion_matrix(y_test, predicted, labels=classes).tolist(),
        },
        "candidates": {name: {"cv_macro_f1_mean": float(s.mean())} for name, s in cv_scores.items()},
        "trained_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "dataset_sha256": hashlib.sha256(csv_path.read_bytes()).hexdigest(),
        "model_sha256": None,
        "library_versions": {
            "sklearn": sklearn.__version__,
            "xgboost": xgboost.__version__,
            "numpy": np.__version__,
            "pandas": pd.__version__,
        },
        "random_seed": seed,
        "n_train": int(len(train_df)),
        "n_test": int(len(test_df)),
    }

    _write_atomically(model, metadata, out_dir, target, force)
    return TrainingResult(artifact_dir=target, metadata=metadata)


def _write_atomically(model, metadata: dict, out_dir: Path, target: Path, force: bool) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=f".staging-{target.name}-", dir=out_dir))
    try:
        model_path = staging / MODEL_FILE
        joblib.dump(model, model_path)
        metadata["model_sha256"] = sha256_file(model_path)
        (staging / METADATA_FILE).write_text(json.dumps(metadata, indent=2, sort_keys=False), encoding="utf-8")
        if target.exists() and force:
            shutil.rmtree(target)
        staging.replace(target)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Train the AgriGenius crop-recommendation model.")
    parser.add_argument("--csv", required=True, type=Path, help="Dataset CSV (N,P,K,temperature,humidity,ph,rainfall,label)")
    parser.add_argument("--out", required=True, type=Path, help="Artifact root, e.g. models/crop_recommendation")
    parser.add_argument("--version", required=True, help="Model version, e.g. 1.0.0")
    parser.add_argument("--data-label", required=True, choices=["DEMO_PUBLIC_DATASET", "SYNTHETIC_TEST_DATA"])
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--min-macro-f1", type=float, default=0.90)
    parser.add_argument("--force", action="store_true", help="Replace an existing artifact version")
    args = parser.parse_args(argv)

    try:
        result = train(args.csv, args.out, args.version, args.data_label, args.seed, args.min_macro_f1,
                       force=args.force)
    except TrainingError as exc:
        print(f"Training failed: {exc}", file=sys.stderr)
        return 1

    m = result.metadata
    print(f"Wrote {result.artifact_dir}")
    print(f"  model={m['name']} version={m['version']} data_label={m['data_label']} classes={len(m['classes'])}")
    print(f"  holdout macro_f1={m['metrics']['macro_f1']:.4f} accuracy={m['metrics']['accuracy']:.4f} "
          f"cv_macro_f1={m['metrics']['cv_macro_f1_mean']:.4f} (std {m['metrics']['cv_macro_f1_std']:.4f})")
    print(f"  candidates={ {k: round(v['cv_macro_f1_mean'], 4) for k, v in m['candidates'].items()} }")
    print(f"  model_sha256={m['model_sha256']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
