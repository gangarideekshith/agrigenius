"""Liveness endpoint.

This reports only that the process is up and serving requests. It does not
check models or downstream dependencies; a readiness check will be added
when the first model is loaded.
"""

from typing import Annotated, Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.core.config import AppEnvironment, Settings, get_settings

router = APIRouter(tags=["health"])


class HealthResponse(BaseModel):
    status: Literal["ok"]
    service: str
    version: str
    environment: AppEnvironment


@router.get("/health", response_model=HealthResponse, summary="Service liveness")
def health(settings: Annotated[Settings, Depends(get_settings)]) -> HealthResponse:
    return HealthResponse(
        status="ok",
        service=settings.service_name,
        version=settings.service_version,
        environment=settings.app_env,
    )
