"""Typed, environment-driven configuration for the ML service.

All settings use the ``ML_`` prefix and may come from process environment
variables or from ``ml-service/.env`` (gitignored). Invalid values fail fast
at startup.
"""

from enum import Enum
from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

SERVICE_ROOT = Path(__file__).resolve().parents[2]

_LOG_LEVELS = {"CRITICAL", "ERROR", "WARNING", "INFO", "DEBUG"}


class AppEnvironment(str, Enum):
    LOCAL = "local"
    TEST = "test"
    STAGING = "staging"
    PRODUCTION = "production"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="ML_",
        env_file=SERVICE_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_env: AppEnvironment = AppEnvironment.LOCAL
    service_name: str = Field(default="agrigenius-ml-service", min_length=1)
    service_version: str = Field(default="0.1.0", min_length=1)
    host: str = "127.0.0.1"
    port: int = Field(default=8001, ge=1, le=65535)
    log_level: str = "INFO"
    # None means "derive from app_env": enabled everywhere except production.
    docs_enabled: bool | None = None

    # Shared secret the Spring Boot backend sends in X-Internal-Api-Key (ADR-0004).
    # Required: the service refuses to start without a strong key.
    internal_api_key: SecretStr = Field(min_length=32)
    # Root directory of crop model artifacts; the active model lives in <model_dir>/<version>/.
    model_dir: Path = SERVICE_ROOT / "models" / "crop_recommendation"
    # Semantic version only, so it can never escape model_dir as a path.
    crop_model_version: str = Field(default="1.0.0", pattern=r"^[0-9]+\.[0-9]+\.[0-9]+$")

    @field_validator("internal_api_key")
    @classmethod
    def _reject_placeholder_key(cls, value: SecretStr) -> SecretStr:
        if value.get_secret_value().strip().upper().startswith("REPLACE_WITH"):
            raise ValueError("internal_api_key must be a generated secret, not the .env.example placeholder")
        return value

    @field_validator("log_level")
    @classmethod
    def _validate_log_level(cls, value: str) -> str:
        normalized = value.strip().upper()
        if normalized not in _LOG_LEVELS:
            raise ValueError(f"log_level must be one of {sorted(_LOG_LEVELS)}")
        return normalized

    @model_validator(mode="after")
    def _derive_docs_enabled(self) -> "Settings":
        if self.docs_enabled is None:
            self.docs_enabled = self.app_env is not AppEnvironment.PRODUCTION
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
