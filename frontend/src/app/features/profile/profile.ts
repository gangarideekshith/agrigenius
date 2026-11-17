import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { FarmerApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { FarmerProfile, LANGUAGES, LanguageCode, UpdateProfileRequest } from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { applyServerErrors, fullNameValidator } from '../../core/validation/validators';
import { FieldError } from '../../shared/field-error';
import { ErrorBanner, Loading } from '../../shared/state-views';

type TextField = 'fullName' | 'email' | 'state' | 'district' | 'village' | 'addressLine';

/**
 * The signed-in farmer's profile: view and edit the fields the backend allows
 * (PUT /api/farmers/me). The mobile number is shown read-only because it is the login identifier.
 */
@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, DatePipe, TranslatePipe, FieldError, ErrorBanner, Loading],
  template: `
    <h1>{{ 'profile.title' | t }}</h1>
    @if (loading()) {
      <app-loading />
    } @else if (loadError()) {
      <app-error-banner [error]="loadError()" [retryable]="true" (retry)="load()" />
    } @else if (profile(); as p) {
      <section class="card">
        <dl class="details">
          <div><dt>{{ 'dashboard.mobile' | t }}</dt><dd>{{ p.mobileNumber }}</dd></div>
          <div><dt>{{ 'dashboard.memberSince' | t }}</dt><dd>{{ p.createdAt | date: 'mediumDate' }}</dd></div>
        </dl>
        <p class="help">{{ 'profile.mobileReadOnly' | t }}</p>
      </section>

      <section class="card" aria-labelledby="profile-edit-title">
        <h2 id="profile-edit-title">{{ 'profile.editTitle' | t }}</h2>
        @if (saved()) {
          <p class="state state-success" role="status" aria-live="polite">{{ 'profile.saved' | t }}</p>
        }
        <app-error-banner [error]="saveError()" />

        <form [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="field">
            <label for="pf-name">{{ 'register.fullName' | t }}</label>
            <input id="pf-name" autocomplete="name" formControlName="fullName"
                   aria-describedby="pf-name-error" [attr.aria-invalid]="invalid('fullName')" />
            <app-field-error [control]="form.controls.fullName" errorId="pf-name-error" />
          </div>
          <div class="field">
            <label for="pf-language">{{ 'register.language' | t }}</label>
            <select id="pf-language" formControlName="preferredLanguage">
              @for (code of languages; track code) {
                <option [value]="code">{{ 'languages.' + code | t }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="pf-email">{{ 'register.email' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
            <input id="pf-email" type="email" autocomplete="email" formControlName="email"
                   aria-describedby="pf-email-error" [attr.aria-invalid]="invalid('email')" />
            <app-field-error [control]="form.controls.email" errorId="pf-email-error" />
          </div>
          <div class="field-row">
            <div class="field">
              <label for="pf-state">{{ 'register.state' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
              <input id="pf-state" autocomplete="address-level1" formControlName="state" aria-describedby="pf-state-error" />
              <app-field-error [control]="form.controls.state" errorId="pf-state-error" />
            </div>
            <div class="field">
              <label for="pf-district">{{ 'register.district' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
              <input id="pf-district" autocomplete="address-level2" formControlName="district" aria-describedby="pf-district-error" />
              <app-field-error [control]="form.controls.district" errorId="pf-district-error" />
            </div>
          </div>
          <div class="field">
            <label for="pf-village">{{ 'register.village' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
            <input id="pf-village" formControlName="village" aria-describedby="pf-village-error" />
            <app-field-error [control]="form.controls.village" errorId="pf-village-error" />
          </div>
          <div class="field">
            <label for="pf-address">{{ 'profile.addressLine' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
            <input id="pf-address" autocomplete="street-address" formControlName="addressLine" aria-describedby="pf-address-error" />
            <app-field-error [control]="form.controls.addressLine" errorId="pf-address-error" />
          </div>

          <div class="actions">
            <button type="button" class="btn btn-secondary" [disabled]="pending() || form.pristine" (click)="reset()">
              {{ 'common.cancel' | t }}
            </button>
            <button type="submit" class="btn btn-primary" [disabled]="pending() || form.pristine">
              {{ (pending() ? 'profile.saving' : 'profile.save') | t }}
            </button>
          </div>
        </form>
      </section>
    }
  `,
})
export class ProfilePage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly farmers = inject(FarmerApi);
  private readonly i18n = inject(I18nService);

  protected readonly languages = LANGUAGES;
  readonly profile = signal<FarmerProfile | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<ErrorView | null>(null);
  readonly saveError = signal<ErrorView | null>(null);
  readonly pending = signal(false);
  readonly saved = signal(false);

  readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, fullNameValidator]],
    preferredLanguage: ['en' as LanguageCode, Validators.required],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    state: ['', Validators.maxLength(60)],
    district: ['', Validators.maxLength(60)],
    village: ['', Validators.maxLength(100)],
    addressLine: ['', Validators.maxLength(200)],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.farmers.me().subscribe({
      next: (p) => {
        this.fill(p);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loadError.set(describeError(asApiError(err), this.i18n));
        this.loading.set(false);
      },
    });
  }

  invalid(name: TextField): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  reset(): void {
    const p = this.profile();
    if (p) {
      this.fill(p);
    }
    this.saveError.set(null);
  }

  save(): void {
    this.saved.set(false);
    this.saveError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.saveError.set({ message: this.i18n.t('validation.formInvalid'), reference: null });
      return;
    }
    const v = this.form.getRawValue();
    const request: UpdateProfileRequest = {
      fullName: v.fullName.trim(),
      preferredLanguage: v.preferredLanguage,
      email: v.email.trim() || null,
      state: v.state.trim() || null,
      district: v.district.trim() || null,
      village: v.village.trim() || null,
      addressLine: v.addressLine.trim() || null,
    };
    this.pending.set(true);
    this.farmers
      .updateMe(request)
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: (p) => {
          this.fill(p);
          this.i18n.applyProfilePreference(p.preferredLanguage);
          this.saved.set(true);
        },
        error: (err: unknown) => {
          const apiError = asApiError(err);
          if (apiError.code === 'VALIDATION_FAILED') {
            applyServerErrors(this.form, apiError.fieldErrors);
          }
          this.saveError.set(describeError(apiError, this.i18n));
        },
      });
  }

  private fill(p: FarmerProfile): void {
    this.profile.set(p);
    this.form.reset({
      fullName: p.fullName,
      preferredLanguage: p.preferredLanguage,
      email: p.email ?? '',
      state: p.state ?? '',
      district: p.district ?? '',
      village: p.village ?? '',
      addressLine: p.addressLine ?? '',
    });
  }
}
