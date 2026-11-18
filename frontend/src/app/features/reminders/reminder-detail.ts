import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { ReminderApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { Reminder } from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { NotificationCenter } from '../../core/notifications/notification-center';
import { ErrorBanner, Loading } from '../../shared/state-views';
import { FarmTimePipe, categoryIcon } from './reminder-utils';

type DetailAction = 'complete' | 'reopen' | 'cancel' | 'delete';

/** One reminder with every detail and all actions (/reminders/:id). */
@Component({
  selector: 'app-reminder-detail',
  imports: [RouterLink, TranslatePipe, FarmTimePipe, Loading, ErrorBanner],
  template: `
    <p><a routerLink="/reminders">← {{ 'reminders.backToList' | t }}</a></p>
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-banner [error]="error()" [retryable]="true" (retry)="load()" />
    } @else if (reminder(); as r) {
      <h1><span aria-hidden="true">{{ icon(r) }}</span> {{ r.title }}</h1>
      @if (message()) {
        <p class="state state-success" role="status" aria-live="polite">{{ message() }}</p>
      }
      <app-error-banner [error]="actionError()" />
      @if (r.overdue) {
        <p class="notice notice-overdue" role="status">{{ 'reminders.overdueNotice' | t }}</p>
      }
      <section class="card">
        <dl class="details">
          <div><dt>{{ 'reminders.fields.when' | t }}</dt>
            <dd><time [attr.datetime]="r.scheduledAt">{{ r.scheduledAt | farmTime: r.timeZone }}</time>
              <span class="muted small"> ({{ r.timeZone }})</span></dd></div>
          <div><dt>{{ 'reminders.fields.status' | t }}</dt>
            <dd><span [class]="'chip chip-status-' + r.status">{{ 'reminders.statuses.' + r.status | t }}</span>
              @if (r.overdue) { <span class="chip chip-overdue">{{ 'reminders.overdue' | t }}</span> }</dd></div>
          <div><dt>{{ 'reminders.fields.category' | t }}</dt><dd>{{ 'reminders.categories.' + r.category | t }}</dd></div>
          <div><dt>{{ 'reminders.fields.priority' | t }}</dt><dd>{{ 'reminders.priorities.' + r.priority | t }}</dd></div>
          <div><dt>{{ 'reminders.fields.crop' | t }}</dt><dd>{{ r.cropName ?? ('dashboard.notGiven' | t) }}</dd></div>
          <div><dt>{{ 'reminders.fields.description' | t }}</dt><dd class="pre-line">{{ r.description ?? ('dashboard.notGiven' | t) }}</dd></div>
          @if (r.completedAt) {
            <div><dt>{{ 'reminders.fields.completedAt' | t }}</dt><dd>{{ r.completedAt | farmTime: r.timeZone }}</dd></div>
          }
          @if (r.notifiedAt) {
            <div><dt>{{ 'reminders.fields.notifiedAt' | t }}</dt><dd>{{ r.notifiedAt | farmTime: r.timeZone }}</dd></div>
          }
          <div><dt>{{ 'reminders.fields.createdAt' | t }}</dt><dd>{{ r.createdAt | farmTime: r.timeZone }}</dd></div>
        </dl>

        @if (confirmingDelete()) {
          <div class="confirm-box" role="alert">
            <p>{{ 'reminders.confirmDelete' | t }}</p>
            <div class="actions">
              <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="confirmingDelete.set(false)">
                {{ 'common.cancel' | t }}
              </button>
              <button type="button" class="btn btn-danger" [disabled]="busy()" (click)="run('delete')">
                {{ 'reminders.confirmDeleteYes' | t }}
              </button>
            </div>
          </div>
        } @else {
          <div class="actions actions-wrap">
            @if (r.status === 'PENDING') {
              <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="run('complete')">
                ✓ {{ 'reminders.markDone' | t }}
              </button>
              <a class="btn btn-secondary" [routerLink]="['/reminders', r.id, 'edit']">{{ 'reminders.edit' | t }}</a>
              <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="run('cancel')">
                {{ 'reminders.cancelReminder' | t }}
              </button>
            } @else {
              <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="run('reopen')">
                {{ 'reminders.reopen' | t }}
              </button>
            }
            <button type="button" class="btn btn-ghost" [disabled]="busy()" (click)="confirmingDelete.set(true)">
              {{ 'common.delete' | t }}
            </button>
          </div>
        }
      </section>
    }
  `,
})
export class ReminderDetailPage implements OnInit {
  private readonly api = inject(ReminderApi);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationCenter);

  readonly id = input.required<string>();
  readonly reminder = signal<Reminder | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ErrorView | null>(null);
  readonly busy = signal(false);
  readonly message = signal<string | null>(null);
  readonly actionError = signal<ErrorView | null>(null);
  readonly confirmingDelete = signal(false);

  constructor() {
    const state = this.router.currentNavigation()?.extras.state as { saved?: boolean } | undefined;
    if (state?.saved) {
      this.message.set(this.i18n.t('reminders.saved'));
    }
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .get(this.id())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (r) => this.reminder.set(r),
        error: (err: unknown) => this.error.set(describeError(asApiError(err), this.i18n)),
      });
  }

  protected icon(r: Reminder): string {
    return categoryIcon(r.category);
  }

  run(action: DetailAction): void {
    const r = this.reminder();
    if (!r) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.actionError.set(null);
    if (action === 'delete') {
      this.api
        .delete(r.id)
        .pipe(finalize(() => this.busy.set(false)))
        .subscribe({
          next: () => {
            this.notifications.refresh();
            void this.router.navigate(['/reminders']);
          },
          error: (err: unknown) => this.actionError.set(describeError(asApiError(err), this.i18n)),
        });
      return;
    }
    const call: Observable<Reminder> =
      action === 'complete' ? this.api.complete(r.id) : action === 'cancel' ? this.api.cancel(r.id) : this.api.reopen(r.id);
    call.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (updated) => {
        this.reminder.set(updated);
        this.message.set(this.i18n.t('reminders.done.' + action, { title: updated.title }));
        this.notifications.refresh();
      },
      error: (err: unknown) => this.actionError.set(describeError(asApiError(err), this.i18n)),
    });
  }
}
