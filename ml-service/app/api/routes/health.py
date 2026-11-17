"""Liveness and readiness endpoints (unauthenticated, loopback-only service).

``/health`` reports only that the process is up. ``/health/ready`` additionally reports whether
the crop recommendation model is loaded; it answers 503 when it is not.
"""

from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from app.core.config import AppEnvironment, Settings, get_settings

router = APIRouter(tags=["health"])


class HealthResponse(BaseModel):
    status: Literal["ok"]
    service: str
    version: str
    environment: AppEnvironment


class ReadinessResponse(BaseModel):
    status: Literal["ready", "not_ready"]
    model_loaded: bool
    model_version: str | None = None


@router.get("/health", response_model=HealthResponse, summary="Service liveness")
def health(settings: Annotated[Settings, Depends(get_settings)]) -> HealthResponse:
    return HealthResponse(
        status="ok",
        service=settings.service_name,
        version=settings.service_version,
        environment=settings.app_env,
    )


@router.get(
    "/health/ready",
    response_model=ReadinessResponse,
    responses={503: {"model": ReadinessResponse}},
    summary="Service readiness (crop model loaded)",
)
def ready(request: Request):
    model = getattr(request.app.state, "crop_model", None)
    if model is None:
        return JSONResponse(status_code=503, content={"status": "not_ready", "model_loaded": False})
    return ReadinessResponse(status="ready", model_loaded=True, model_version=model.metadata.version)
