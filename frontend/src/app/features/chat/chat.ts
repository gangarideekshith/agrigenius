import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { asApiError } from '../../core/api/api-error';
import { ChatApi } from '../../core/api/api.services';
import { ErrorView, describeError } from '../../core/api/error-message';
import { ChatMessage, ChatSession, ChatSource } from '../../core/api/models';
import { I18nService, TranslatePipe } from '../../core/i18n/i18n.service';
import { ErrorBanner, Loading } from '../../shared/state-views';

export const MAX_MESSAGE_LENGTH = 2000;

@Component({
  selector: 'app-chat',
  imports: [FormsModule, DatePipe, TranslatePipe, Loading, ErrorBanner],
  template: `
    <h1 class="visually-hidden">{{ 'chat.title' | t }}</h1>
    @if (loadingSessions()) {
      <app-loading />
    } @else {
      <div class="chat-layout">
        <aside class="chat-sessions" [class.open]="sessionsOpen()" [attr.aria-label]="'chat.sessions' | t">
          <div class="chat-sessions-head">
            <h2>{{ 'chat.sessions' | t }}</h2>
            <button type="button" class="btn btn-primary" [disabled]="creating()" (click)="newSession()">
              {{ (creating() ? 'chat.creating' : 'chat.newChat') | t }}
            </button>
          </div>
          @if (sessions().length === 0) {
            <p class="muted">{{ 'chat.noSessions' | t }}</p>
          } @else {
            <ul class="session-list">
              @for (s of sessions(); track s.id) {
                <li [class.active]="s.id === activeId()">
                  <button type="button" class="session-btn" [attr.aria-current]="s.id === activeId() ? 'true' : null" (click)="select(s.id)">
                    {{ sessionTitle(s) }}
                  </button>
                </li>
              }
            </ul>
          }
        </aside>

        <section class="chat-main" aria-labelledby="chat-heading">
          <div class="chat-main-head">
            <button type="button" class="btn btn-ghost chat-sessions-toggle" [attr.aria-expanded]="sessionsOpen()" (click)="sessionsOpen.set(!sessionsOpen())">
              {{ 'chat.openSessions' | t }}
            </button>
            <h2 id="chat-heading">{{ activeSession() ? sessionTitle(activeSession()!) : ('chat.title' | t) }}</h2>
            @if (activeSession()) {
              @if (confirmingDelete()) {
                <span class="confirm" role="group" [attr.aria-label]="'chat.deleteConfirm' | t">
                  <span>{{ 'chat.deleteConfirm' | t }}</span>
                  <button type="button" class="btn btn-danger" (click)="deleteActive()">{{ 'chat.deleteYes' | t }}</button>
                  <button type="button" class="btn btn-secondary" (click)="confirmingDelete.set(false)">{{ 'common.cancel' | t }}</button>
                </span>
              } @else {
                <button type="button" class="btn btn-ghost" (click)="confirmingDelete.set(true)">{{ 'chat.delete' | t }}</button>
              }
            }
          </div>

          @if (disabled()) {
            <section class="notice chat-disabled" role="status">
              <strong>{{ 'chat.disabledTitle' | t }}</strong>
              <p>{{ 'chat.disabledText' | t }}</p>
            </section>
          }
          <app-error-banner [error]="error()" />

          @if (!activeSession()) {
            <div class="state state-empty">
              <p>{{ 'chat.selectOrStart' | t }}</p>
              <button type="button" class="btn btn-primary" [disabled]="creating()" (click)="newSession()">
                {{ (creating() ? 'chat.creating' : 'chat.newChat') | t }}
              </button>
            </div>
          } @else {
            <div class="messages" aria-live="polite" aria-relevant="additions" #messageList>
              @if (loadingMessages()) {
                <app-loading />
              } @else if (messages().length === 0 && !pending()) {
                <p class="muted">{{ 'chat.emptyConversation' | t }}</p>
              }
              @for (m of messages(); track m.id) {
                <article class="message" [class]="'message-' + m.role + ' status-' + m.status">
                  <header class="small muted">
                    {{ (m.role === 'USER' ? 'chat.you' : 'chat.assistant') | t }} · {{ m.createdAt | date: 'short' }}
                  </header>
                  @if (m.role === 'ASSISTANT' && m.status !== 'OK') {
                    <p class="message-status">{{ 'chat.status' + m.status | t }}</p>
                  }
                  @if (m.content) {
                    <p class="message-text">{{ m.content }}</p>
                  }
                  @if (m.uncertainty) {
                    <p class="uncertainty"><strong>{{ 'chat.uncertainty' | t }}:</strong> {{ m.uncertainty }}</p>
                  }
                  @if (m.sources.length) {
                    <details class="sources">
                      <summary>{{ 'chat.sources' | t }} ({{ m.sources.length }})</summary>
                      <ul>
                        @for (src of m.sources; track $index) {
                          <li>
                            @if (safeUrl(src)) {
                              <a [href]="safeUrl(src)" target="_blank" rel="noopener noreferrer">{{ src.title }}</a>
                            } @else {
                              {{ src.title }}
                            }
                            <span class="muted"> — {{ src.publisher }} ({{ src.licence }})</span>
                          </li>
                        }
                      </ul>
                    </details>
                  }
                  @if (m.toolsUsed.length) {
                    <p class="small muted">{{ 'chat.toolsUsed' | t: { tools: m.toolsUsed.join(', ') } }}</p>
                  }
                </article>
              }
              @if (pending()) {
                <article class="message message-USER pending"><p class="message-text">{{ pendingText() }}</p></article>
                <p class="muted" role="status">{{ 'chat.sending' | t }}</p>
              }
            </div>

            <form class="composer" (ngSubmit)="send()">
              <label for="chat-input" class="visually-hidden">{{ 'chat.messageLabel' | t }}</label>
              <textarea
                id="chat-input"
                name="message"
                rows="3"
                [maxlength]="maxLength"
                [(ngModel)]="draft"
                [placeholder]="'chat.placeholder' | t"
                [disabled]="pending()"
                aria-describedby="chat-counter chat-disclosure"
                (keydown.control.enter)="send()"
                #composer
              ></textarea>
              <div class="composer-foot">
                <span id="chat-counter" class="small muted">{{ 'chat.counter' | t: { count: draft().length } }}</span>
                @if (remaining() !== null) {
                  <span class="small muted">{{ 'chat.remaining' | t: { count: remaining() } }}</span>
                }
                <button type="submit" class="btn btn-primary" [disabled]="!canSend()">
                  {{ (pending() ? 'chat.sending' : 'chat.send') | t }}
                </button>
              </div>
              <p id="chat-disclosure" class="small muted">{{ 'chat.disclosure' | t }}</p>
            </form>
          }
        </section>
      </div>
    }
  `,
})
export class ChatPage implements OnInit {
  private readonly api = inject(ChatApi);
  private readonly i18n = inject(I18nService);
  private readonly composer = viewChild<ElementRef<HTMLTextAreaElement>>('composer');
  private readonly messageList = viewChild<ElementRef<HTMLElement>>('messageList');

  protected readonly maxLength = MAX_MESSAGE_LENGTH;

  readonly sessions = signal<ChatSession[]>([]);
  readonly activeId = signal<string | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly draft = signal('');
  readonly pendingText = signal('');
  readonly remaining = signal<number | null>(null);

  readonly loadingSessions = signal(true);
  readonly loadingMessages = signal(false);
  readonly creating = signal(false);
  readonly pending = signal(false);
  readonly disabled = signal(false);
  readonly sessionsOpen = signal(false);
  readonly confirmingDelete = signal(false);
  readonly error = signal<ErrorView | null>(null);

  readonly activeSession = computed(() => this.sessions().find((s) => s.id === this.activeId()) ?? null);
  readonly canSend = computed(() => {
    const text = this.draft().trim();
    return !this.pending() && !this.disabled() && text.length > 0 && text.length <= MAX_MESSAGE_LENGTH;
  });

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.loadingSessions.set(true);
    this.api.sessions(0, 50).subscribe({
      next: (page) => {
        this.sessions.set(page.items);
        this.loadingSessions.set(false);
        if (page.items.length > 0) {
          this.select(page.items[0].id);
        }
      },
      error: (err: unknown) => {
        this.handleError(err);
        this.loadingSessions.set(false);
      },
    });
  }

  select(id: string): void {
    this.activeId.set(id);
    this.sessionsOpen.set(false);
    this.confirmingDelete.set(false);
    this.error.set(null);
    this.messages.set([]);
    this.loadingMessages.set(true);
    this.api
      .messages(id, 0, 100)
      .pipe(finalize(() => this.loadingMessages.set(false)))
      .subscribe({
        next: (page) => {
          this.messages.set(page.items);
          this.scrollToEnd();
        },
        error: (err: unknown) => this.handleError(err),
      });
  }

  newSession(): void {
    this.creating.set(true);
    this.error.set(null);
    this.api
      .createSession(this.i18n.language())
      .pipe(finalize(() => this.creating.set(false)))
      .subscribe({
        next: (session) => {
          this.sessions.update((list) => [session, ...list]);
          this.activeId.set(session.id);
          this.messages.set([]);
          this.sessionsOpen.set(false);
          queueMicrotask(() => this.composer()?.nativeElement.focus());
        },
        error: (err: unknown) => this.handleError(err),
      });
  }

  deleteActive(): void {
    const id = this.activeId();
    if (!id) {
      return;
    }
    this.api.deleteSession(id).subscribe({
      next: () => {
        this.confirmingDelete.set(false);
        this.sessions.update((list) => list.filter((s) => s.id !== id));
        this.activeId.set(null);
        this.messages.set([]);
        const next = this.sessions()[0];
        if (next) {
          this.select(next.id);
        }
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  send(): void {
    const id = this.activeId();
    const text = this.draft().trim();
    if (!id || !this.canSend()) {
      return;
    }
    this.error.set(null);
    this.pending.set(true);
    this.pendingText.set(text);
    this.api
      .send(id, text)
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: (response) => {
          this.messages.update((list) => [...list, response.userMessage, response.assistantMessage]);
          this.remaining.set(response.usage?.remainingMessagesToday ?? null);
          this.draft.set('');
          this.sessions.update((list) =>
            list.map((s) => (s.id === id ? { ...s, messageCount: s.messageCount + 2, lastMessageAt: response.assistantMessage.createdAt } : s)),
          );
          this.scrollToEnd();
          queueMicrotask(() => this.composer()?.nativeElement.focus());
        },
        // The draft is kept so the farmer can retry without retyping.
        error: (err: unknown) => this.handleError(err),
      });
  }

  sessionTitle(s: ChatSession): string {
    if (s.title) {
      return s.title;
    }
    const date = new Date(s.createdAt).toLocaleDateString(this.i18n.language());
    return this.i18n.t('chat.untitled', { date });
  }

  /** Only plain http(s) links are rendered as links; anything else is shown as text. */
  safeUrl(source: ChatSource): string | null {
    try {
      const url = new URL(source.url);
      return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null;
    } catch {
      return null;
    }
  }

  private handleError(err: unknown): void {
    const apiError = asApiError(err);
    if (apiError.code === 'CHAT_DISABLED') {
      this.disabled.set(true);
      return;
    }
    this.error.set(describeError(apiError, this.i18n));
  }

  private scrollToEnd(): void {
    queueMicrotask(() => {
      const list = this.messageList()?.nativeElement;
      if (list) {
        list.scrollTop = list.scrollHeight;
      }
    });
  }
}
