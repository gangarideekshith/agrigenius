import { HttpErrorResponse } from '@angular/common/http';

/** One field error from a problem+json VALIDATION_FAILED response. `field` may be nested, e.g. `soil.ph`. */
export interface FieldError {
  field: string;
  message: string;
}

/**
 * Normalised API failure. Every HTTP error reaching components is converted to this by
 * {@link errorInterceptor}, so templates only deal with a stable `code` (translated in the UI).
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string | null,
    readonly requestId: string | null,
    readonly fieldErrors: FieldError[],
    readonly retryAfterSeconds: number | null,
  ) {
    super(code);
    this.name = 'ApiError';
  }

  /** i18n key for this error's user-facing message. */
  get messageKey(): string {
    return `errors.${this.code}`;
  }
}

const KNOWN_FALLBACK_BY_STATUS: Record<number, string> = {
  0: 'NETWORK_ERROR',
  400: 'MALFORMED_REQUEST',
  401: 'AUTHENTICATION_REQUIRED',
  403: 'ACCESS_DENIED',
  404: 'NOT_FOUND',
  409: 'CONFLICT',
  429: 'TOO_MANY_REQUESTS',
  502: 'ML_SERVICE_ERROR',
  503: 'SERVICE_UNAVAILABLE',
};

/** Builds an {@link ApiError} from any HttpErrorResponse, problem+json or not. */
export function toApiError(response: HttpErrorResponse): ApiError {
  const body = isRecord(response.error) ? response.error : {};
  const code =
    typeof body['code'] === 'string' && body['code']
      ? (body['code'] as string)
      : (KNOWN_FALLBACK_BY_STATUS[response.status] ?? (response.status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN_ERROR'));
  const fieldErrors: FieldError[] = Array.isArray(body['errors'])
    ? (body['errors'] as unknown[])
        .filter(isRecord)
        .filter((e) => typeof e['field'] === 'string')
        .map((e) => ({ field: e['field'] as string, message: String(e['message'] ?? '') }))
    : [];
  const retryHeader = response.headers?.get('Retry-After');
  const retryAfter = retryHeader !== null && retryHeader !== undefined && /^\d+$/.test(retryHeader)
    ? Number(retryHeader)
    : null;
  return new ApiError(
    response.status,
    code,
    typeof body['detail'] === 'string' ? (body['detail'] as string) : null,
    typeof body['requestId'] === 'string' ? (body['requestId'] as string) : null,
    fieldErrors,
    retryAfter,
  );
}

/** Narrow unknown errors from service calls to ApiError (anything else becomes INTERNAL_ERROR). */
export function asApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (error instanceof HttpErrorResponse) {
    return toApiError(error);
  }
  return new ApiError(0, 'UNKNOWN_ERROR', null, null, [], null);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
