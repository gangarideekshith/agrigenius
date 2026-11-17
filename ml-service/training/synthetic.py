"""SYNTHETIC crop data for tests and pipeline development only.

The values are invented Gaussian clusters with plausible ranges. They are NOT agronomic
facts and must never be used to train a model that farmers see. Artifacts trained on this
data must carry ``data_label = "SYNTHETIC_TEST_DATA"``.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

CSV_COLUMNS = ["N", "P", "K", "temperature", "humidity", "ph", "rainfall", "label"]

# Invented cluster centres: (N, P, K, temperature °C, humidity %, pH, rainfall mm).
_CENTRES: dict[str, tuple[float, ...]] = {
    "rice": (80, 48, 40, 23.5, 82, 6.4, 236),
    "maize": (78, 48, 20, 22.4, 65, 6.2, 85),
    "chickpea": (40, 68, 80, 18.9, 17, 7.3, 80),
    "cotton": (118, 46, 20, 24.0, 80, 6.9, 80),
    "banana": (100, 82, 50, 27.4, 80, 6.0, 105),
    "coffee": (101, 29, 30, 25.5, 58, 6.8, 158),
    "jute": (78, 47, 40, 25.0, 80, 6.7, 175),
    "mango": (20, 27, 30, 31.2, 50, 5.8, 95),
}
_SPREAD = (6.0, 5.0, 4.0, 1.2, 3.0, 0.25, 12.0)
_LOWER = (0, 0, 0, -5, 0, 3.0, 0)
_UPPER = (300, 300, 300, 55, 100, 10.0, 5000)

DEFAULT_CLASSES = ("rice", "maize", "chickpea", "cotton", "banana", "coffee")


def generate(n_per_class: int = 60, classes: tuple[str, ...] = DEFAULT_CLASSES, seed: int = 0) -> pd.DataFrame:
    """Return a SYNTHETIC dataset in the public Crop Recommendation CSV layout."""
    unknown = [c for c in classes if c not in _CENTRES]
    if unknown:
        raise ValueError(f"No synthetic centre for classes: {unknown}")
    rng = np.random.default_rng(seed)
    frames = []
    for crop in classes:
        values = rng.normal(loc=_CENTRES[crop], scale=_SPREAD, size=(n_per_class, len(_SPREAD)))
        values = np.clip(values, _LOWER, _UPPER).round(2)
        frame = pd.DataFrame(values, columns=CSV_COLUMNS[:-1])
        frame["label"] = crop
        frames.append(frame)
    return pd.concat(frames, ignore_index=True)
