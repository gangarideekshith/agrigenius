import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { asApiError } from '../../core/api/api-error';
import { FarmerApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { FarmerProfile } from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { ErrorBanner, Loading } from '../../shared/state-views';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, TranslatePipe, Loading, ErrorBanner],
  template: `
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-banner [error]="error()" [retryable]="true" (retry)="load()" />
    } @else if (profile(); as p) {
      <h1>{{ 'dashboard.greeting' | t: { name: p.fullName } }}</h1>

      <section class="card" aria-labelledby="profile-title">
        <h2 id="profile-title">{{ 'dashboard.profileTitle' | t }}</h2>
        <dl class="details">
          <div><dt>{{ 'dashboard.mobile' | t }}</dt><dd>{{ p.mobileNumber }}</dd></div>
          <div><dt>{{ 'dashboard.email' | t }}</dt><dd>{{ p.email ?? ('dashboard.notGiven' | t) }}</dd></div>
          <div><dt>{{ 'dashboard.location' | t }}</dt><dd>{{ location(p) || ('dashboard.notGiven' | t) }}</dd></div>
          <div><dt>{{ 'dashboard.language' | t }}</dt><dd>{{ 'languages.' + p.preferredLanguage | t }}</dd></div>
          <div><dt>{{ 'dashboard.memberSince' | t }}</dt><dd>{{ p.createdAt | date: 'mediumDate' }}</dd></div>
        </dl>
      </section>

      <h2>{{ 'dashboard.shortcutsTitle' | t }}</h2>
      <div class="tiles">
        <a class="tile" routerLink="/recommendations/new">
          <span class="tile-icon" aria-hidden="true">🌾</span>
          <strong>{{ 'dashboard.newRecTitle' | t }}</strong>
          <span>{{ 'dashboard.newRecText' | t }}</span>
        </a>
        <a class="tile" routerLink="/recommendations">
          <span class="tile-icon" aria-hidden="true">📋</span>
          <strong>{{ 'dashboard.historyTitle' | t }}</strong>
          <span>{{ 'dashboard.historyText' | t }}</span>
        </a>
        <a class="tile" routerLink="/chat">
          <span class="tile-icon" aria-hidden="true">💬</span>
          <strong>{{ 'dashboard.chatTitle' | t }}</strong>
          <span>{{ 'dashboard.chatText' | t }}</span>
        </a>
      </div>
    }
  `,
})
export class DashboardPage implements OnInit {
  private readonly farmers = inject(FarmerApi);
  private readonly i18n = inject(I18nService);

  readonly profile = signal<FarmerProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ErrorView | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.farmers.me().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.i18n.applyProfilePreference(p.preferredLanguage);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(describeError(asApiError(err), this.i18n));
        this.loading.set(false);
      },
    });
  }

  location(p: FarmerProfile): string {
    return [p.village, p.district, p.state].filter((part): part is string => !!part).join(', ');
  }
}
