import { Component, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LANGUAGES, LanguageCode } from './core/api/models';
import { AuthService } from './core/auth/auth.service';
import { I18nService, TranslatePipe, isLanguage } from './core/i18n/i18n.service';
import { NotificationCenter } from './core/notifications/notification-center';
import { NotificationBell } from './shared/notification-bell';

/** Page frame: header with navigation, language switcher and sign-out; main content below. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, NotificationBell],
  template: `
    <a class="skip-link" href="#main">{{ 'app.skipToContent' | t }}</a>
    <header class="topbar">
      <a class="brand" [routerLink]="auth.isAuthenticated() ? '/dashboard' : '/login'">
        <span class="brand-mark" aria-hidden="true">🌱</span>
        <span>{{ 'app.name' | t }}</span>
      </a>
      @if (auth.isAuthenticated()) {
        <button
          type="button"
          class="menu-toggle"
          [attr.aria-expanded]="menuOpen()"
          aria-controls="main-nav"
          (click)="menuOpen.set(!menuOpen())"
        >
          {{ 'nav.menu' | t }}
        </button>
        <nav id="main-nav" class="mainnav" [class.open]="menuOpen()" [attr.aria-label]="'nav.mainNavigation' | t">
          <a routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page">{{ 'nav.dashboard' | t }}</a>
          <a routerLink="/recommendations/new" routerLinkActive="active" ariaCurrentWhenActive="page">{{
            'nav.newRecommendation' | t
          }}</a>
          <a
            routerLink="/recommendations"
            routerLinkActive="active"
            [routerLinkActiveOptions]="{ exact: true }"
            ariaCurrentWhenActive="page"
            >{{ 'nav.history' | t }}</a
          >
          <a routerLink="/reminders" routerLinkActive="active" ariaCurrentWhenActive="page">{{ 'nav.reminders' | t }}</a>
          <a routerLink="/chat" routerLinkActive="active" ariaCurrentWhenActive="page">{{ 'nav.chat' | t }}</a>
          <a routerLink="/profile" routerLinkActive="active" ariaCurrentWhenActive="page">{{ 'nav.profile' | t }}</a>
        </nav>
      }
      <div class="topbar-actions">
        <label class="lang-switch">
          <span class="visually-hidden">{{ 'nav.language' | t }}</span>
          <select [value]="i18n.language()" (change)="onLanguage($event)">
            @for (code of languages; track code) {
              <option [value]="code">{{ 'languages.' + code | t }}</option>
            }
          </select>
        </label>
        @if (auth.isAuthenticated()) {
          <app-notification-bell />
          <button type="button" class="btn btn-ghost" (click)="auth.logout('signed-out')">{{ 'nav.logout' | t }}</button>
        }
      </div>
    </header>
    <main id="main" tabindex="-1">
      <router-outlet />
    </main>
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly languages: readonly LanguageCode[] = LANGUAGES;
  protected readonly menuOpen = signal(false);

  constructor() {
    // Poll for due-reminder notifications only while signed in.
    const notifications = inject(NotificationCenter);
    effect(() => (this.auth.isAuthenticated() ? notifications.start() : notifications.stop()));
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.menuOpen.set(false);
        // Move focus to the new page's content for keyboard and screen-reader users.
        queueMicrotask(() => document.getElementById('main')?.focus({ preventScroll: true }));
      });
  }

  protected onLanguage(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    if (isLanguage(value)) {
      this.i18n.choose(value);
    }
  }
}
