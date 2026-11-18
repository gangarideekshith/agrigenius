import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, finalize, map } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { ReminderApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import {
  REMINDER_CATEGORIES,
  REMINDER_STATUSES,
  Reminder,
  ReminderCategory,
  ReminderListFilter,
  ReminderStatus,
  ReminderSummary,
} from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { NotificationCenter } from '../../core/notifications/notification-center';
import { ErrorBanner, Loading } from '../../shared/state-views';
import { ReminderAction, ReminderItem } from './reminder-item';
import { browserTimeZone } from './reminder-utils';

export type ReminderView = 'overdue' | 'today' | 'upcoming' | 'pending' | 'completed' | 'all';
export const REMINDER_VIEWS: readonly ReminderView[] = ['overdue', 'today', 'upcoming', 'pending', 'completed', 'all'];
const PAGE_SIZE = 20;

/**
 * Reminders dashboard: counts, a view per section (overdue, today, upcoming, pending, completed, all)
 * and, for the list views, filters by category, date range, status and text. The selected view
 * is kept in the URL (?view=) so it can be linked to and survives reloads.
 */
@Component({
  selector: 'app-reminders',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, Loading, ErrorBanner, ReminderItem],
  template: `
    <div class="page-head">
      <h1>{{ 'reminders.title' | t }}</h1>
      <a class="btn btn-primary" routerLink="/reminders/new">＋ {{ 'reminders.new' | t }}</a>
    </div>
    <p class="muted">{{ 'reminders.intro' | t }}</p>

    <section [attr.aria-label]="'reminders.statsTitle' | t">
      @if (summaryError()) {
        <p class="state state-error" role="alert">{{ 'reminders.statsError' | t }}</p>
      } @else if (summary(); as s) {
        <div class="stat-grid">
          <button type="button" class="stat stat-overdue" [class.active]="view() === 'overdue'" (click)="select('overdue')">
            <strong>{{ s.overdue }}</strong><span>{{ 'reminders.views.overdue' | t }}</span>
          </button>
          <button type="button" class="stat" [class.active]="view() === 'today'" (click)="select('today')">
            <strong>{{ s.dueToday }}</strong><span>{{ 'reminders.stats.dueToday' | t }}</span>
          </button>
          <button type="button" class="stat" [class.active]="view() === 'upcoming'" (click)="select('upcoming')">
            <strong>{{ s.upcoming }}</strong><span>{{ 'reminders.stats.upcoming' | t: { days: s.upcomingDays } }}</span>
          </button>
          <button type="button" class="stat" [class.active]="view() === 'pending'" (click)="select('pending')">
            <strong>{{ s.pending }}</strong><span>{{ 'reminders.views.pending' | t }}</span>
          </button>
          <button type="button" class="stat" [class.active]="view() === 'completed'" (click)="select('completed')">
            <strong>{{ s.completed }}</strong><span>{{ 'reminders.views.completed' | t }}</span>
          </button>
        </div>
      }
    </section>

    <nav class="tabs" [attr.aria-label]="'reminders.viewsLabel' | t">
      @for (v of views; track v) {
        <button type="button" class="tab" [class.active]="view() === v" [attr.aria-pressed]="view() === v" (click)="select(v)">
          {{ 'reminders.views.' + v | t }}
        </button>
      }
    </nav>

    @if (isListView()) {
      <form class="card filters" [formGroup]="filters" (ngSubmit)="applyFilters()" role="search">
        <div class="field">
          <label for="rf-q">{{ 'reminders.filters.search' | t }}</label>
          <input id="rf-q" type="search" formControlName="q" maxlength="100" />
        </div>
        <div class="field-row">
          <div class="field">
            <label for="rf-category">{{ 'reminders.fields.category' | t }}</label>
            <select id="rf-category" formControlName="category">
              <option value="">{{ 'reminders.filters.any' | t }}</option>
              @for (c of categories; track c) {
                <option [value]="c">{{ 'reminders.categories.' + c | t }}</option>
              }
            </select>
          </div>
          @if (view() === 'all') {
            <div class="field">
              <label for="rf-status">{{ 'reminders.fields.status' | t }}</label>
              <select id="rf-status" formControlName="status">
                <option value="">{{ 'reminders.filters.any' | t }}</option>
                @for (s of statuses; track s) {
                  <option [value]="s">{{ 'reminders.statuses.' + s | t }}</option>
                }
              </select>
            </div>
          }
        </div>
        <div class="field-row">
          <div class="field">
            <label for="rf-from">{{ 'reminders.filters.from' | t }}</label>
            <input id="rf-from" type="date" formControlName="from" />
          </div>
          <div class="field">
            <label for="rf-to">{{ 'reminders.filters.to' | t }}</label>
            <input id="rf-to" type="date" formControlName="to" />
          </div>
        </div>
        @if (rangeInvalid()) {
          <p class="field-error" role="alert">{{ 'errors.INVALID_DATE_RANGE' | t }}</p>
        }
        <div class="actions">
          <button type="button" class="btn btn-secondary" (click)="clearFilters()">{{ 'reminders.filters.clear' | t }}</button>
          <button type="submit" class="btn btn-primary">{{ 'reminders.filters.apply' | t }}</button>
        </div>
      </form>
    }

    @if (actionMessage()) {
      <p class="state state-success" role="status" aria-live="polite">{{ actionMessage() }}</p>
    }
    <app-error-banner [error]="actionError()" />

    <section aria-live="polite" [attr.aria-busy]="loading()">
      <h2 class="visually-hidden">{{ 'reminders.views.' + view() | t }}</h2>
      @if (loading()) {
        <app-loading />
      } @else if (error()) {
        <app-error-banner [error]="error()" [retryable]="true" (retry)="load()" />
      } @else if (items().length === 0) {
        <div class="state state-empty">
          <p>{{ 'reminders.empty.' + view() | t }}</p>
          <a class="btn btn-secondary" routerLink="/reminders/new">{{ 'reminders.new' | t }}</a>
        </div>
      } @else {
        <ul class="reminder-list">
          @for (r of items(); track r.id) {
            <li><app-reminder-item [reminder]="r" [busy]="busyId() === r.id" (action)="onAction(r, $event)" /></li>
          }
        </ul>
        @if (totalPages() > 1) {
          <nav class="pager" [attr.aria-label]="'common.pagination' | t">
            <button type="button" class="btn btn-secondary" [disabled]="page() === 0" (click)="load(page() - 1)">
              {{ 'common.previous' | t }}
            </button>
            <span>{{ 'common.page' | t: { page: page() + 1, total: totalPages() } }}</span>
            <button type="button" class="btn btn-secondary" [disabled]="page() + 1 >= totalPages()" (click)="load(page() + 1)">
              {{ 'common.next' | t }}
            </button>
          </nav>
        }
      }
    </section>
  `,
})
export class RemindersPage implements OnInit {
  private readonly api = inject(ReminderApi);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationCenter);
  private readonly timeZone = browserTimeZone();

  protected readonly views = REMINDER_VIEWS;
  protected readonly categories = REMINDER_CATEGORIES;
  protected readonly statuses = REMINDER_STATUSES;

  readonly view = signal<ReminderView>('overdue');
  readonly summary = signal<ReminderSummary | null>(null);
  readonly summaryError = signal(false);
  readonly items = signal<Reminder[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ErrorView | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly busyId = signal<string | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly actionError = signal<ErrorView | null>(null);
  readonly rangeInvalid = signal(false);

  readonly filters = inject(FormBuilder).nonNullable.group({
    q: [''],
    category: ['' as ReminderCategory | ''],
    status: ['' as ReminderStatus | ''],
    from: [''],
    to: [''],
  });
  private appliedFilter: ReminderListFilter = {};

  ngOnInit(): void {
    const requested = this.route.snapshot.queryParamMap.get('view');
    if (requested && (REMINDER_VIEWS as readonly string[]).includes(requested)) {
      this.view.set(requested as ReminderView);
    }
    this.loadSummary();
    this.load();
  }

  isListView(): boolean {
    return ['pending', 'completed', 'all'].includes(this.view());
  }

  select(view: ReminderView): void {
    if (view === this.view()) {
      return;
    }
    this.view.set(view);
    this.actionMessage.set(null);
    this.actionError.set(null);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { view }, replaceUrl: true });
    this.load();
  }

  applyFilters(): void {
    const v = this.filters.getRawValue();
    this.rangeInvalid.set(!!v.from && !!v.to && v.from > v.to);
    if (this.rangeInvalid()) {
      return;
    }
    this.appliedFilter = { q: v.q, category: v.category || null, status: v.status || null, from: v.from, to: v.to };
    this.load();
  }

  clearFilters(): void {
    this.filters.reset();
    this.rangeInvalid.set(false);
    this.appliedFilter = {};
    this.load();
  }

  loadSummary(): void {
    this.summaryError.set(false);
    this.api.summary(this.timeZone).subscribe({
      next: (s) => this.summary.set(s),
      error: () => this.summaryError.set(true),
    });
  }

  load(page = 0): void {
    this.loading.set(true);
    this.error.set(null);
    this.request(page)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (result) => {
          this.items.set(result.items);
          this.page.set(result.page);
          this.totalPages.set(result.totalPages);
        },
        error: (err: unknown) => this.error.set(describeError(asApiError(err), this.i18n)),
      });
  }

  onAction(reminder: Reminder, action: ReminderAction): void {
    this.busyId.set(reminder.id);
    this.actionMessage.set(null);
    this.actionError.set(null);
    const call: Observable<unknown> =
      action === 'complete'
        ? this.api.complete(reminder.id)
        : action === 'reopen'
          ? this.api.reopen(reminder.id)
          : this.api.delete(reminder.id);
    call.pipe(finalize(() => this.busyId.set(null))).subscribe({
      next: () => {
        this.actionMessage.set(this.i18n.t('reminders.done.' + action, { title: reminder.title }));
        this.loadSummary();
        this.load(this.page());
        this.notifications.refresh();
      },
      error: (err: unknown) => this.actionError.set(describeError(asApiError(err), this.i18n)),
    });
  }

  private request(page: number): Observable<{ items: Reminder[]; page: number; totalPages: number }> {
    const whole = (items: Reminder[]) => ({ items, page: 0, totalPages: 1 });
    switch (this.view()) {
      case 'overdue':
        return this.api.overdue().pipe(map(whole));
      case 'today':
        return this.api.today(this.timeZone).pipe(map(whole));
      case 'upcoming':
        return this.api.upcoming().pipe(map(whole));
      case 'pending':
        return this.api.list({ ...this.appliedFilter, status: 'PENDING' }, this.timeZone, page, PAGE_SIZE);
      case 'completed':
        return this.api.list({ ...this.appliedFilter, status: 'COMPLETED' }, this.timeZone, page, PAGE_SIZE);
      default:
        return this.api.list(this.appliedFilter, this.timeZone, page, PAGE_SIZE);
    }
  }
}
