import { DatePipe, DecimalPipe, Location, PercentPipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { asApiError } from '../../core/api/api-error';
import { RecommendationApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { RecommendationCandidate, RecommendationResponse } from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { ErrorBanner, Loading } from '../../shared/state-views';

@Component({
  selector: 'app-recommendation-result',
  imports: [RouterLink, DatePipe, DecimalPipe, PercentPipe, TranslatePipe, Loading, ErrorBanner],
  template: `
    <h1>{{ 'rec.resultTitle' | t }}</h1>
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-banner [error]="error()" [retryable]="true" (retry)="load()" />
    } @else if (result(); as r) {
      <p class="muted">{{ 'rec.requestedAt' | t: { date: (r.requestedAt | date: 'medium') } }}</p>

      <div class="status-banner" [class]="'status-' + r.status" role="status" aria-live="polite">
        {{ 'rec.status' + r.status | t }}
      </div>

      @if (r.model?.dataLabel === 'DEMO_PUBLIC_DATASET') {
        <p class="badge badge-demo">{{ 'rec.demoLabel' | t }}</p>
      }

      @if (r.warnings.length) {
        <section class="card card-warning" aria-labelledby="warn-title">
          <h2 id="warn-title">{{ 'rec.warningsTitle' | t }}</h2>
          <ul>
            @for (w of r.warnings; track $index) {
              <li>{{ warningText(w.code, w.feature) }}</li>
            }
          </ul>
        </section>
      }

      @if (r.advisory) {
        <section class="card card-advisory" aria-labelledby="advisory-title">
          <h2 id="advisory-title">{{ 'rec.advisoryTitle' | t }}</h2>
          <p>{{ r.advisory }}</p>
        </section>
      }

      <ol class="candidates">
        @for (c of r.candidates; track c.rank) {
          <li class="card candidate">
            <div class="candidate-head">
              <span class="rank">{{ 'rec.rank' | t: { rank: c.rank } }}</span>
              <h2 class="crop-name">{{ cropName(c) }}</h2>
              <span class="chip" [class]="'chip-' + c.confidence">{{ 'rec.confidence' + c.confidence | t }}</span>
            </div>
            <div class="meter" role="meter" aria-valuemin="0" aria-valuemax="100" [attr.aria-valuenow]="c.suitabilityScore"
                 [attr.aria-label]="'rec.suitability' | t: { score: c.suitabilityScore }">
              <span class="meter-fill" [style.width.%]="c.suitabilityScore"></span>
            </div>
            <p class="small">{{ 'rec.suitability' | t: { score: c.suitabilityScore } }} · {{ c.probability | percent: '1.0-1' }}</p>

            @if (c.explanation.length) {
              <h3 class="small-heading">{{ 'rec.whyTitle' | t }}</h3>
              <ul class="explanation">
                @for (e of c.explanation; track e.feature) {
                  <li [class]="'fit-' + e.fit">
                    <strong>{{ 'features.' + e.feature | t }}</strong>: {{ e.value | number: '1.0-2' }} —
                    {{ 'rec.fit' + e.fit | t: { min: (e.typicalRange[0] | number: '1.0-1'), max: (e.typicalRange[1] | number: '1.0-1') } }}
                  </li>
                }
              </ul>
            }
            @if (!c.economics.available) {
              <p class="muted small">{{ 'rec.economicsNotAvailable' | t }}</p>
            }
          </li>
        }
      </ol>

      @if (r.model) {
        <p class="muted small">{{ 'rec.modelInfo' | t: { name: r.model.name, version: r.model.version } }}</p>
      }
      <div class="actions">
        <a class="btn btn-primary" routerLink="/recommendations/new">{{ 'rec.askAgain' | t }}</a>
        <a class="btn btn-secondary" routerLink="/recommendations">{{ 'rec.toHistory' | t }}</a>
      </div>
    }
  `,
})
export class RecommendationResultPage implements OnInit {
  private readonly api = inject(RecommendationApi);
  private readonly i18n = inject(I18nService);
  private readonly location = inject(Location);

  /** Route parameter, bound by the router. */
  readonly id = input.required<string>();

  readonly result = signal<RecommendationResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ErrorView | null>(null);

  ngOnInit(): void {
    // A fresh result is handed over in navigation state; otherwise (reload, history) fetch it.
    const state = this.location.getState() as { result?: RecommendationResponse } | null;
    if (state?.result && state.result.id === this.id()) {
      this.result.set(state.result);
      this.loading.set(false);
      return;
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.get(this.id()).subscribe({
      next: (r) => {
        this.result.set(r);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(describeError(asApiError(err), this.i18n));
        this.loading.set(false);
      },
    });
  }

  cropName(c: RecommendationCandidate): string {
    const key = `crops.${c.crop.code}`;
    return this.i18n.has(key) ? this.i18n.t(key) : c.crop.name;
  }

  warningText(code: string, feature: string | null): string {
    const key = `rec.warning${code}`;
    if (!this.i18n.has(key)) {
      return this.i18n.t('rec.warningUnknown', { code });
    }
    return this.i18n.t(key, { feature: feature ? this.i18n.t(`features.${feature}`) : '' });
  }
}
