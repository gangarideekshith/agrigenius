import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Allows signed-in users; others go to /login and come back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.token()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Keeps signed-in users away from the login and register pages. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.token() ? inject(Router).createUrlTree(['/dashboard']) : true;
};
