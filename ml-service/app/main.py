"""FastAPI application entry point for the private AgriGenius ML service.

Run locally with:
    python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import health
from app.core.config import Settings, get_settings
from app.core.errors import register_error_handlers
from app.core.logging import configure_logging
from app.crop_recommendation import router as crop_recommendation
from app.crop_recommendation.model_loader import load_model


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        # Load (and checksum-verify) the crop model once at startup; None means "not ready".
        app.state.crop_model = load_model(settings.model_dir, settings.crop_model_version)
        yield
        app.state.crop_model = None

    docs = settings.docs_enabled
    app = FastAPI(
        title="AgriGenius ML Service",
        version=settings.service_version,
        description="Internal ML inference service. Called only by the Spring Boot backend.",
        docs_url="/docs" if docs else None,
        redoc_url="/redoc" if docs else None,
        openapi_url="/openapi.json" if docs else None,
        lifespan=lifespan,
    )
    app.state.crop_model = None
    # Ensure routes that depend on get_settings see the same settings object.
    app.dependency_overrides[get_settings] = lambda: settings

    register_error_handlers(app)
    app.include_router(health.router)
    app.include_router(crop_recommendation.router)
    return app


def __getattr__(name: str) -> FastAPI:
    """Create the ASGI ``app`` lazily (``uvicorn app.main:app``).

    Importing this module (for example ``create_app`` in tests) must not require
    ``ML_INTERNAL_API_KEY`` to be set; only serving does.
    """
    if name == "app":
        application = create_app()
        globals()["app"] = application
        return application
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
