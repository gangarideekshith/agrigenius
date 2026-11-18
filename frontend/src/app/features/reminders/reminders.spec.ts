import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AppNotification, Reminder, ReminderSummary } from '../../core/api/models';
import { authInterceptor, errorInterceptor } from '../../core/auth/interceptors';
import { NotificationCenter } from '../../core/notifications/notification-center';
import { NotificationBell } from '../../shared/notification-bell';
import { ReminderDetailPage } from './reminder-detail';
import { ReminderFormPage } from './reminder-form';
import { RemindersPage } from './reminders';
import { isInPast, nowInZone } from './reminder-utils';

const REMINDER: Reminder = {
  id: 'r1',
  title: 'Irrigate paddy field',
  description: 'Open the canal gate',
  category: 'IRRIGATION',
  cropName: 'Rice',
  scheduledAt: '2030-06-02T01:00:00Z',
  scheduledDate: '2030-06-02',
  scheduledTime: '06:30:00',
  timeZone: 'Asia/Kolkata',
  status: 'PENDING',
  priority: 'HIGH',
  overdue: false,
  notifiedAt: null,
  completedAt: null,
  createdAt: '2030-06-01T00:00:00Z',
  updatedAt: '2030-06-01T00:00:00Z',
};

const SUMMARY: ReminderSummary = {
  pending: 3, overdue: 1, dueToday: 1, upcoming: 2, upcomingDays: 7, completed: 4, cancelled: 0,
};

const NOTIFICATION: AppNotification = {
  id: 'n1',
  type: 'REMINDER_DUE',
  title: 'Irrigate paddy field',
  body: null,
  reminderId: 'r1',
  scheduledFor: '2030-06-02T01:00:00Z',
  createdAt: '2030-06-02T01:00:30Z',
  read: false,
  readAt: null,
};

function setup() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  return TestBed.inject(HttpTestingController);
}

function type(el: HTMLElement, selector: string, value: string): void {
  const input = el.querySelector(selector) as HTMLInputElement | HTMLSelectElement;
  input.value = value;
  input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
  input.dispatchEvent(new Event('blur'));
}

function problem(req: TestRequest, status: number, code: string, errors: unknown[] = []): void {
  req.flush({ status, code, title: code, errors }, { status, statusText: code });
}

async function settle(fixture: { whenStable(): Promise<unknown>; detectChanges(): void }): Promise<void> {
  await fixture.whenStable();
  fixture.detectChanges();
}

function futureDate(days: number): string {
  const d = new Date(`${nowInZone('Asia/Kolkata').date}T00:00:00`);
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Lets the poll timer (timer(0, ...)) fire. */
function tick(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

describe('reminder time helpers', () => {
  it('detects past local times in a zone', () => {
    expect(isInPast('2000-01-01', '06:00', 'Asia/Kolkata')).toBe(true);
    expect(isInPast(futureDate(1), '06:00', 'Asia/Kolkata')).toBe(false);
  });
});

describe('ReminderFormPage', () => {
  it('creates a reminder with trimmed values and the browser time zone', async () => {
    const http = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ReminderFormPage);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const date = futureDate(2);

    type(el, '#rm-title', '  Water the paddy field ');
    type(el, '#rm-category', 'IRRIGATION');
    type(el, '#rm-priority', 'HIGH');
    type(el, '#rm-crop', ' Rice ');
    type(el, '#rm-date', date);
    type(el, '#rm-time', '06:30');
    type(el, '#rm-description', '');
    fixture.detectChanges();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/reminders');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        title: 'Water the paddy field',
        category: 'IRRIGATION',
        priority: 'HIGH',
        cropName: 'Rice',
        description: null,
        scheduledDate: date,
        scheduledTime: '06:30',
      }),
    );
    expect(typeof req.request.body.timeZone).toBe('string');
    expect(req.request.body.userId).toBeUndefined();
    req.flush({ ...REMINDER, id: 'new1' });
    await settle(fixture);
    expect(navigate).toHaveBeenCalledWith(['/reminders', 'new1'], { state: { saved: true } });
  });

  it('does not submit an incomplete form and shows field errors', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ReminderFormPage);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle(fixture);

    http.expectNone('/api/reminders');
    expect(el.querySelector('#rm-title-error')?.textContent).toContain('Please fill this in.');
    expect(el.querySelector('#rm-category-error')).not.toBeNull();
    expect(el.textContent).toContain('Please correct the highlighted fields.');
  });

  it('rejects a past date and time before calling the API', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ReminderFormPage);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    type(el, '#rm-title', 'Late task');
    type(el, '#rm-category', 'GENERAL');
    type(el, '#rm-date', '2001-01-01');
    type(el, '#rm-time', '06:00');
    fixture.detectChanges();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle(fixture);

    http.expectNone('/api/reminders');
    expect(el.textContent).toContain('already passed');
  });

  it('shows server field errors next to the fields', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ReminderFormPage);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    type(el, '#rm-title', 'Spray');
    type(el, '#rm-category', 'PESTICIDE');
    fixture.detectChanges();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    problem(http.expectOne('/api/reminders'), 400, 'VALIDATION_FAILED', [
      { field: 'title', message: 'size must be between 0 and 120' },
    ]);
    await settle(fixture);
    expect(el.querySelector('#rm-title-error')?.textContent).toContain('size must be between 0 and 120');
    expect(el.textContent).toContain('Some details are not valid');
  });

  it('loads an existing reminder for editing and saves it with PUT in its own zone', async () => {
    const http = setup();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ReminderFormPage);
    fixture.componentRef.setInput('id', 'r1');
    fixture.detectChanges();
    http.expectOne('/api/reminders/r1').flush({ ...REMINDER, scheduledDate: futureDate(3), timeZone: 'Asia/Kolkata' });
    await settle(fixture);
    const el = fixture.nativeElement as HTMLElement;
    expect((el.querySelector('#rm-title') as HTMLInputElement).value).toBe('Irrigate paddy field');
    expect((el.querySelector('#rm-time') as HTMLInputElement).value).toBe('06:30');

    type(el, '#rm-title', 'Irrigate east field');
    fixture.detectChanges();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/reminders/r1');
    expect(req.request.body.title).toBe('Irrigate east field');
    expect(req.request.body.timeZone).toBe('Asia/Kolkata');
    req.flush(REMINDER);
  });

  it('explains that completed reminders cannot be edited', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ReminderFormPage);
    fixture.componentRef.setInput('id', 'r1');
    fixture.detectChanges();
    http.expectOne('/api/reminders/r1').flush({ ...REMINDER, status: 'COMPLETED' });
    await settle(fixture);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('form')).toBeNull();
    expect(el.textContent).toContain('Only pending reminders can be edited');
  });
});

describe('RemindersPage', () => {
  function open() {
    const http = setup();
    const fixture = TestBed.createComponent(RemindersPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/reminders/summary').flush(SUMMARY);
    return { http, fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows counts and the overdue section first', async () => {
    const { http, fixture, el } = open();
    http.expectOne('/api/reminders/overdue').flush([{ ...REMINDER, overdue: true }]);
    await settle(fixture);
    expect(el.textContent).toContain('Irrigate paddy field');
    expect(el.textContent).toContain('Overdue');
    expect(el.querySelector('.reminder-item.is-overdue')).not.toBeNull();
    const stats = Array.from(el.querySelectorAll('.stat strong')).map((s) => s.textContent?.trim());
    expect(stats).toEqual(['1', '1', '2', '3', '4']);
  });

  it('shows an empty state when there is nothing to show', async () => {
    const { http, fixture, el } = open();
    http.expectOne('/api/reminders/overdue').flush([]);
    await settle(fixture);
    expect(el.textContent).toContain('No overdue reminders');
  });

  it('shows an error with retry when loading fails', async () => {
    const { http, fixture, el } = open();
    problem(http.expectOne('/api/reminders/overdue'), 500, 'INTERNAL_ERROR');
    await settle(fixture);
    expect(el.querySelector('.state-error')).not.toBeNull();
    (el.querySelector('.state-error button') as HTMLButtonElement).click();
    http.expectOne('/api/reminders/overdue').flush([]);
    await settle(fixture);
    expect(el.querySelector('.state-error')).toBeNull();
  });

  it('filters the full list by category, status, dates and text', async () => {
    const { http, fixture, el } = open();
    http.expectOne('/api/reminders/overdue').flush([]);
    await settle(fixture);
    (Array.from(el.querySelectorAll('.tab')).find((t) => t.textContent?.trim() === 'All') as HTMLButtonElement).click();
    http.expectOne((r) => r.url === '/api/reminders' && !r.params.has('status')).flush({
      items: [REMINDER], page: 0, size: 20, totalItems: 1, totalPages: 1,
    });
    await settle(fixture);

    type(el, '#rf-q', ' paddy ');
    type(el, '#rf-category', 'IRRIGATION');
    type(el, '#rf-status', 'PENDING');
    type(el, '#rf-from', '2030-06-01');
    type(el, '#rf-to', '2030-06-30');
    fixture.detectChanges();
    (el.querySelector('form.filters') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const req = http.expectOne((r) => r.url === '/api/reminders' && r.params.get('category') === 'IRRIGATION');
    expect(req.request.params.get('q')).toBe('paddy');
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.get('from')).toBe('2030-06-01');
    expect(req.request.params.get('to')).toBe('2030-06-30');
    req.flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    await settle(fixture);
    expect(el.textContent).toContain('No reminders match');
  });

  it('marks a reminder as done and refreshes the counts', async () => {
    const { http, fixture, el } = open();
    http.expectOne('/api/reminders/overdue').flush([{ ...REMINDER, overdue: true }]);
    await settle(fixture);
    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Mark done')) as HTMLButtonElement).click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/reminders/r1/complete').flush({ ...REMINDER, status: 'COMPLETED' });
    http.expectOne((r) => r.url === '/api/reminders/summary').flush({ ...SUMMARY, overdue: 0 });
    http.expectOne('/api/reminders/overdue').flush([]);
    await settle(fixture);
    expect(el.textContent).toContain('marked as done');
  });

  it('asks for confirmation before deleting', async () => {
    const { http, fixture, el } = open();
    http.expectOne('/api/reminders/overdue').flush([{ ...REMINDER, overdue: true }]);
    await settle(fixture);
    const findButton = (text: string) =>
      Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim().startsWith(text)) as HTMLButtonElement;

    findButton('Delete').click();
    await settle(fixture);
    http.expectNone((r) => r.method === 'DELETE');
    expect(el.textContent).toContain('This cannot be undone');

    findButton('Yes, delete').click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/reminders/r1').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne((r) => r.url === '/api/reminders/summary').flush(SUMMARY);
    http.expectOne('/api/reminders/overdue').flush([]);
    await settle(fixture);
    expect(el.textContent).toContain('was deleted');
  });
});

describe('ReminderDetailPage', () => {
  it('shows the reminder and reopens a completed one', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ReminderDetailPage);
    fixture.componentRef.setInput('id', 'r1');
    fixture.detectChanges();
    http.expectOne('/api/reminders/r1').flush({ ...REMINDER, status: 'COMPLETED', completedAt: '2030-06-02T02:00:00Z' });
    await settle(fixture);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Irrigate paddy field');
    expect(el.textContent).toContain('Open the canal gate');
    expect(el.textContent).toContain('Completed');
    expect(Array.from(el.querySelectorAll('a')).some((a) => a.textContent?.trim() === 'Edit')).toBe(false);

    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Reopen') as HTMLButtonElement).click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/reminders/r1/reopen').flush(REMINDER);
    await settle(fixture);
    expect(el.textContent).toContain('is pending again');
    expect(Array.from(el.querySelectorAll('a')).some((a) => a.textContent?.trim() === 'Edit')).toBe(true);
  });

  it('shows not-found errors from the API', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ReminderDetailPage);
    fixture.componentRef.setInput('id', 'missing');
    fixture.detectChanges();
    problem(http.expectOne('/api/reminders/missing'), 404, 'NOT_FOUND');
    await settle(fixture);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('We could not find this.');
  });
});

describe('NotificationBell', () => {
  it('shows the unread count, lists notifications and opens the reminder', async () => {
    const http = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const center = TestBed.inject(NotificationCenter);
    const fixture = TestBed.createComponent(NotificationBell);
    center.start();
    await tick();
    http.expectOne('/api/notifications/unread-count').flush({ count: 2 });
    await settle(fixture);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.bell-badge')?.textContent?.trim()).toBe('2');
    expect(el.querySelector('.bell-button')?.getAttribute('aria-label')).toBe('Notifications, 2 unread');

    (el.querySelector('.bell-button') as HTMLButtonElement).click();
    http.expectOne((r) => r.url === '/api/notifications' && r.params.get('unreadOnly') === 'false').flush({
      items: [NOTIFICATION], page: 0, size: 10, totalItems: 1, totalPages: 1,
    });
    await settle(fixture);
    expect(el.textContent).toContain('Irrigate paddy field');

    (el.querySelector('.bell-item') as HTMLButtonElement).click();
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/notifications/n1/read').flush({ ...NOTIFICATION, read: true });
    await settle(fixture);
    expect(navigate).toHaveBeenCalledWith(['/reminders', 'r1']);
    expect(center.unreadCount()).toBe(1);
    center.stop();
  });

  it('shows an empty state and marks everything read', async () => {
    const http = setup();
    const center = TestBed.inject(NotificationCenter);
    const fixture = TestBed.createComponent(NotificationBell);
    center.start();
    await tick();
    http.expectOne('/api/notifications/unread-count').flush({ count: 0 });
    await settle(fixture);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.bell-badge')).toBeNull();
    (el.querySelector('.bell-button') as HTMLButtonElement).click();
    http.expectOne((r) => r.url === '/api/notifications').flush({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 });
    await settle(fixture);
    expect(el.textContent).toContain('No notifications yet');
    expect(el.textContent).toContain('Nothing rings when the browser is closed');
    center.stop();
    http.verify();
  });
});
