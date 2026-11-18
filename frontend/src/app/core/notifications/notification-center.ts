import { Injectable, inject, signal } from '@angular/core';
import { Subscription, finalize, timer } from 'rxjs';
import { asApiError } from '../api/api-error';
import { NotificationApi } from '../api/api.services';
import { ErrorView, describeError } from '../api/error-message';
import { AppNotification } from '../api/models';
import { I18nService } from '../i18n/i18n.service';

export const POLL_INTERVAL_MS = 60_000;
const BROWSER_PREF_KEY = 'agrigenius.browserNotifications';
const PANEL_SIZE = 10;

export type BrowserNotificationState = 'unsupported' | 'off' | 'on' | 'denied';

/**
 * In-app notifications, kept fresh by polling the unread count while a farmer is signed in and the
 * app is open. Due reminders are detected on the server; this only displays them.
 *
 * Optional browser (system) notifications: if the farmer turns them on and grants permission, a
 * system notification is shown for each new notification seen by the poll. This works only while
 * AgriGenius is open in a browser tab; nothing rings when the browser is closed.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenter {
  private readonly api = inject(NotificationApi);
  private readonly i18n = inject(I18nService);

  readonly unreadCount = signal(0);
  readonly items = signal<AppNotification[]>([]);
  readonly loading = signal(false);
  readonly error = signal<ErrorView | null>(null);
  readonly browserState = signal<BrowserNotificationState>(this.initialBrowserState());

  private polling: Subscription | null = null;
  private lastCount: number | null = null;
  private readonly announced = new Set<string>();

  /** Starts polling (idempotent). Called by the shell when a session starts. */
  start(): void {
    if (this.polling) {
      return;
    }
    this.lastCount = null;
    this.polling = timer(0, POLL_INTERVAL_MS).subscribe(() => this.poll());
  }

  /** Stops polling and forgets everything (sign-out). */
  stop(): void {
    this.polling?.unsubscribe();
    this.polling = null;
    this.lastCount = null;
    this.announced.clear();
    this.unreadCount.set(0);
    this.items.set([]);
    this.error.set(null);
  }

  /** Re-checks immediately, e.g. after a reminder was completed or deleted. */
  refresh(): void {
    if (this.polling) {
      this.poll();
    }
  }

  /** Loads the latest notifications for the bell panel. */
  loadPanel(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(false, 0, PANEL_SIZE)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => this.items.set(page.items),
        error: (err: unknown) => this.error.set(describeError(asApiError(err), this.i18n)),
      });
  }

  markRead(notification: AppNotification): void {
    if (notification.read) {
      return;
    }
    this.api.markRead(notification.id).subscribe({
      next: (updated) => {
        this.items.update((list) => list.map((n) => (n.id === updated.id ? updated : n)));
        this.unreadCount.update((c) => Math.max(0, c - 1));
        this.lastCount = this.unreadCount();
      },
      error: (err: unknown) => this.error.set(describeError(asApiError(err), this.i18n)),
    });
  }

  markAllRead(): void {
    this.api.markAllRead().subscribe({
      next: () => {
        const now = new Date().toISOString();
        this.items.update((list) => list.map((n) => (n.read ? n : { ...n, read: true, readAt: now })));
        this.unreadCount.set(0);
        this.lastCount = 0;
      },
      error: (err: unknown) => this.error.set(describeError(asApiError(err), this.i18n)),
    });
  }

  /** Asks the browser for permission; only call from a user click. */
  async enableBrowserNotifications(): Promise<void> {
    if (!this.browserSupported()) {
      return;
    }
    const permission = await Notification.requestPermission();
    if (permission === 'granted') {
      this.savePreference(true);
      this.browserState.set('on');
    } else {
      this.savePreference(false);
      this.browserState.set(permission === 'denied' ? 'denied' : 'off');
    }
  }

  disableBrowserNotifications(): void {
    this.savePreference(false);
    this.browserState.set(this.browserSupported() ? 'off' : 'unsupported');
  }

  private poll(): void {
    this.api.unreadCount().subscribe({
      next: ({ count }) => {
        const previous = this.lastCount;
        this.unreadCount.set(count);
        this.lastCount = count;
        // On the first poll after sign-in only remember what exists; announce later arrivals.
        if (previous === null) {
          this.rememberUnread();
        } else if (count > previous) {
          this.announceNew();
        }
      },
      // Polling errors are not shown: the badge keeps its last value and the next poll retries.
      error: () => undefined,
    });
  }

  private rememberUnread(): void {
    if (this.browserState() !== 'on') {
      return;
    }
    this.api.list(true, 0, PANEL_SIZE).subscribe({
      next: (page) => page.items.forEach((n) => this.announced.add(n.id)),
      error: () => undefined,
    });
  }

  private announceNew(): void {
    if (this.browserState() !== 'on' || !this.browserSupported() || Notification.permission !== 'granted') {
      return;
    }
    this.api.list(true, 0, PANEL_SIZE).subscribe({
      next: (page) => {
        for (const n of page.items) {
          if (this.announced.has(n.id)) {
            continue;
          }
          this.announced.add(n.id);
          try {
            new Notification(n.title, { body: n.body ?? this.i18n.t('notifications.reminderDue'), tag: n.id });
          } catch {
            // Some browsers only allow notifications from a service worker; the in-app bell still works.
          }
        }
      },
      error: () => undefined,
    });
  }

  private browserSupported(): boolean {
    return typeof window !== 'undefined' && 'Notification' in window;
  }

  private initialBrowserState(): BrowserNotificationState {
    if (!this.browserSupported()) {
      return 'unsupported';
    }
    if (Notification.permission === 'denied') {
      return 'denied';
    }
    return Notification.permission === 'granted' && this.readPreference() ? 'on' : 'off';
  }

  private readPreference(): boolean {
    try {
      return localStorage.getItem(BROWSER_PREF_KEY) === 'on';
    } catch {
      return false;
    }
  }

  private savePreference(on: boolean): void {
    try {
      if (on) {
        localStorage.setItem(BROWSER_PREF_KEY, 'on');
      } else {
        localStorage.removeItem(BROWSER_PREF_KEY);
      }
    } catch {
      // Storage may be unavailable (private mode); the preference then lasts for this page only.
    }
  }
}
