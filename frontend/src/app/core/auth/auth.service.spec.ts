import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService, SESSION_STORAGE_KEY } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  function create(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    http?.verify();
  });

  it('stores the token in memory and sessionStorage only, never localStorage', () => {
    const auth = create();
    auth.login({ mobileNumber: '9876543210', password: 'pass1234' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      accessToken: 'tok-123',
      tokenType: 'Bearer',
      expiresIn: 1800,
      user: { id: 'u1', role: 'FARMER' },
    });

    expect(auth.token()).toBe('tok-123');
    expect(auth.isAuthenticated()).toBe(true);
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toContain('tok-123');
    expect(localStorage.length).toBe(0);
  });

  it('never stores the password', () => {
    const auth = create();
    auth.login({ mobileNumber: '9876543210', password: 'secret-pass-9' }).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 60, user: { id: 'u', role: 'FARMER' } });
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).not.toContain('secret-pass-9');
  });

  it('expires the session and signs out automatically', () => {
    vi.useFakeTimers();
    const auth = create();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login({ mobileNumber: '9876543210', password: 'pass1234' }).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 60, user: { id: 'u', role: 'FARMER' } });

    vi.advanceTimersByTime(61_000);

    expect(auth.token()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });

  it('restores a valid session after reload and drops an expired one', () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ token: 'kept', expiresAt: Date.now() + 60_000, userId: 'u', role: 'FARMER' }),
    );
    expect(create().token()).toBe('kept');

    TestBed.resetTestingModule();
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ token: 'old', expiresAt: Date.now() - 1, userId: 'u', role: 'FARMER' }),
    );
    expect(create().token()).toBeNull();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('ignores a corrupted stored session', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, '{not json');
    expect(create().token()).toBeNull();
  });

  it('logout clears the session and returns to login', () => {
    const auth = create();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login({ mobileNumber: '9876543210', password: 'pass1234' }).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 600, user: { id: 'u', role: 'FARMER' } });

    auth.logout('signed-out');

    expect(auth.token()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'signed-out' } });
  });
});
