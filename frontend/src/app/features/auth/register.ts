import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { ErrorView, describeError } from '../../core/api/error-message';
import { LANGUAGES, LanguageCode, RegisterRequest } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import {
  applyServerErrors,
  fullNameValidator,
  indianMobileValidator,
  normalizeMobile,
  passwordNotMobileValidator,
  passwordPolicyValidator,
} from '../../core/validation/validators';
import { FieldError } from '../../shared/field-error';
import { ErrorBanner } from '../../shared/state-views';

type TextField = 'fullName' | 'mobileNumber' | 'password' | 'email' | 'state' | 'district' | 'village';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldError, ErrorBanner],
  template: `
    <section class="auth-card card">
      <h1>{{ 'register.title' | t }}</h1>
      <p class="muted">{{ 'register.subtitle' | t }}</p>
      <app-error-banner [error]="error()" />

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="reg-name">{{ 'register.fullName' | t }}</label>
          <input id="reg-name" autocomplete="name" formControlName="fullName"
                 aria-describedby="reg-name-error" [attr.aria-invalid]="invalid('fullName')" />
          <app-field-error [control]="form.controls.fullName" errorId="reg-name-error" />
        </div>
        <div class="field">
          <label for="reg-mobile">{{ 'register.mobile' | t }}</label>
          <input id="reg-mobile" type="tel" inputmode="tel" autocomplete="tel" formControlName="mobileNumber"
                 aria-describedby="reg-mobile-help reg-mobile-error" [attr.aria-invalid]="invalid('mobileNumber')" />
          <p id="reg-mobile-help" class="help">{{ 'login.mobileHelp' | t }}</p>
          <app-field-error [control]="form.controls.mobileNumber" errorId="reg-mobile-error" />
        </div>
        <div class="field">
          <label for="reg-password">{{ 'register.password' | t }}</label>
          <input id="reg-password" type="password" autocomplete="new-password" formControlName="password"
                 aria-describedby="reg-password-help reg-password-error" [attr.aria-invalid]="invalid('password')" />
          <p id="reg-password-help" class="help">{{ 'register.passwordHelp' | t }}</p>
          <app-field-error [control]="form.controls.password" errorId="reg-password-error" />
        </div>
        <div class="field">
          <label for="reg-language">{{ 'register.language' | t }}</label>
          <select id="reg-language" formControlName="preferredLanguage">
            @for (code of languages; track code) {
              <option [value]="code">{{ 'languages.' + code | t }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="reg-email">{{ 'register.email' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
          <input id="reg-email" type="email" autocomplete="email" formControlName="email"
                 aria-describedby="reg-email-error" [attr.aria-invalid]="invalid('email')" />
          <app-field-error [control]="form.controls.email" errorId="reg-email-error" />
        </div>
        <div class="field-row">
          <div class="field">
            <label for="reg-state">{{ 'register.state' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
            <input id="reg-state" autocomplete="address-level1" formControlName="state" aria-describedby="reg-state-error" />
            <app-field-error [control]="form.controls.state" errorId="reg-state-error" />
          </div>
          <div class="field">
            <label for="reg-district">{{ 'register.district' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
            <input id="reg-district" autocomplete="address-level2" formControlName="district" aria-describedby="reg-district-error" />
            <app-field-error [control]="form.controls.district" errorId="reg-district-error" />
          </div>
        </div>
        <div class="field">
          <label for="reg-village">{{ 'register.village' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
          <input id="reg-village" formControlName="village" aria-describedby="reg-village-error" />
          <app-field-error [control]="form.controls.village" errorId="reg-village-error" />
        </div>

        <button type="submit" class="btn btn-primary btn-block" [disabled]="pending()">
          {{ (pending() ? 'register.submitting' : 'register.submit') | t }}
        </button>
      </form>

      <p class="switch-link">
        {{ 'register.haveAccount' | t }} <a routerLink="/login">{{ 'register.loginLink' | t }}</a>
      </p>
    </section>
  `,
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  protected readonly languages = LANGUAGES;
  readonly pending = signal(false);
  readonly error = signal<ErrorView | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      fullName: ['', [Validators.required, fullNameValidator]],
      mobileNumber: ['', [Validators.required, indianMobileValidator]],
      password: ['', [Validators.required, passwordPolicyValidator]],
      preferredLanguage: [this.i18n.language() as LanguageCode, Validators.required],
      email: ['', [Validators.email, Validators.maxLength(254)]],
      state: ['', Validators.maxLength(60)],
      district: ['', Validators.maxLength(60)],
      village: ['', Validators.maxLength(100)],
    },
    { validators: passwordNotMobileValidator('mobileNumber', 'password') },
  );

  invalid(name: TextField): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  submit(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set({ message: this.i18n.t('validation.formInvalid'), reference: null });
      return;
    }
    const v = this.form.getRawValue();
    const request: RegisterRequest = {
      fullName: v.fullName.trim(),
      mobileNumber: normalizeMobile(v.mobileNumber),
      password: v.password,
      preferredLanguage: v.preferredLanguage,
      email: v.email.trim() || null,
      state: v.state.trim() || null,
      district: v.district.trim() || null,
      village: v.village.trim() || null,
    };
    this.pending.set(true);
    this.auth
      .register(request)
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: () => void this.router.navigate(['/login'], { queryParams: { registered: '1' } }),
        error: (err: unknown) => {
          const apiError = asApiError(err);
          if (apiError.code === 'VALIDATION_FAILED') {
            applyServerErrors(this.form, apiError.fieldErrors);
          }
          this.error.set(describeError(apiError, this.i18n));
        },
      });
  }
}
