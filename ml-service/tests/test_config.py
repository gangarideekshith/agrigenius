import pytest
from pydantic import ValidationError

from app.core.config import AppEnvironment, Settings


def test_defaults_are_private_and_local():
    settings = Settings(_env_file=None)

    assert settings.app_env is AppEnvironment.LOCAL
    assert settings.host == "127.0.0.1"
    assert settings.port == 8001
    assert settings.log_level == "INFO"
    assert settings.docs_enabled is True


def test_environment_variables_override_defaults(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ML_APP_ENV", "staging")
    monkeypatch.setenv("ML_SERVICE_NAME", "ml-from-env")
    monkeypatch.setenv("ML_PORT", "9001")
    monkeypatch.setenv("ML_LOG_LEVEL", "debug")
    monkeypatch.setenv("ML_DOCS_ENABLED", "false")

    settings = Settings(_env_file=None)

    assert settings.app_env is AppEnvironment.STAGING
    assert settings.service_name == "ml-from-env"
    assert settings.port == 9001
    assert settings.log_level == "DEBUG"
    assert settings.docs_enabled is False


def test_env_file_is_read(tmp_path):
    env_file = tmp_path / ".env"
    env_file.write_text("ML_SERVICE_VERSION=1.2.3\n", encoding="utf-8")

    assert Settings(_env_file=env_file).service_version == "1.2.3"


def test_production_disables_docs_unless_explicitly_enabled():
    assert Settings(_env_file=None, app_env="production").docs_enabled is False
    assert Settings(_env_file=None, app_env="production", docs_enabled=True).docs_enabled is True


@pytest.mark.parametrize(
    ("variable", "value"),
    [
        ("ML_APP_ENV", "prod-ish"),
        ("ML_LOG_LEVEL", "VERBOSE"),
        ("ML_PORT", "0"),
        ("ML_PORT", "70000"),
        ("ML_PORT", "not-a-number"),
        ("ML_SERVICE_NAME", ""),
    ],
)
def test_invalid_values_fail_fast(monkeypatch: pytest.MonkeyPatch, variable: str, value: str):
    monkeypatch.setenv(variable, value)

    with pytest.raises(ValidationError):
        Settings(_env_file=None)


def test_internal_api_key_is_required(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("ML_INTERNAL_API_KEY", raising=False)

    with pytest.raises(ValidationError):
        Settings(_env_file=None)


@pytest.mark.parametrize(
    "key",
    ["too-short", "REPLACE_WITH_BASE64_OF_48_RANDOM_BYTES_PLACEHOLDER"],
)
def test_weak_or_placeholder_api_key_is_rejected(monkeypatch: pytest.MonkeyPatch, key: str):
    monkeypatch.setenv("ML_INTERNAL_API_KEY", key)

    with pytest.raises(ValidationError):
        Settings(_env_file=None)


def test_api_key_is_never_shown_in_repr():
    settings = Settings(_env_file=None)

    assert settings.internal_api_key.get_secret_value() not in repr(settings)
    assert settings.internal_api_key.get_secret_value() not in str(settings.model_dump())


@pytest.mark.parametrize("version", ["../1.0.0", "1.0", "latest", "1.0.0/../../etc"])
def test_model_version_must_be_semantic_version(version: str):
    with pytest.raises(ValidationError):
        Settings(_env_file=None, crop_model_version=version)
