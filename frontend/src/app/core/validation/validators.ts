import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FieldError } from '../api/api-error';

/** Same rule as the backend: optional +91, then 10 digits starting 6-9, spaces/hyphens ignored. */
export const INDIAN_MOBILE = /^(\+91)?[6-9]\d{9}$/;

export function normalizeMobile(raw: string | null | undefined): string {
  return (raw ?? '').replace(/[\s-]/g, '');
}

export const indianMobileValidator: ValidatorFn = (control) => {
  const value = control.value as string | null;
  if (!value) {
    return null;
  }
  return INDIAN_MOBILE.test(normalizeMobile(value)) ? null : { mobile: true };
};

/** 8-64 characters (code points), at most 72 UTF-8 bytes (BCrypt), at least one letter and one digit. */
export const passwordPolicyValidator: ValidatorFn = (control) => {
  const value = control.value as string | null;
  if (!value) {
    return null;
  }
  const length = Array.from(value).length;
  if (length < 8 || length > 64 || !/\p{L}/u.test(value) || !/\p{Nd}/u.test(value)) {
    return { password: true };
  }
  if (new TextEncoder().encode(value).length > 72) {
    return { passwordBytes: true };
  }
  return null;
};

/** Mirrors the backend name rule: letters (any script), spaces and . ' - ; 2-100 characters. */
export const fullNameValidator: ValidatorFn = (control) => {
  const value = ((control.value as string | null) ?? '').trim();
  if (!value) {
    return null;
  }
  return /^[\p{L}\p{M}][\p{L}\p{M} .'-]{1,99}$/u.test(value) ? null : { fullName: true };
};

/** Group-level check: the password may not equal the mobile number (raw, normalised or 10-digit). */
export function passwordNotMobileValidator(mobileKey: string, passwordKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const mobile = normalizeMobile(group.get(mobileKey)?.value as string | null);
    const password = group.get(passwordKey)?.value as string | null;
    if (!mobile || !password) {
      return null;
    }
    const national = mobile.startsWith('+91') ? mobile.slice(3) : mobile;
    const same = password === mobile || password === national || password === `+91${national}`;
    const passwordControl = group.get(passwordKey);
    if (passwordControl) {
      const { passwordEqualsMobile, ...rest } = passwordControl.errors ?? {};
      if (same) {
        passwordControl.setErrors({ ...rest, passwordEqualsMobile: true });
      } else if (passwordEqualsMobile) {
        passwordControl.setErrors(Object.keys(rest).length ? rest : null);
      }
    }
    return null;
  };
}

/**
 * Puts server field errors (e.g. `soil.ph`, `climate.humidityPct`, `password`) on the matching
 * controls as `{ server: message }`. Returns the fields that had no matching control.
 */
export function applyServerErrors(form: FormGroup, errors: FieldError[]): FieldError[] {
  const unmatched: FieldError[] = [];
  for (const error of errors) {
    const control = form.get(error.field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: error.message });
      control.markAsTouched();
    } else {
      unmatched.push(error);
    }
  }
  return unmatched;
}
