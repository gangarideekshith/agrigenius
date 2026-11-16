"""FastAPI application entry point for the private AgriGenius ML service.

Run locally with:
    python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
"""

from fastapi import FastAPI

from app.api.routes import health
from app.core.config import Settings, get_settings
from app.core.logging import configure_logging


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    docs = settings.docs_enabled
    app = FastAPI(
        title="AgriGenius ML Service",
        version=settings.service_version,
        description="Internal ML inference service. Called only by the Spring Boot backend.",
        docs_url="/docs" if docs else None,
        redoc_url="/redoc" if docs else None,
        openapi_url="/openapi.json" if docs else None,
    )
    # Ensure routes that depend on get_settings see the same settings object.
    app.dependency_overrides[get_settings] = lambda: settings

    app.include_router(health.router)
    return app


app = create_app()
