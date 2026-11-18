import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppNotification } from '../core/api/models';
import { TranslatePipe } from '../core/i18n/i18n.service';
import { NotificationCenter } from '../core/notifications/notification-center';
import { FarmTimePipe } from '../features/reminders/reminder-utils';
import { ErrorBanner, Loading } from './state-views';

/**
 * Header bell: unread badge and a panel with the latest notifications. Opening a notification marks
 * it read and goes to its reminder. Closes on Escape or a click outside.
 */
@Component({
  selector: 'app-notification-bell',
  imports: [RouterLink, TranslatePipe, FarmTimePipe, Loading, ErrorBanner],
  template: `
    <button
      type="button"
      class="bell-button"
      [attr.aria-expanded]="open()"
      aria-controls="notification-panel"
      [attr.aria-label]="'notifications.bellLabel' | t: { count: center.unreadCount() }"
      (click)="toggle()"
    >
      <span aria-hidden="true">🔔</span>
      @if (center.unreadCount() > 0) {
        <span class="bell-badge" aria-hidden="true">{{ center.unreadCount() > 99 ? '99+' : center.unreadCount() }}</span>
      }
    </button>
    @if (open()) {
      <div id="notification-panel" class="bell-panel" role="region" [attr.aria-label]="'notifications.title' | t">
        <div class="bell-panel-head">
          <h2>{{ 'notifications.title' | t }}</h2>
          <button type="button" class="btn btn-ghost btn-small" [disabled]="center.unreadCount() === 0" (click)="center.markAllRead()">
            {{ 'notifications.markAllRead' | t }}
          </button>
        </div>
        @if (center.loading()) {
          <app-loading />
        } @else if (center.error()) {
          <app-error-banner [error]="center.error()" [retryable]="true" (retry)="center.loadPanel()" />
        } @else if (center.items().length === 0) {
          <p class="state state-empty">{{ 'notifications.empty' | t }}</p>
        } @else {
          <ul class="bell-list">
            @for (n of center.items(); track n.id) {
              <li [class.unread]="!n.read">
                <button type="button" class="bell-item" (click)="openNotification(n)">
                  <strong>{{ n.title }}</strong>
                  <span class="small">{{ 'notifications.dueAt' | t: { time: (n.scheduledFor | farmTime) } }}</span>
                  @if (!n.read) { <span class="visually-hidden">{{ 'notifications.unread' | t }}</span> }
                </button>
                @if (!n.read) {
                  <button type="button" class="btn btn-ghost btn-small" (click)="center.markRead(n)"
                          [attr.aria-label]="('notifications.markRead' | t) + ': ' + n.title">
                    {{ 'notifications.markRead' | t }}
                  </button>
                }
              </li>
            }
          </ul>
        }
        <div class="bell-panel-foot">
          <a routerLink="/reminders" (click)="open.set(false)">{{ 'notifications.allReminders' | t }}</a>
          @switch (center.browserState()) {
            @case ('off') {
              <button type="button" class="btn btn-ghost btn-small" (click)="center.enableBrowserNotifications()">
                {{ 'notifications.browserEnable' | t }}
              </button>
            }
            @case ('on') {
              <button type="button" class="btn btn-ghost btn-small" (click)="center.disableBrowserNotifications()">
                {{ 'notifications.browserDisable' | t }}
              </button>
            }
            @case ('denied') {
              <span class="small muted">{{ 'notifications.browserDenied' | t }}</span>
            }
          }
        </div>
        <p class="small muted">{{ 'notifications.openAppNote' | t }}</p>
      </div>
    }
  `,
})
export class NotificationBell {
  protected readonly center = inject(NotificationCenter);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  readonly open = signal(false);

  toggle(): void {
    this.open.update((o) => !o);
    if (this.open()) {
      this.center.loadPanel();
    }
  }

  openNotification(n: AppNotification): void {
    this.center.markRead(n);
    this.open.set(false);
    void this.router.navigate(n.reminderId ? ['/reminders', n.reminderId] : ['/reminders']);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
