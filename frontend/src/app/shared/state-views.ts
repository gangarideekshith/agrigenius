import { Component, input, output } from '@angular/core';
import { ErrorView } from '../core/api/error-message';
import { TranslatePipe } from '../core/i18n/i18n.service';

/** Inline loading indicator, announced politely to screen readers. */
@Component({
  selector: 'app-loading',
  imports: [TranslatePipe],
  template: `
    <div class="state state-loading" role="status" aria-live="polite">
      <span class="spinner" aria-hidden="true"></span>
      <span>{{ label() ?? ('common.loading' | t) }}</span>
    </div>
  `,
})
export class Loading {
  readonly label = input<string | null>(null);
}

/** Error box with an optional retry button. Uses role="alert" so the message is read immediately. */
@Component({
  selector: 'app-error-banner',
  imports: [TranslatePipe],
  template: `
    @if (error(); as e) {
      <div class="state state-error" role="alert">
        <p>{{ e.message }}</p>
        @if (e.reference) {
          <p class="muted small">{{ e.reference }}</p>
        }
        @if (retryable()) {
          <button type="button" class="btn btn-secondary" (click)="retry.emit()">{{ 'common.retry' | t }}</button>
        }
      </div>
    }
  `,
})
export class ErrorBanner {
  readonly error = input<ErrorView | null>(null);
  readonly retryable = input(false);
  readonly retry = output<void>();
}
