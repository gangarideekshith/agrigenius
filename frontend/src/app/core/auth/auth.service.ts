import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, RegisterRequest, FarmerProfile } from '../api/models';

/** What is kept between page reloads. Never contains the password. */
interface StoredSession {
  token: string;
  expiresAt: number;
  userId: string;
  role: string;
}

export const SESSION_STORAGE_KEY = 'agrigenius.session';

/**
 * Holds the access token in memory and in sessionStorage (cleared when the tab closes).
 * localStorage is never used, so the token does not survive the browser session.
 * The session ends automatically when the token expires.
 */
@Injectable({ providedIn: 'root' })
export class AuthService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<StoredSession | null>(null);
  private expiryTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly role = computed(() => this.session()?.role ?? null);

  constructor() {
    this.restore();
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }

  /** The bearer token, or null if there is none or it has expired. */
  token(): string | null {
    const current = this.session();
    if (!current) {
      return null;
    }
    if (Date.now() >= current.expiresAt) {
      this.clear();
      return null;
    }
    return current.token;
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, request).pipe(
      tap((response) =>
        this.start({
          token: response.accessToken,
          expiresAt: Date.now() + response.expiresIn * 1000,
          userId: response.user.id,
          role: response.user.role,
        }),
      ),
    );
  }

  register(request: RegisterRequest): Observable<FarmerProfile> {
    return this.http.post<FarmerProfile>(`${environment.apiBaseUrl}/auth/register`, request);
  }

  /** Ends the session and returns to the login page. `reason` is shown there (e.g. "expired"). */
  logout(reason?: 'expired' | 'signed-out'): void {
    this.clear();
    void this.router.navigate(['/login'], reason ? { queryParams: { reason } } : {});
  }

  private start(session: StoredSession): void {
    this.session.set(session);
    try {
      sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    } catch {
      // Storage may be unavailable (private mode); the in-memory session still works.
    }
    this.scheduleExpiry(session.expiresAt);
  }

  private restore(): void {
    let raw: string | null = null;
    try {
      raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
    } catch {
      return;
    }
    if (!raw) {
      return;
    }
    try {
      const parsed = JSON.parse(raw) as Partial<StoredSession>;
      if (
        typeof parsed.token === 'string' &&
        typeof parsed.expiresAt === 'number' &&
        typeof parsed.userId === 'string' &&
        typeof parsed.role === 'string' &&
        parsed.expiresAt > Date.now()
      ) {
        this.session.set(parsed as StoredSession);
        this.scheduleExpiry(parsed.expiresAt);
        return;
      }
    } catch {
      // fall through: corrupted entry
    }
    this.clear();
  }

  private scheduleExpiry(expiresAt: number): void {
    this.clearTimer();
    const delay = Math.max(0, expiresAt - Date.now());
    // setTimeout overflows above ~24.8 days; tokens live 30 minutes, but guard anyway.
    this.expiryTimer = setTimeout(() => this.logout('expired'), Math.min(delay, 2_147_000_000));
  }

  private clear(): void {
    this.clearTimer();
    this.session.set(null);
    try {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // ignore
    }
  }

  private clearTimer(): void {
    if (this.expiryTimer !== null) {
      clearTimeout(this.expiryTimer);
      this.expiryTimer = null;
    }
  }
}
