import { Component, OnInit, inject, input, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { ReminderApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import {
  REMINDER_CATEGORIES,
  REMINDER_PRIORITIES,
  Reminder,
  ReminderCategory,
  ReminderPriority,
  ReminderRequest,
} from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { applyServerErrors } from '../../core/validation/validators';
import { FieldError } from '../../shared/field-error';
import { ErrorBanner, Loading } from '../../shared/state-views';
import { browserTimeZone, isInPast, nowInZone } from './reminder-utils';

/**
 * Create (/reminders/new) or edit (/reminders/:id/edit) a reminder. The date and time are the
 * farmer's local time; new reminders use the browser's time zone, edits keep the reminder's zone.
 */
@Component({
  selector: 'app-reminder-form',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldError, ErrorBanner, Loading],
  template: `
    <h1>{{ (id() ? 'reminders.editTitle' : 'reminders.newTitle') | t }}</h1>
    @if (loading()) {
      <app-loading />
    } @else if (loadError()) {
      <app-error-banner [error]="loadError()" [retryable]="true" (retry)="load()" />
    } @else if (notEditable()) {
      <div class="state state-empty">
        <p>{{ 'errors.REMINDER_NOT_EDITABLE' | t }}</p>
        <a class="btn btn-secondary" [routerLink]="['/reminders', id()]">{{ 'reminders.backToReminder' | t }}</a>
      </div>
    } @else {
      <section class="card">
        <app-error-banner [error]="saveError()" />
        <form [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="field">
            <label for="rm-title">{{ 'reminders.fields.title' | t }}</label>
            <input id="rm-title" formControlName="title" maxlength="120" autocomplete="off"
                   [placeholder]="'reminders.titlePlaceholder' | t"
                   aria-describedby="rm-title-error" [attr.aria-invalid]="invalid('title')" />
            <app-field-error [control]="form.controls.title" errorId="rm-title-error" />
          </div>
          <div class="field-row">
            <div class="field">
              <label for="rm-category">{{ 'reminders.fields.category' | t }}</label>
              <select id="rm-category" formControlName="category" aria-describedby="rm-category-error"
                      [attr.aria-invalid]="invalid('category')">
                <option value="" disabled>{{ 'common.select' | t }}</option>
                @for (c of categories; track c) {
                  <option [value]="c">{{ 'reminders.categories.' + c | t }}</option>
                }
              </select>
              <app-field-error [control]="form.controls.category" errorId="rm-category-error" />
            </div>
            <div class="field">
              <label for="rm-priority">{{ 'reminders.fields.priority' | t }}</label>
              <select id="rm-priority" formControlName="priority">
                @for (p of priorities; track p) {
                  <option [value]="p">{{ 'reminders.priorities.' + p | t }}</option>
                }
              </select>
            </div>
          </div>
          <div class="field">
            <label for="rm-crop">{{ 'reminders.fields.crop' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
            <input id="rm-crop" formControlName="cropName" maxlength="60" [placeholder]="'reminders.cropPlaceholder' | t"
                   aria-describedby="rm-crop-error" />
            <app-field-error [control]="form.controls.cropName" errorId="rm-crop-error" />
          </div>
          <div class="field-row">
            <div class="field">
              <label for="rm-date">{{ 'reminders.fields.date' | t }}</label>
              <input id="rm-date" type="date" formControlName="scheduledDate" [min]="minDate"
                     aria-describedby="rm-date-error" [attr.aria-invalid]="invalid('scheduledDate')" />
              <app-field-error [control]="form.controls.scheduledDate" errorId="rm-date-error" />
            </div>
            <div class="field">
              <label for="rm-time">{{ 'reminders.fields.time' | t }}</label>
              <input id="rm-time" type="time" formControlName="scheduledTime"
                     aria-describedby="rm-time-error rm-time-help" [attr.aria-invalid]="invalid('scheduledTime')" />
              <app-field-error [control]="form.controls.scheduledTime" errorId="rm-time-error" />
            </div>
          </div>
          <p id="rm-time-help" class="help">{{ 'reminders.timeZoneHelp' | t: { zone: timeZone } }}</p>
          @if (pastError()) {
            <p class="field-error" role="alert">{{ 'errors.REMINDER_IN_PAST' | t }}</p>
          }
          <div class="field">
            <label for="rm-description">{{ 'reminders.fields.description' | t }} <span class="muted">({{ 'common.optional' | t }})</span></label>
            <textarea id="rm-description" rows="3" formControlName="description" maxlength="1000"
                      aria-describedby="rm-description-error"></textarea>
            <app-field-error [control]="form.controls.description" errorId="rm-description-error" />
          </div>
          <div class="actions">
            <a class="btn btn-secondary" [routerLink]="id() ? ['/reminders', id()] : ['/reminders']">{{ 'common.cancel' | t }}</a>
            <button type="submit" class="btn btn-primary" [disabled]="pending()">
              {{ (pending() ? 'reminders.saving' : 'reminders.save') | t }}
            </button>
          </div>
        </form>
      </section>
    }
  `,
})
export class ReminderFormPage implements OnInit {
  private readonly api = inject(ReminderApi);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  /** Route parameter (component input binding); absent when creating. */
  readonly id = input<string | undefined>(undefined);

  protected readonly categories = REMINDER_CATEGORIES;
  protected readonly priorities = REMINDER_PRIORITIES;
  protected timeZone = browserTimeZone();
  protected minDate = nowInZone(this.timeZone).date;

  readonly loading = signal(false);
  readonly loadError = signal<ErrorView | null>(null);
  readonly notEditable = signal(false);
  readonly saveError = signal<ErrorView | null>(null);
  readonly pending = signal(false);
  readonly pastError = signal(false);
  private original: Reminder | null = null;

  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(120), notBlank]],
    category: ['' as ReminderCategory | '', Validators.required],
    priority: ['MEDIUM' as ReminderPriority, Validators.required],
    cropName: ['', Validators.maxLength(60)],
    scheduledDate: ['', Validators.required],
    scheduledTime: ['', Validators.required],
    description: ['', Validators.maxLength(1000)],
  });

  ngOnInit(): void {
    if (this.id()) {
      this.load();
    } else {
      // Sensible default: tomorrow at 7:00 local time.
      const tomorrow = new Date(`${nowInZone(this.timeZone).date}T00:00:00`);
      tomorrow.setDate(tomorrow.getDate() + 1);
      const iso = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`;
      this.form.patchValue({ scheduledDate: iso, scheduledTime: '07:00' });
    }
  }

  load(): void {
    const id = this.id();
    if (!id) {
      return;
    }
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .get(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (r) => {
          this.original = r;
          this.notEditable.set(r.status !== 'PENDING');
          this.timeZone = r.timeZone;
          this.minDate = '';
          this.form.reset({
            title: r.title,
            category: r.category,
            priority: r.priority,
            cropName: r.cropName ?? '',
            scheduledDate: r.scheduledDate,
            scheduledTime: r.scheduledTime.slice(0, 5),
            description: r.description ?? '',
          });
        },
        error: (err: unknown) => this.loadError.set(describeError(asApiError(err), this.i18n)),
      });
  }

  invalid(name: 'title' | 'category' | 'scheduledDate' | 'scheduledTime'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  save(): void {
    this.saveError.set(null);
    this.pastError.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.saveError.set({ message: this.i18n.t('validation.formInvalid'), reference: null });
      return;
    }
    const v = this.form.getRawValue();
    const timeChanged =
      !this.original ||
      this.original.scheduledDate !== v.scheduledDate ||
      this.original.scheduledTime.slice(0, 5) !== v.scheduledTime;
    if (timeChanged && isInPast(v.scheduledDate, v.scheduledTime, this.timeZone)) {
      this.pastError.set(true);
      this.form.controls.scheduledTime.markAsTouched();
      return;
    }
    const request: ReminderRequest = {
      title: v.title.trim(),
      description: v.description.trim() || null,
      category: v.category as ReminderCategory,
      cropName: v.cropName.trim() || null,
      scheduledDate: v.scheduledDate,
      scheduledTime: v.scheduledTime.slice(0, 5),
      timeZone: this.timeZone,
      priority: v.priority,
    };
    const id = this.id();
    this.pending.set(true);
    (id ? this.api.update(id, request) : this.api.create(request))
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: (saved) => void this.router.navigate(['/reminders', saved.id], { state: { saved: true } }),
        error: (err: unknown) => {
          const error = asApiError(err);
          if (error.code === 'REMINDER_IN_PAST') {
            this.pastError.set(true);
            return;
          }
          applyServerErrors(this.form, error.fieldErrors);
          this.saveError.set(describeError(error, this.i18n));
        },
      });
  }
}

function notBlank(control: AbstractControl): ValidationErrors | null {
  const value = control.value as string;
  return value && value.trim().length === 0 ? { required: true } : null;
}
