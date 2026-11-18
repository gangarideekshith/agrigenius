import { Component, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Reminder } from '../../core/api/models';
import { TranslatePipe } from '../../core/i18n/i18n.service';
import { FarmTimePipe, categoryIcon } from './reminder-utils';

export type ReminderAction = 'complete' | 'reopen' | 'delete';

/**
 * One reminder in a list, with its quick actions. Deleting asks for confirmation inline.
 * The parent performs the API call and sets {@code busy} while it runs.
 */
@Component({
  selector: 'app-reminder-item',
  imports: [RouterLink, TranslatePipe, FarmTimePipe],
  template: `
    @let r = reminder();
    <article class="reminder-item" [class.is-overdue]="r.overdue" [class.is-done]="r.status !== 'PENDING'">
      <div class="reminder-main">
        <span class="reminder-icon" aria-hidden="true">{{ icon(r) }}</span>
        <div>
          <h3 class="reminder-title">
            <a [routerLink]="['/reminders', r.id]">{{ r.title }}</a>
          </h3>
          <p class="reminder-meta">
            <span>{{ 'reminders.categories.' + r.category | t }}</span>
            @if (r.cropName) { · <span>{{ r.cropName }}</span> }
            · <time [attr.datetime]="r.scheduledAt">{{ r.scheduledAt | farmTime: r.timeZone }}</time>
          </p>
          <p class="reminder-badges">
            @if (r.overdue) {
              <span class="chip chip-overdue">{{ 'reminders.overdue' | t }}</span>
            }
            @if (r.status !== 'PENDING') {
              <span [class]="'chip chip-status-' + r.status">{{ 'reminders.statuses.' + r.status | t }}</span>
            }
            <span [class]="'chip chip-priority-' + r.priority">
              {{ 'reminders.priorityLabel' | t: { priority: ('reminders.priorities.' + r.priority | t) } }}
            </span>
          </p>
        </div>
      </div>
      <div class="reminder-actions">
        @if (confirming()) {
          <p class="confirm-text" role="alert">{{ 'reminders.confirmDelete' | t }}</p>
          <button type="button" class="btn btn-danger" [disabled]="busy()" (click)="act('delete')">
            {{ 'reminders.confirmDeleteYes' | t }}
          </button>
          <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="confirming.set(false)">
            {{ 'common.cancel' | t }}
          </button>
        } @else {
          @if (r.status === 'PENDING') {
            <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="act('complete')">
              ✓ {{ 'reminders.markDone' | t }}
            </button>
            <a class="btn btn-secondary" [routerLink]="['/reminders', r.id, 'edit']">{{ 'reminders.edit' | t }}</a>
          } @else {
            <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="act('reopen')">
              {{ 'reminders.reopen' | t }}
            </button>
          }
          <button type="button" class="btn btn-ghost" [disabled]="busy()" (click)="confirming.set(true)"
                  [attr.aria-label]="('common.delete' | t) + ': ' + r.title">
            {{ 'common.delete' | t }}
          </button>
        }
      </div>
    </article>
  `,
})
export class ReminderItem {
  readonly reminder = input.required<Reminder>();
  readonly busy = input(false);
  readonly action = output<ReminderAction>();
  readonly confirming = signal(false);

  protected icon(r: Reminder): string {
    return categoryIcon(r.category);
  }

  protected act(action: ReminderAction): void {
    this.confirming.set(false);
    this.action.emit(action);
  }
}
