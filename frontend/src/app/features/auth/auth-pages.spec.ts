import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor, errorInterceptor } from '../../core/auth/interceptors';
import { LoginPage } from './login';
import { RegisterPage } from './register';

function setup() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  return {
    http: TestBed.inject(HttpTestingController),
    navigate: vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true),
    navigateByUrl: vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true),
  };
}

function type(input: HTMLInputElement | HTMLSelectElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
  input.dispatchEvent(new Event('blur'));
}

describe('LoginPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('does not call the API when the form is invalid and shows field errors', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    type(el.querySelector<HTMLInputElement>('#login-mobile')!, '12345');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectNone('/api/auth/login');
    expect(el.textContent).toContain('Enter a valid 10-digit Indian mobile number.');
    expect(el.textContent).toContain('Please fill this in.');
    expect(el.querySelector('#login-mobile')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('shows the generic invalid-credentials message and clears the password', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    type(el.querySelector<HTMLInputElement>('#login-mobile')!, '98765 43210');
    type(el.querySelector<HTMLInputElement>('#login-password')!, 'wrong-pass1');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ mobileNumber: '9876543210', password: 'wrong-pass1' });
    req.flush({ code: 'INVALID_CREDENTIALS', status: 401 }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Mobile number or password is not correct.');
    expect(fixture.componentInstance.form.controls.password.value).toBe('');
  });

  it('signs in, loads the profile and goes to the dashboard', async () => {
    const { http, navigateByUrl } = setup();
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    fixture.componentInstance.form.setValue({ mobileNumber: '9876543210', password: 'good-pass1' });
    fixture.componentInstance.submit();

    http.expectOne('/api/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 1800, user: { id: 'u', role: 'FARMER' } });
    const me = http.expectOne('/api/farmers/me');
    expect(me.request.headers.get('Authorization')).toBe('Bearer t');
    me.flush({ id: 'u', fullName: 'Ravi', preferredLanguage: 'te' });

    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('only follows same-app return URLs', async () => {
    const { http, navigateByUrl } = setup();
    const fixture = TestBed.createComponent(LoginPage);
    fixture.componentRef.setInput('returnUrl', 'https://evil.example/');
    await fixture.whenStable();
    fixture.componentInstance.form.setValue({ mobileNumber: '9876543210', password: 'good-pass1' });
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 1800, user: { id: 'u', role: 'FARMER' } });
    http.expectOne('/api/farmers/me').flush({ id: 'u', fullName: 'Ravi', preferredLanguage: 'en' });
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });
});

describe('RegisterPage', () => {
  beforeEach(() => localStorage.clear());

  it('rejects a password equal to the mobile number without calling the API', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
    const form = fixture.componentInstance.form;
    form.patchValue({ fullName: 'Ravi Kumar', mobileNumber: '9876543210', password: '9876543210' });
    fixture.componentInstance.submit();
    await fixture.whenStable();

    http.expectNone('/api/auth/register');
    expect(form.controls.password.hasError('passwordEqualsMobile')).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Your password must not be your mobile number.');
  });

  it('sends normalised data and goes to login on success', async () => {
    const { http, navigate } = setup();
    const fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
    fixture.componentInstance.form.patchValue({
      fullName: '  Ravi Kumar ',
      mobileNumber: '+91 98765-43210',
      password: 'Kh3t-Safe-pass',
      preferredLanguage: 'te',
      email: '',
      district: 'Warangal',
    });
    fixture.componentInstance.submit();

    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      fullName: 'Ravi Kumar',
      mobileNumber: '+919876543210',
      password: 'Kh3t-Safe-pass',
      preferredLanguage: 'te',
      email: null,
      state: null,
      district: 'Warangal',
      village: null,
    });
    req.flush({ id: 'u' }, { status: 201, statusText: 'Created' });
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { registered: '1' } });
  });

  it('shows server field errors and the duplicate-account message', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
    fixture.componentInstance.form.patchValue({ fullName: 'Ravi Kumar', mobileNumber: '9876543210', password: 'Kh3t-Safe-pass' });

    fixture.componentInstance.submit();
    http.expectOne('/api/auth/register').flush(
      { code: 'VALIDATION_FAILED', errors: [{ field: 'fullName', message: 'may contain only letters' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('may contain only letters');

    fixture.componentInstance.form.controls.fullName.setValue('Ravi Kumar');
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/register').flush({ code: 'ACCOUNT_ALREADY_EXISTS' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')!.textContent).toContain('already exists');
  });
});
