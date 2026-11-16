"""Run the service using ML_HOST / ML_PORT / ML_LOG_LEVEL from settings.

Usage (from ml-service/):  python -m app
"""

import uvicorn

from app.core.config import get_settings


def main() -> None:
    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        log_level=settings.log_level.lower(),
    )


if __name__ == "__main__":
    main()
