import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { Subscription } from 'rxjs';
import { I18nService } from '../core/i18n/i18n.service';

/**
 * Shows the first validation message for a control once the user has touched it (or submitted).
 * The element id lets inputs reference it with aria-describedby.
 *
 * Components are OnPush by default, and form-control state is not a signal, so this component
 * subscribes to the control's unified `events` stream (value, status, touched, pristine) and
 * re-evaluates the message on every change.
 */
@Component({
  selector: 'app-field-error',
  template: `
    @if (message(); as text) {
      <p class="field-error" [id]="errorId()" role="alert">{{ text }}</p>
    }
  `,
})
export class FieldError {
  private readonly i18n = inject(I18nService);
  readonly control = input.required<AbstractControl | null>();
  readonly errorId = input.required<string>();

  private readonly version = signal(0);

  readonly message = computed(() => {
    this.version();
    this.i18n.language();
    return this.describe(this.control());
  });

  constructor() {
    let subscription: Subscription | null = null;
    effect(() => {
      const control = this.control();
      subscription?.unsubscribe();
      subscription = control ? control.events.subscribe(() => this.version.update((v) => v + 1)) : null;
    });
    inject(DestroyRef).onDestroy(() => subscription?.unsubscribe());
  }

  private describe(control: AbstractControl | null): string | null {
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    const errors = control.errors;
    if (typeof errors['server'] === 'string') {
      return errors['server'] as string;
    }
    if (errors['required']) return this.i18n.t('validation.required');
    if (errors['mobile']) return this.i18n.t('validation.mobile');
    if (errors['passwordEqualsMobile']) return this.i18n.t('validation.passwordEqualsMobile');
    if (errors['passwordBytes']) return this.i18n.t('validation.passwordBytes');
    if (errors['password']) return this.i18n.t('validation.password');
    if (errors['fullName']) return this.i18n.t('validation.fullName');
    if (errors['email']) return this.i18n.t('validation.email');
    if (errors['maxlength']) {
      return this.i18n.t('validation.maxlength', { max: (errors['maxlength'] as { requiredLength: number }).requiredLength });
    }
    if (errors['min']) return this.i18n.t('validation.min', { min: (errors['min'] as { min: number }).min });
    if (errors['max']) return this.i18n.t('validation.max', { max: (errors['max'] as { max: number }).max });
    return this.i18n.t('validation.number');
  }
}
