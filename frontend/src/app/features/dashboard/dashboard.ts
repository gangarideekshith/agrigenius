import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { FarmerApi, RecommendationApi, ReminderApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { FarmerProfile, RecommendationSummary, Reminder, ReminderSummary } from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { ErrorBanner, Loading } from '../../shared/state-views';
import { FarmTimePipe, browserTimeZone } from '../reminders/reminder-utils';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, TranslatePipe, Loading, ErrorBanner, FarmTimePipe],
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
        <a class="btn btn-secondary" routerLink="/profile">{{ 'dashboard.editProfile' | t }}</a>
      </section>

      <section class="card" aria-labelledby="reminders-title">
        <h2 id="reminders-title">{{ 'dashboard.remindersTitle' | t }}</h2>
        @if (remindersLoading()) {
          <app-loading />
        } @else if (remindersError()) {
          <p class="state state-error" role="alert">{{ 'dashboard.remindersError' | t }}</p>
        } @else if (reminderSummary(); as s) {
          <p>
            @if (s.overdue > 0) {
              <a class="chip chip-overdue" routerLink="/reminders" [queryParams]="{ view: 'overdue' }">
                {{ 'dashboard.remindersOverdue' | t: { count: s.overdue } }}
              </a>
            }
            <a class="chip" routerLink="/reminders" [queryParams]="{ view: 'today' }">{{ 'dashboard.remindersToday' | t: { count: s.dueToday } }}</a>
            <a class="chip" routerLink="/reminders" [queryParams]="{ view: 'upcoming' }">
              {{ 'dashboard.remindersUpcoming' | t: { count: s.upcoming, days: s.upcomingDays } }}
            </a>
          </p>
          @if (todayReminders().length > 0) {
            <ul class="compact-list">
              @for (r of todayReminders(); track r.id) {
                <li [class.is-done]="r.status !== 'PENDING'">
                  <a [routerLink]="['/reminders', r.id]">{{ r.title }}</a>
                  <span class="muted small"> · {{ r.scheduledAt | farmTime: r.timeZone }}</span>
                  @if (r.status === 'COMPLETED') { <span class="small"> · ✓ {{ 'reminders.statuses.COMPLETED' | t }}</span> }
                  @else if (r.overdue) { <span class="chip chip-overdue">{{ 'reminders.overdue' | t }}</span> }
                </li>
              }
            </ul>
          } @else {
            <p class="state state-empty">{{ 'dashboard.remindersNoneToday' | t }}</p>
          }
          <div class="actions actions-start">
            <a class="btn btn-primary" routerLink="/reminders/new">{{ 'reminders.new' | t }}</a>
            <a class="btn btn-secondary" routerLink="/reminders">{{ 'dashboard.remindersOpen' | t }}</a>
          </div>
        }
      </section>

      <section class="card" aria-labelledby="latest-rec-title">
        <h2 id="latest-rec-title">{{ 'dashboard.latestRecTitle' | t }}</h2>
        @if (latestLoading()) {
          <app-loading />
        } @else if (latestError()) {
          <p class="state state-error" role="alert">{{ 'dashboard.latestRecError' | t }}</p>
        } @else if (latest(); as r) {
          <p>
            <strong>{{ r.topCrop?.name ?? ('rec.statusLabel' + r.status | t) }}</strong>
            @if (r.topConfidence) { · {{ 'rec.confidence' + r.topConfidence | t }} }
            · {{ r.requestedAt | date: 'medium' }}
          </p>
          <a class="btn btn-secondary" [routerLink]="['/recommendations', r.id]">{{ 'dashboard.latestRecOpen' | t }}</a>
        } @else {
          <p class="state state-empty">{{ 'dashboard.latestRecEmpty' | t }}</p>
        }
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
        <a class="tile" routerLink="/reminders">
          <span class="tile-icon" aria-hidden="true">⏰</span>
          <strong>{{ 'dashboard.remindersTileTitle' | t }}</strong>
          <span>{{ 'dashboard.remindersTileText' | t }}</span>
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
  private readonly recommendations = inject(RecommendationApi);
  private readonly i18n = inject(I18nService);
  private readonly reminders = inject(ReminderApi);

  readonly profile = signal<FarmerProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ErrorView | null>(null);
  readonly latest = signal<RecommendationSummary | null>(null);
  readonly latestLoading = signal(true);
  readonly latestError = signal(false);
  readonly reminderSummary = signal<ReminderSummary | null>(null);
  readonly todayReminders = signal<Reminder[]>([]);
  readonly remindersLoading = signal(true);
  readonly remindersError = signal(false);

  ngOnInit(): void {
    this.load();
    this.loadLatest();
    this.loadReminders();
  }

  /** Reminder counts plus today's reminders (real reminder APIs, browser time zone). */
  loadReminders(): void {
    const timeZone = browserTimeZone();
    this.remindersLoading.set(true);
    this.remindersError.set(false);
    forkJoin({ summary: this.reminders.summary(timeZone), today: this.reminders.today(timeZone) }).subscribe({
      next: ({ summary, today }) => {
        this.reminderSummary.set(summary);
        this.todayReminders.set(today.slice(0, 5));
        this.remindersLoading.set(false);
      },
      error: () => {
        this.remindersError.set(true);
        this.remindersLoading.set(false);
      },
    });
  }

  /** Most recent crop recommendation (real history API, page size 1). */
  loadLatest(): void {
    this.latestLoading.set(true);
    this.latestError.set(false);
    this.recommendations.history(0, 1).subscribe({
      next: (page) => {
        this.latest.set(page.items[0] ?? null);
        this.latestLoading.set(false);
      },
      error: () => {
        this.latestError.set(true);
        this.latestLoading.set(false);
      },
    });
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
