import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap, finalize } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { FarmerApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { indianMobileValidator, normalizeMobile } from '../../core/validation/validators';
import { FieldError } from '../../shared/field-error';
import { ErrorBanner } from '../../shared/state-views';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldError, ErrorBanner],
  template: `
    <section class="auth-card card">
      <h1>{{ 'login.title' | t }}</h1>
      <p class="muted">{{ 'login.subtitle' | t }}</p>

      @if (notice(); as key) {
        <p class="notice" role="status">{{ key | t }}</p>
      }
      <app-error-banner [error]="error()" />

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="login-mobile">{{ 'login.mobile' | t }}</label>
          <input
            id="login-mobile"
            type="tel"
            inputmode="tel"
            autocomplete="tel"
            formControlName="mobileNumber"
            aria-describedby="login-mobile-help login-mobile-error"
            [attr.aria-invalid]="invalid('mobileNumber')"
          />
          <p id="login-mobile-help" class="help">{{ 'login.mobileHelp' | t }}</p>
          <app-field-error [control]="form.controls.mobileNumber" errorId="login-mobile-error" />
        </div>
        <div class="field">
          <label for="login-password">{{ 'login.password' | t }}</label>
          <input
            id="login-password"
            type="password"
            autocomplete="current-password"
            formControlName="password"
            aria-describedby="login-password-error"
            [attr.aria-invalid]="invalid('password')"
          />
          <app-field-error [control]="form.controls.password" errorId="login-password-error" />
        </div>
        <button type="submit" class="btn btn-primary btn-block" [disabled]="pending()">
          {{ (pending() ? 'login.submitting' : 'login.submit') | t }}
        </button>
      </form>

      <p class="switch-link">
        {{ 'login.noAccount' | t }} <a routerLink="/register">{{ 'login.registerLink' | t }}</a>
      </p>
    </section>
  `,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly farmers = inject(FarmerApi);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  /** Query params bound by the router. */
  readonly reason = input<string | undefined>();
  readonly registered = input<string | undefined>();
  readonly returnUrl = input<string | undefined>();

  readonly form = this.fb.nonNullable.group({
    mobileNumber: ['', [Validators.required, indianMobileValidator]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });
  readonly pending = signal(false);
  readonly error = signal<ErrorView | null>(null);

  notice(): string | null {
    if (this.registered()) return 'login.registered';
    if (this.reason() === 'expired') return 'login.expired';
    if (this.reason() === 'signed-out') return 'login.signedOut';
    return null;
  }

  invalid(name: 'mobileNumber' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  submit(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.pending.set(true);
    const { mobileNumber, password } = this.form.getRawValue();
    this.auth
      .login({ mobileNumber: normalizeMobile(mobileNumber), password })
      .pipe(
        switchMap(() => this.farmers.me()),
        finalize(() => this.pending.set(false)),
      )
      .subscribe({
        next: (profile) => {
          this.i18n.applyProfilePreference(profile.preferredLanguage);
          const target = this.safeReturnUrl();
          void this.router.navigateByUrl(target);
        },
        error: (err: unknown) => {
          this.form.controls.password.reset('');
          this.error.set(describeError(asApiError(err), this.i18n));
        },
      });
  }

  /** Only same-app paths are allowed as return targets (no open redirects). */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') ? url : '/dashboard';
  }
}
