import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ChatMessage, ChatSession } from '../../core/api/models';
import { authInterceptor, errorInterceptor } from '../../core/auth/interceptors';
import { ChatPage } from './chat';

const SESSION: ChatSession = {
  id: 's1',
  title: 'Paddy pests',
  language: 'en',
  status: 'ACTIVE',
  messageCount: 0,
  createdAt: '2026-11-17T09:00:00Z',
  lastMessageAt: null,
};

function msg(partial: Partial<ChatMessage>): ChatMessage {
  return { id: 'm', role: 'ASSISTANT', status: 'OK', content: '', sources: [], toolsUsed: [], createdAt: '2026-11-17T09:01:00Z', ...partial };
}

async function setupWithSession() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])), provideHttpClientTesting()],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ChatPage);
  await fixture.whenStable();
  http.expectOne((r) => r.url === '/api/chat/sessions').flush({ items: [SESSION], page: 0, size: 50, totalItems: 1, totalPages: 1 });
  http.expectOne((r) => r.url === '/api/chat/sessions/s1/messages').flush({ items: [], page: 0, size: 100, totalItems: 0, totalPages: 0 });
  await fixture.whenStable();
  return { http, fixture, el: fixture.nativeElement as HTMLElement };
}

describe('ChatPage', () => {
  it('shows a notice when the chat assistant is not configured', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ChatPage);
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/chat/sessions').flush({ code: 'CHAT_DISABLED' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('The chat assistant is not configured yet');
    expect(el.querySelector('textarea')).toBeNull();
  });

  it('keeps conversations visible and disables sending after CHAT_DISABLED on send', async () => {
    const { http, fixture, el } = await setupWithSession();
    const page = fixture.componentInstance;
    page.draft.set('Hello');
    await fixture.whenStable();
    page.send();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/chat/sessions/s1/messages')
      .flush({ code: 'CHAT_DISABLED' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('.chat-disabled')?.textContent).toContain('not configured');
    expect(el.querySelector('.session-list')).not.toBeNull();
    page.draft.set('Another question');
    await fixture.whenStable();
    expect(page.canSend()).toBe(false);
  });

  it('disables sending while the answer is pending, then renders it as plain text', async () => {
    const { http, fixture, el } = await setupWithSession();
    const page = fixture.componentInstance;
    page.draft.set('  My paddy leaves have <b>brown</b> spots  ');
    await fixture.whenStable();
    expect(el.textContent).toContain('42 of 2000 characters'.replace('42', String(page.draft().length)));

    page.send();
    await fixture.whenStable();
    expect(el.querySelector<HTMLButtonElement>('.composer button[type="submit"]')!.disabled).toBe(true);
    expect(el.textContent).toContain('Waiting for the answer…');

    const req = http.expectOne('/api/chat/sessions/s1/messages');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ content: 'My paddy leaves have <b>brown</b> spots' });
    req.flush({
      userMessage: msg({ id: 'u1', role: 'USER', content: 'My paddy leaves have <b>brown</b> spots' }),
      assistantMessage: msg({
        id: 'a1',
        content: '<img src=x onerror=alert(1)> It may be brown spot.',
        sources: [
          { title: 'Rice IPM', publisher: 'Demo', url: 'https://example.org/rice', licence: 'CC-BY-4.0' },
          { title: 'Bad link', publisher: 'Demo', url: 'javascript:alert(1)', licence: 'CC-BY-4.0' },
        ],
        uncertainty: 'Confirm with your local KVK.',
      }),
      usage: { remainingMessagesToday: 37 },
    });
    await fixture.whenStable();

    expect(el.querySelector('img')).toBeNull(); // content rendered as text, never HTML
    expect(el.textContent).toContain('<img src=x onerror=alert(1)> It may be brown spot.');
    expect(el.textContent).toContain('Confirm with your local KVK.');
    expect(el.textContent).toContain('37 questions left today');
    const links = Array.from(el.querySelectorAll<HTMLAnchorElement>('.sources a'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['https://example.org/rice']);
    expect(links[0].rel).toContain('noopener');
    expect(page.draft()).toBe('');
  });

  it('shows a distinct notice for NO_SOURCES answers', async () => {
    const { http, fixture, el } = await setupWithSession();
    fixture.componentInstance.draft.set('What is the market price of cotton today?');
    fixture.componentInstance.send();
    http.expectOne('/api/chat/sessions/s1/messages').flush({
      userMessage: msg({ id: 'u2', role: 'USER', content: 'What is the market price of cotton today?' }),
      assistantMessage: msg({ id: 'a2', status: 'NO_SOURCES', content: '' }),
      usage: { remainingMessagesToday: 36 },
    });
    await fixture.whenStable();
    expect(el.querySelector('.status-NO_SOURCES')!.textContent).toContain('No verified information was found');
  });

  it('keeps the draft and shows the daily-limit message when the limit is reached', async () => {
    const { http, fixture, el } = await setupWithSession();
    fixture.componentInstance.draft.set('One more question');
    fixture.componentInstance.send();
    http.expectOne('/api/chat/sessions/s1/messages').flush({ code: 'CHAT_DAILY_LIMIT_REACHED' }, { status: 429, statusText: 'Too Many' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('You have used all your questions for today');
    expect(fixture.componentInstance.draft()).toBe('One more question');
  });

  it('refuses empty or over-long messages', async () => {
    const { http, fixture } = await setupWithSession();
    const page = fixture.componentInstance;
    page.draft.set('   ');
    expect(page.canSend()).toBe(false);
    page.draft.set('x'.repeat(2001));
    expect(page.canSend()).toBe(false);
    page.send();
    http.expectNone('/api/chat/sessions/s1/messages');
  });

  it('asks for confirmation before deleting a conversation', async () => {
    const { http, fixture, el } = await setupWithSession();
    const deleteButton = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('Delete conversation'))!;
    deleteButton.click();
    await fixture.whenStable();
    http.expectNone((r) => r.method === 'DELETE');
    const yes = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('Yes, delete'))!;
    yes.click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/chat/sessions/s1').flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(fixture.componentInstance.sessions()).toEqual([]);
  });
});
