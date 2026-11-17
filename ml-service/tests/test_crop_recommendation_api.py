"""Private crop recommendation API: auth, validation, contract shape and predictions."""

import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from tests.conftest import API_KEY_HEADER

CONTRACTS = Path(__file__).resolve().parents[2] / "docs" / "api" / "contracts" / "crop-recommendation"
PREDICT = "/v1/crop-recommendation/predict"
MODEL = "/v1/crop-recommendation/model"


def fixture(name: str) -> dict:
    return json.loads((CONTRACTS / name).read_text(encoding="utf-8"))


def valid_request(**overrides) -> dict:
    body = fixture("predict-request.json")
    body.update(overrides)
    return body


def _kind(value) -> str:
    if isinstance(value, bool):
        return "bool"
    if isinstance(value, (int, float)):
        return "number"
    if value is None:
        return "null"
    return type(value).__name__


def assert_same_shape(actual, expected, path="$"):
    """Same keys and JSON types as the contract fixture (lists compared against their first item)."""
    if isinstance(expected, dict):
        assert isinstance(actual, dict), f"{path}: expected object"
        assert set(actual) == set(expected), f"{path}: keys {sorted(actual)} != {sorted(expected)}"
        for key in expected:
            assert_same_shape(actual[key], expected[key], f"{path}.{key}")
    elif isinstance(expected, list):
        assert isinstance(actual, list), f"{path}: expected array"
        if expected and actual:
            for i, item in enumerate(actual):
                assert_same_shape(item, expected[0], f"{path}[{i}]")
    else:
        assert _kind(actual) == _kind(expected), f"{path}: {_kind(actual)} != {_kind(expected)}"


# --- authentication -------------------------------------------------------------------------


@pytest.mark.parametrize("headers", [{}, {"X-Internal-Api-Key": "wrong-key"}, {"X-Internal-Api-Key": ""}])
def test_predict_requires_valid_api_key(model_client: TestClient, headers: dict):
    response = model_client.post(PREDICT, json=valid_request(), headers=headers)

    assert response.status_code == 401
    assert response.json() == fixture("error-unauthorized.json")


def test_model_info_requires_api_key(model_client: TestClient):
    response = model_client.get(MODEL)

    assert response.status_code == 401
    assert response.json() == fixture("error-unauthorized.json")


def test_auth_is_checked_before_body_validation(model_client: TestClient):
    response = model_client.post(PREDICT, json={"bogus": True})

    assert response.status_code == 401


def test_health_endpoints_stay_open(model_client: TestClient):
    assert model_client.get("/health").status_code == 200
    ready = model_client.get("/health/ready")
    assert ready.status_code == 200
    assert ready.json() == {"status": "ready", "model_loaded": True, "model_version": "1.0.0"}


# --- validation -----------------------------------------------------------------------------


def _with_feature(name: str, value) -> dict:
    body = valid_request()
    body["features"] = {**body["features"], name: value}
    return body


@pytest.mark.parametrize(
    ("body", "field"),
    [
        (_with_feature("ph", 15), "features.ph"),
        (_with_feature("humidity_pct", -1), "features.humidity_pct"),
        (_with_feature("unexpected", 1), "features.unexpected"),
        (valid_request(top_k=0), "top_k"),
        (valid_request(top_k=11), "top_k"),
        (valid_request(request_id="not-a-uuid"), "request_id"),
        (valid_request(extra_field=True), "extra_field"),
    ],
)
def test_invalid_requests_return_contract_422(model_client: TestClient, body: dict, field: str):
    response = model_client.post(PREDICT, json=body, headers=API_KEY_HEADER)

    assert response.status_code == 422
    payload = response.json()
    assert_same_shape(payload, fixture("error-validation.json"))
    assert payload["code"] == "INVALID_REQUEST"
    assert field in [error["field"] for error in payload["errors"]]


def test_missing_feature_returns_422(model_client: TestClient):
    body = valid_request()
    del body["features"]["rainfall_mm"]

    response = model_client.post(PREDICT, json=body, headers=API_KEY_HEADER)

    assert response.status_code == 422
    assert "features.rainfall_mm" in [e["field"] for e in response.json()["errors"]]


def test_validation_errors_do_not_echo_input_or_stack_traces(model_client: TestClient):
    response = model_client.post(PREDICT, json=_with_feature("ph", 987654.321), headers=API_KEY_HEADER)

    text = response.text
    assert response.status_code == 422
    assert "987654" not in text
    assert "Traceback" not in text


def test_malformed_json_returns_422(model_client: TestClient):
    response = model_client.post(
        PREDICT, content=b"{not json", headers={**API_KEY_HEADER, "Content-Type": "application/json"}
    )

    assert response.status_code == 422
    assert response.json()["code"] == "INVALID_REQUEST"


# --- predictions ----------------------------------------------------------------------------


def test_request_fixture_is_accepted_and_response_matches_contract(model_client: TestClient):
    response = model_client.post(PREDICT, json=fixture("predict-request.json"), headers=API_KEY_HEADER)

    assert response.status_code == 200
    body = response.json()
    assert_same_shape(body, fixture("predict-response.json"))
    assert body["model"]["data_label"] == "SYNTHETIC_TEST_DATA"
    assert body["predictions"][0]["crop"] == "rice"


def test_predictions_are_top_k_sorted_probabilities(model_client: TestClient):
    response = model_client.post(PREDICT, json=valid_request(top_k=3), headers=API_KEY_HEADER)

    predictions = response.json()["predictions"]
    probabilities = [p["probability"] for p in predictions]
    assert len(predictions) == 3
    assert probabilities == sorted(probabilities, reverse=True)
    assert all(0.0 <= p <= 1.0 for p in probabilities)
    assert sum(probabilities) <= 1.0 + 1e-9
    assert len({p["crop"] for p in predictions}) == 3


def test_top_k_larger_than_class_count_returns_all_classes(model_client: TestClient):
    response = model_client.post(PREDICT, json=valid_request(top_k=10), headers=API_KEY_HEADER)

    predictions = response.json()["predictions"]
    assert len(predictions) == 4  # synthetic model has 4 classes
    assert sum(p["probability"] for p in predictions) <= 1.0 + 1e-9


def test_explanations_are_bounded_and_data_derived(model_client: TestClient):
    response = model_client.post(PREDICT, json=valid_request(), headers=API_KEY_HEADER)

    for prediction in response.json()["predictions"]:
        assert 1 <= len(prediction["explanation"]) <= 7
        for item in prediction["explanation"]:
            assert item["p05"] <= item["p95"]


def test_in_range_input_has_no_warnings(model_client: TestClient):
    response = model_client.post(PREDICT, json=valid_request(), headers=API_KEY_HEADER)

    assert response.json()["warnings"] == []


def test_out_of_training_range_input_is_flagged(model_client: TestClient):
    response = model_client.post(PREDICT, json=_with_feature("rainfall_mm", 1200), headers=API_KEY_HEADER)

    body = response.json()
    assert response.status_code == 200
    assert {"code": "INPUT_OUTSIDE_TRAINING_RANGE", "feature": "rainfall_mm"} in body["warnings"]
    assert_same_shape(body, fixture("predict-response-low-confidence.json"))


def test_model_info_matches_contract(model_client: TestClient):
    response = model_client.get(MODEL, headers=API_KEY_HEADER)

    assert response.status_code == 200
    body = response.json()
    expected = fixture("model-info-response.json")
    assert set(body) == set(expected)
    assert body["features"] == expected["features"]
    assert body["classes"] == ["chickpea", "jute", "maize", "rice"]
    assert_same_shape(body["metrics"], expected["metrics"])
    assert len(body["sha256"]) == 64


# --- model not available --------------------------------------------------------------------


def test_predict_returns_503_when_no_model(client: TestClient):
    response = client.post(PREDICT, json=valid_request(), headers=API_KEY_HEADER)

    assert response.status_code == 503
    assert response.json() == fixture("error-model-not-loaded.json")


def test_model_info_returns_503_when_no_model(client: TestClient):
    response = client.get(MODEL, headers=API_KEY_HEADER)

    assert response.status_code == 503
    assert response.json()["code"] == "MODEL_NOT_LOADED"


def test_readiness_reports_not_ready_without_model(client: TestClient):
    response = client.get("/health/ready")

    assert response.status_code == 503
    assert response.json() == {"status": "not_ready", "model_loaded": False}


def test_unknown_internal_route_returns_404(model_client: TestClient):
    assert model_client.get("/v1/does-not-exist", headers=API_KEY_HEADER).status_code == 404
