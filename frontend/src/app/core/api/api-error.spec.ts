import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { I18nService } from '../i18n/i18n.service';
import { ApiError, asApiError, toApiError } from './api-error';
import { describeError } from './error-message';

describe('ApiError mapping', () => {
  it('reads code, detail, requestId and nested field errors from problem+json', () => {
    const error = toApiError(
      new HttpErrorResponse({
        status: 400,
        error: {
          code: 'VALIDATION_FAILED',
          detail: 'Request validation failed.',
          requestId: 'req-1',
          errors: [
            { field: 'soil.ph', message: 'must be at most 10' },
            { field: 'climate.humidityPct', message: 'must be at most 100' },
          ],
        },
      }),
    );
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.requestId).toBe('req-1');
    expect(error.fieldErrors.map((f) => f.field)).toEqual(['soil.ph', 'climate.humidityPct']);
  });

  it('reads Retry-After seconds', () => {
    const error = toApiError(
      new HttpErrorResponse({ status: 429, error: { code: 'TOO_MANY_REQUESTS' }, headers: new HttpHeaders({ 'Retry-After': '42' }) }),
    );
    expect(error.retryAfterSeconds).toBe(42);
  });

  it('falls back to a code from the status when the body is not problem+json', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') })).code).toBe('NETWORK_ERROR');
    expect(toApiError(new HttpErrorResponse({ status: 503, error: 'oops' })).code).toBe('SERVICE_UNAVAILABLE');
    expect(toApiError(new HttpErrorResponse({ status: 500, error: null })).code).toBe('INTERNAL_ERROR');
  });

  it('wraps unknown errors', () => {
    expect(asApiError(new Error('x')).code).toBe('UNKNOWN_ERROR');
  });

  it('describes errors with translated text, wait time and reference only for server errors', () => {
    const i18n = TestBed.inject(I18nService);
    expect(describeError(new ApiError(429, 'TOO_MANY_REQUESTS', null, 'r', [], 30), i18n).message).toContain('30');
    const server = describeError(new ApiError(500, 'INTERNAL_ERROR', 'stack trace here', 'req-9', [], null), i18n);
    expect(server.message).toBe('Something went wrong on our side.');
    expect(server.reference).toContain('req-9');
    expect(describeError(new ApiError(401, 'INVALID_CREDENTIALS', null, 'r', [], null), i18n).reference).toBeNull();
    // Server-provided detail text is never shown verbatim.
    expect(server.message).not.toContain('stack trace');
  });
});
