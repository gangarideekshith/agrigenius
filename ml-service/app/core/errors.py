"""JSON error responses for the internal /v1 API.

Shape (see docs/api/contracts/crop-recommendation/error-*.json):
    {"code": "...", "message": "...", "errors": [{"field": "...", "message": "..."}]}

Input values and stack traces are never included.
"""

from fastapi import FastAPI, Request
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

INTERNAL_API_PREFIX = "/v1/"


class ApiError(Exception):
    """An error with a fixed HTTP status, stable code and safe message."""

    def __init__(self, status_code: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


def _field_name(location: tuple) -> str:
    parts = [str(part) for part in location]
    if parts and parts[0] in {"body", "query", "path", "header"}:
        parts = parts[1:]
    return ".".join(parts) or "body"


async def _api_error_handler(_: Request, exc: ApiError) -> JSONResponse:
    return JSONResponse(status_code=exc.status_code, content={"code": exc.code, "message": exc.message})


async def _validation_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    if not request.url.path.startswith(INTERNAL_API_PREFIX):
        return await request_validation_exception_handler(request, exc)
    errors = [
        {"field": _field_name(tuple(error.get("loc", ()))), "message": str(error.get("msg", "Invalid value"))}
        for error in exc.errors()
    ]
    return JSONResponse(
        status_code=422,
        content={"code": "INVALID_REQUEST", "message": "Request validation failed.", "errors": errors},
    )


def register_error_handlers(app: FastAPI) -> None:
    app.add_exception_handler(ApiError, _api_error_handler)
    app.add_exception_handler(RequestValidationError, _validation_error_handler)
