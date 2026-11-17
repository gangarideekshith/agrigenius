import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { toApiError } from '../api/api-error';
import { AuthService } from './auth.service';

const API = environment.apiBaseUrl;
const PUBLIC_AUTH_PREFIX = `${API}/auth/`;

function isApiRequest(url: string): boolean {
  return url === API || url.startsWith(`${API}/`);
}

/** Adds `Authorization: Bearer` to backend API calls, except the public login/register endpoints. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiRequest(req.url) || req.url.startsWith(PUBLIC_AUTH_PREFIX)) {
    return next(req);
  }
  const token = inject(AuthService).token();
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
};

/**
 * Converts every API failure into an {@link ApiError}. A 401 on a protected endpoint means the
 * session is no longer valid: the user is signed out and sent to the login page.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const apiError = toApiError(error);
      if (error.status === 401 && isApiRequest(req.url) && !req.url.startsWith(PUBLIC_AUTH_PREFIX)) {
        auth.logout('expired');
      }
      return throwError(() => apiError);
    }),
  );
};
