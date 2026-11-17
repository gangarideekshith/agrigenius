import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor, errorInterceptor } from '../../core/auth/interceptors';
import { FarmerProfile } from '../../core/api/models';
import { DashboardPage } from '../dashboard/dashboard';
import { ProfilePage } from './profile';

const PROFILE: FarmerProfile = {
  id: 'u1',
  fullName: 'Ravi Kumar',
  mobileNumber: '+919876543210',
  email: null,
  preferredLanguage: 'en',
  state: 'Telangana',
  district: 'Warangal',
  village: null,
  addressLine: null,
  role: 'FARMER',
  createdAt: '2026-11-17T00:00:00Z',
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

function type(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

describe('ProfilePage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('loads the profile, shows the mobile number read-only and pre-fills the form', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    http.expectOne('/api/farmers/me').flush(PROFILE);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('+919876543210');
    expect((el.querySelector('#pf-name') as HTMLInputElement).value).toBe('Ravi Kumar');
    expect((el.querySelector('#pf-district') as HTMLInputElement).value).toBe('Warangal');
    expect(el.querySelector('#pf-mobile')).toBeNull();
    const save = el.querySelector('button[type=submit]') as HTMLButtonElement;
    expect(save.disabled).toBe(true); // nothing changed yet
  });

  it('saves edits with PUT and shows a success message', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    http.expectOne('/api/farmers/me').flush(PROFILE);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    type(el.querySelector('#pf-village') as HTMLInputElement, '  Hanamkonda ');
    type(el.querySelector('#pf-email') as HTMLInputElement, 'ravi@example.org');
    fixture.detectChanges();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/farmers/me');
    expect(req.request.body).toEqual({
      fullName: 'Ravi Kumar',
      preferredLanguage: 'en',
      email: 'ravi@example.org',
      state: 'Telangana',
      district: 'Warangal',
      village: 'Hanamkonda',
      addressLine: null,
    });
    req.flush({ ...PROFILE, village: 'Hanamkonda', email: 'ravi@example.org' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[role=status]')?.textContent).toContain('saved');
  });

  it('does not call the API when the name is invalid', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    http.expectOne('/api/farmers/me').flush(PROFILE);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    type(el.querySelector('#pf-name') as HTMLInputElement, '1');
    fixture.detectChanges();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http.expectNone((r) => r.method === 'PUT');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')).not.toBeNull();
  });

  it('shows the server error when the email is already used', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    http.expectOne('/api/farmers/me').flush(PROFILE);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    type(el.querySelector('#pf-email') as HTMLInputElement, 'taken@example.org');
    fixture.detectChanges();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http.expectOne((r) => r.method === 'PUT').flush(
      { status: 409, code: 'ACCOUNT_ALREADY_EXISTS', detail: 'An account with this email address already exists.' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent?.length).toBeGreaterThan(0);
  });

  it('shows a retryable error when the profile cannot be loaded', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    http.expectOne('/api/farmers/me').flush({ code: 'INTERNAL_ERROR', status: 500 }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role=alert]')).not.toBeNull();
    expect(el.querySelector('form')).toBeNull();
  });
});

describe('DashboardPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('greets the farmer and shows the latest real recommendation', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    http.expectOne('/api/farmers/me').flush(PROFILE);
    http.expectOne((r) => r.url === '/api/recommendations/history' && r.params.get('size') === '1').flush({
      items: [{ id: 'r1', status: 'COMPLETED', requestedAt: '2026-11-17T01:00:00Z', topCrop: { code: 'rice', name: 'Rice' }, topConfidence: 'HIGH' }],
      page: 0, size: 1, totalItems: 1, totalPages: 1,
    });
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h1')?.textContent).toContain('Ravi Kumar');
    expect(el.textContent).toContain('Rice');
    expect(el.querySelector('a[href="/recommendations/r1"]')).not.toBeNull();
    expect(el.querySelector('a[href="/profile"]')).not.toBeNull();
  });

  it('shows an empty state when the farmer has no recommendations yet', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    http.expectOne('/api/farmers/me').flush(PROFILE);
    http.expectOne((r) => r.url === '/api/recommendations/history').flush({ items: [], page: 0, size: 1, totalItems: 0, totalPages: 0 });
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.state-empty')).not.toBeNull();
  });
});
