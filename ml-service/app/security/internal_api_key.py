"""Service-to-service authentication for the internal API (ADR-0004).

The Spring Boot backend sends the shared secret in ``X-Internal-Api-Key``. The comparison is
constant-time, the expected key is never logged, and every failure looks the same.
"""

import hmac
from typing import Annotated

from fastapi import Depends, Request

from app.core.config import Settings, get_settings
from app.core.errors import ApiError

HEADER_NAME = "X-Internal-Api-Key"


def require_internal_api_key(request: Request, settings: Annotated[Settings, Depends(get_settings)]) -> None:
    provided = request.headers.get(HEADER_NAME)
    expected = settings.internal_api_key.get_secret_value()
    if provided is None or not hmac.compare_digest(provided.encode("utf-8"), expected.encode("utf-8")):
        raise ApiError(401, "UNAUTHORIZED", "Missing or invalid internal API key.")
