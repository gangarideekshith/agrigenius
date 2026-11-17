import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { asApiError } from '../../core/api/api-error';
import { RecommendationApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { Page, RecommendationSummary } from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { ErrorBanner, Loading } from '../../shared/state-views';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-recommendation-history',
  imports: [RouterLink, DatePipe, TranslatePipe, Loading, ErrorBanner],
  template: `
    <h1>{{ 'rec.historyTitle' | t }}</h1>
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-banner [error]="error()" [retryable]="true" (retry)="load(pageIndex())" />
    } @else if (page(); as p) {
      @if (p.items.length === 0) {
        <div class="state state-empty">
          <p>{{ 'rec.historyEmpty' | t }}</p>
          <a class="btn btn-primary" routerLink="/recommendations/new">{{ 'rec.historyEmptyAction' | t }}</a>
        </div>
      } @else {
        <ul class="history-list">
          @for (item of p.items; track item.id) {
            <li class="card history-item">
              <div>
                <p class="small muted">{{ item.requestedAt | date: 'medium' }}</p>
                <p class="crop-name">{{ topCrop(item) }}</p>
                <p class="small">
                  <span class="chip" [class]="'chip-status-' + item.status">{{ 'rec.colStatus' | t }}: {{ statusLabel(item) }}</span>
                  @if (item.topConfidence) {
                    <span class="chip" [class]="'chip-' + item.topConfidence">{{ 'rec.confidence' + item.topConfidence | t }}</span>
                  }
                </p>
              </div>
              <a class="btn btn-secondary" [routerLink]="['/recommendations', item.id]">{{ 'common.view' | t }}</a>
            </li>
          }
        </ul>
        @if (p.totalPages > 1) {
          <nav class="pager" [attr.aria-label]="'common.pagination' | t">
            <button type="button" class="btn btn-secondary" [disabled]="p.page === 0" (click)="load(p.page - 1)">
              {{ 'common.previous' | t }}
            </button>
            <span aria-live="polite">{{ 'common.page' | t: { page: p.page + 1, total: p.totalPages } }}</span>
            <button type="button" class="btn btn-secondary" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">
              {{ 'common.next' | t }}
            </button>
          </nav>
        }
      }
    }
  `,
})
export class RecommendationHistoryPage implements OnInit {
  private readonly api = inject(RecommendationApi);
  private readonly i18n = inject(I18nService);

  readonly page = signal<Page<RecommendationSummary> | null>(null);
  readonly pageIndex = signal(0);
  readonly loading = signal(true);
  readonly error = signal<ErrorView | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  load(index: number): void {
    this.pageIndex.set(index);
    this.loading.set(true);
    this.error.set(null);
    this.api.history(index, PAGE_SIZE).subscribe({
      next: (p) => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(describeError(asApiError(err), this.i18n));
        this.loading.set(false);
      },
    });
  }

  topCrop(item: RecommendationSummary): string {
    if (!item.topCrop) {
      return this.i18n.t('rec.noTopCrop');
    }
    const key = `crops.${item.topCrop.code}`;
    return this.i18n.has(key) ? this.i18n.t(key) : item.topCrop.name;
  }

  statusLabel(item: RecommendationSummary): string {
    return this.i18n.t(`rec.statusLabel${item.status}`);
  }
}
