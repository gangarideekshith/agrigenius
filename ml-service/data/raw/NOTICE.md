# Crop_recommendation.csv: provenance and licence

| | |
|---|---|
| Dataset | Crop Recommendation Dataset |
| Author | Atharva Ingle |
| Licence | Apache License 2.0 (as declared on the Kaggle dataset page) |
| Canonical source | https://www.kaggle.com/datasets/atharvaingle/crop-recommendation-dataset (last updated 2020-12-19) |
| File obtained from | https://raw.githubusercontent.com/Gladiator07/Harvestify/master/Data-processed/crop_recommendation.csv (the same author's GitHub repository, whose code is GPL-3.0; the dataset licence above is the author's Kaggle grant). Downloaded 2026-11-17 because a Kaggle download was not available. Not byte-verified against the Kaggle file. |
| SHA-256 | `808e1d84a544647f1ab2fdae04f434b1842692d6be09eee18a5c2d43eb78d77c` |
| Shape | 2,200 rows × 8 columns (`N, P, K, temperature, humidity, ph, rainfall, label`); 22 crops × 100 rows; no missing values, no duplicate rows |

Limitations: collection method, region, units (N/P/K are commonly described as soil content ratios) and the
original sources are not documented by the author. The perfectly balanced classes suggest curated or augmented
data. Use only as a **demo** dataset (`DEMO_PUBLIC_DATASET`); not validated for field advice (see ADR-0005 and
docs/ml/MODEL_CARDS.md).

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
