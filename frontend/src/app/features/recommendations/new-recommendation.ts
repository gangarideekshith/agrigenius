import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { RecommendationApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { CROP_CODES, CreateRecommendationRequest, Season, SoilType } from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { applyServerErrors } from '../../core/validation/validators';
import { FieldError } from '../../shared/field-error';
import { ErrorBanner } from '../../shared/state-views';

const SOIL_TYPES: readonly SoilType[] = ['SANDY', 'LOAMY', 'CLAY', 'SILT', 'BLACK', 'RED', 'LATERITE', 'ALLUVIAL'];
const SEASONS: readonly Season[] = ['KHARIF', 'RABI', 'ZAID'];

/** Numeric inputs with the same ranges the backend enforces. */
interface NumberFieldDef {
  group: 'soil' | 'climate';
  name: string;
  label: string;
  help: string;
  min: number;
  max: number;
  step: string;
  required: boolean;
}

const SOIL_FIELDS: NumberFieldDef[] = [
  { group: 'soil', name: 'nitrogenKgHa', label: 'rec.nitrogen', help: 'rec.npkHelp', min: 0, max: 300, step: '0.1', required: true },
  { group: 'soil', name: 'phosphorusKgHa', label: 'rec.phosphorus', help: 'rec.npkHelp', min: 0, max: 300, step: '0.1', required: true },
  { group: 'soil', name: 'potassiumKgHa', label: 'rec.potassium', help: 'rec.npkHelp', min: 0, max: 300, step: '0.1', required: true },
  { group: 'soil', name: 'ph', label: 'rec.ph', help: 'rec.phHelp', min: 3, max: 10, step: '0.1', required: true },
  { group: 'soil', name: 'moisturePct', label: 'rec.moisture', help: 'rec.moistureHelp', min: 0, max: 100, step: '0.1', required: false },
];
const CLIMATE_FIELDS: NumberFieldDef[] = [
  { group: 'climate', name: 'temperatureC', label: 'rec.temperature', help: 'rec.temperatureHelp', min: -5, max: 55, step: '0.1', required: true },
  { group: 'climate', name: 'humidityPct', label: 'rec.humidity', help: 'rec.humidityHelp', min: 0, max: 100, step: '0.1', required: true },
  { group: 'climate', name: 'rainfallMm', label: 'rec.rainfall', help: 'rec.rainfallHelp', min: 0, max: 5000, step: '1', required: true },
];

@Component({
  selector: 'app-new-recommendation',
  imports: [ReactiveFormsModule, TranslatePipe, FieldError, ErrorBanner],
  template: `
    <h1>{{ 'rec.newTitle' | t }}</h1>
    <p class="muted">{{ 'rec.newIntro' | t }}</p>
    <app-error-banner [error]="error()" />

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="card">
      <fieldset formGroupName="soil">
        <legend>{{ 'rec.soilSection' | t }}</legend>
        <div class="grid-2">
          @for (f of soilFields; track f.name) {
            <div class="field">
              <label [for]="'rec-' + f.name">
                {{ f.label | t }}
                @if (!f.required) {
                  <span class="muted">({{ 'common.optional' | t }})</span>
                }
              </label>
              <input [id]="'rec-' + f.name" type="number" inputmode="decimal" [formControlName]="f.name"
                     [attr.min]="f.min" [attr.max]="f.max" [attr.step]="f.step"
                     [attr.aria-describedby]="'rec-' + f.name + '-help rec-' + f.name + '-error'"
                     [attr.aria-invalid]="invalid(f.group + '.' + f.name)" />
              <p class="help" [id]="'rec-' + f.name + '-help'">{{ f.help | t }}</p>
              <app-field-error [control]="form.get(f.group + '.' + f.name)" [errorId]="'rec-' + f.name + '-error'" />
            </div>
          }
          <div class="field">
            <label for="rec-soilType">{{ 'rec.soilType' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
            <select id="rec-soilType" formControlName="soilType">
              <option value="">{{ 'common.select' | t }}</option>
              @for (s of soilTypes; track s) {
                <option [value]="s">{{ 'soilTypes.' + s | t }}</option>
              }
            </select>
          </div>
        </div>
      </fieldset>

      <fieldset formGroupName="climate">
        <legend>{{ 'rec.climateSection' | t }}</legend>
        <div class="grid-2">
          @for (f of climateFields; track f.name) {
            <div class="field">
              <label [for]="'rec-' + f.name">{{ f.label | t }}</label>
              <input [id]="'rec-' + f.name" type="number" inputmode="decimal" [formControlName]="f.name"
                     [attr.min]="f.min" [attr.max]="f.max" [attr.step]="f.step"
                     [attr.aria-describedby]="'rec-' + f.name + '-help rec-' + f.name + '-error'"
                     [attr.aria-invalid]="invalid(f.group + '.' + f.name)" />
              <p class="help" [id]="'rec-' + f.name + '-help'">{{ f.help | t }}</p>
              <app-field-error [control]="form.get(f.group + '.' + f.name)" [errorId]="'rec-' + f.name + '-error'" />
            </div>
          }
        </div>
      </fieldset>

      <fieldset>
        <legend>{{ 'rec.moreSection' | t }} <span class="muted">({{ 'common.optional' | t }})</span></legend>
        <div class="grid-2">
          <div class="field">
            <label for="rec-season">{{ 'rec.season' | t }}</label>
            <select id="rec-season" formControlName="season">
              <option value="">{{ 'common.select' | t }}</option>
              @for (s of seasons; track s) {
                <option [value]="s">{{ 'seasons.' + s | t }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="rec-irrigation">{{ 'rec.irrigation' | t }}</label>
            <select id="rec-irrigation" formControlName="irrigationAvailable">
              <option value="">{{ 'common.notSure' | t }}</option>
              <option value="yes">{{ 'common.yes' | t }}</option>
              <option value="no">{{ 'common.no' | t }}</option>
            </select>
          </div>
          <div class="field">
            <label for="rec-previousCrop">{{ 'rec.previousCrop' | t }}</label>
            <select id="rec-previousCrop" formControlName="previousCrop" aria-describedby="rec-previousCrop-error">
              <option value="">{{ 'common.select' | t }}</option>
              @for (c of cropCodes; track c) {
                <option [value]="c">{{ 'crops.' + c | t }}</option>
              }
            </select>
            <app-field-error [control]="form.controls.previousCrop" errorId="rec-previousCrop-error" />
          </div>
          <div class="field" formGroupName="location">
            <label for="rec-state">{{ 'rec.state' | t }}</label>
            <input id="rec-state" formControlName="state" autocomplete="address-level1" />
          </div>
          <div class="field" formGroupName="location">
            <label for="rec-district">{{ 'rec.district' | t }}</label>
            <input id="rec-district" formControlName="district" autocomplete="address-level2" />
          </div>
        </div>
      </fieldset>

      <button type="submit" class="btn btn-primary btn-block" [disabled]="pending()">
        {{ (pending() ? 'rec.submitting' : 'rec.submit') | t }}
      </button>
    </form>
  `,
})
export class NewRecommendationPage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(RecommendationApi);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  protected readonly soilFields = SOIL_FIELDS;
  protected readonly climateFields = CLIMATE_FIELDS;
  protected readonly soilTypes = SOIL_TYPES;
  protected readonly seasons = SEASONS;
  protected readonly cropCodes = CROP_CODES;

  readonly pending = signal(false);
  readonly error = signal<ErrorView | null>(null);

  readonly form = this.fb.group({
    soil: this.fb.group({
      nitrogenKgHa: this.num(true, 0, 300),
      phosphorusKgHa: this.num(true, 0, 300),
      potassiumKgHa: this.num(true, 0, 300),
      ph: this.num(true, 3, 10),
      moisturePct: this.num(false, 0, 100),
      soilType: this.fb.nonNullable.control(''),
    }),
    climate: this.fb.group({
      temperatureC: this.num(true, -5, 55),
      humidityPct: this.num(true, 0, 100),
      rainfallMm: this.num(true, 0, 5000),
    }),
    season: this.fb.nonNullable.control(''),
    irrigationAvailable: this.fb.nonNullable.control(''),
    previousCrop: this.fb.nonNullable.control(''),
    location: this.fb.group({
      state: this.fb.nonNullable.control('', Validators.maxLength(60)),
      district: this.fb.nonNullable.control('', Validators.maxLength(60)),
    }),
  });

  invalid(path: string): boolean {
    const control = this.form.get(path);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  submit(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set({ message: this.i18n.t('validation.formInvalid'), reference: null });
      return;
    }
    this.pending.set(true);
    this.api
      .create(this.toRequest())
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: (result) => void this.router.navigate(['/recommendations', result.id], { state: { result } }),
        error: (err: unknown) => {
          const apiError = asApiError(err);
          if (apiError.code === 'VALIDATION_FAILED') {
            applyServerErrors(this.form, apiError.fieldErrors);
          }
          this.error.set(describeError(apiError, this.i18n));
        },
      });
  }

  /** Builds the API body; blank optional fields are sent as null. */
  toRequest(): CreateRecommendationRequest {
    const v = this.form.getRawValue();
    const state = v.location.state?.trim() || null;
    const district = v.location.district?.trim() || null;
    return {
      soil: {
        nitrogenKgHa: v.soil.nitrogenKgHa as number,
        phosphorusKgHa: v.soil.phosphorusKgHa as number,
        potassiumKgHa: v.soil.potassiumKgHa as number,
        ph: v.soil.ph as number,
        soilType: (v.soil.soilType || null) as SoilType | null,
        moisturePct: v.soil.moisturePct ?? null,
      },
      climate: {
        temperatureC: v.climate.temperatureC as number,
        humidityPct: v.climate.humidityPct as number,
        rainfallMm: v.climate.rainfallMm as number,
      },
      season: (v.season || null) as Season | null,
      irrigationAvailable: v.irrigationAvailable === '' ? null : v.irrigationAvailable === 'yes',
      previousCrop: v.previousCrop || null,
      location: state || district ? { state, district } : null,
    };
  }

  private num(required: boolean, min: number, max: number) {
    const validators = [Validators.min(min), Validators.max(max)];
    return this.fb.control<number | null>(null, required ? [Validators.required, ...validators] : validators);
  }
}
