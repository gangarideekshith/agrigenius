import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApiError } from '../api/api-error';
import { AuthService } from './auth.service';
import { authInterceptor, errorInterceptor } from './interceptors';

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const auth = { token: vi.fn<() => string | null>(), logout: vi.fn() };

  beforeEach(() => {
    auth.token.mockReset().mockReturnValue('abc');
    auth.logout.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to protected API calls', () => {
    http.get('/api/farmers/me').subscribe();
    expect(backend.expectOne('/api/farmers/me').request.headers.get('Authorization')).toBe('Bearer abc');
  });

  it('does not send the token to the public auth endpoints', () => {
    http.post('/api/auth/login', {}).subscribe();
    http.post('/api/auth/register', {}).subscribe();
    expect(backend.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('/api/auth/register').request.headers.has('Authorization')).toBe(false);
  });

  it('never sends the token outside the API', () => {
    http.get('https://example.org/data').subscribe();
    expect(backend.expectOne('https://example.org/data').request.headers.has('Authorization')).toBe(false);
  });

  it('sends no header when signed out', () => {
    auth.token.mockReturnValue(null);
    http.get('/api/farmers/me').subscribe();
    expect(backend.expectOne('/api/farmers/me').request.headers.has('Authorization')).toBe(false);
  });

  it('converts problem+json errors to ApiError and signs out on 401 from protected endpoints', () => {
    let caught: unknown;
    http.get('/api/farmers/me').subscribe({ error: (e: unknown) => (caught = e) });
    backend
      .expectOne('/api/farmers/me')
      .flush({ code: 'AUTHENTICATION_REQUIRED', status: 401, requestId: 'r1' }, { status: 401, statusText: 'Unauthorized' });

    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).code).toBe('AUTHENTICATION_REQUIRED');
    expect(auth.logout).toHaveBeenCalledWith('expired');
  });

  it('does not sign out on a failed login (wrong password is also 401)', () => {
    let caught: unknown;
    http.post('/api/auth/login', {}).subscribe({ error: (e: unknown) => (caught = e) });
    backend.expectOne('/api/auth/login').flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });

    expect((caught as ApiError).code).toBe('INVALID_CREDENTIALS');
    expect(auth.logout).not.toHaveBeenCalled();
  });
});
