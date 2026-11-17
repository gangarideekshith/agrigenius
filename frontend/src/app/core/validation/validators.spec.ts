import { FormControl, FormGroup } from '@angular/forms';
import {
  applyServerErrors,
  fullNameValidator,
  indianMobileValidator,
  normalizeMobile,
  passwordNotMobileValidator,
  passwordPolicyValidator,
} from './validators';

describe('validators', () => {
  it('accepts Indian mobile numbers with optional +91, spaces and hyphens', () => {
    for (const ok of ['9876543210', '+919876543210', '98765 43210', '98765-43210']) {
      expect(indianMobileValidator(new FormControl(ok))).toBeNull();
    }
    for (const bad of ['5876543210', '987654321', '09876543210', '919876543210', 'abcdefghij']) {
      expect(indianMobileValidator(new FormControl(bad))).toEqual({ mobile: true });
    }
    expect(normalizeMobile(' 98765-43210 ')).toBe('9876543210');
  });

  it('enforces the password policy including the 72-byte limit', () => {
    expect(passwordPolicyValidator(new FormControl('abcdefg1'))).toBeNull();
    expect(passwordPolicyValidator(new FormControl('abcdefgh'))).toEqual({ password: true });
    expect(passwordPolicyValidator(new FormControl('12345678'))).toEqual({ password: true });
    expect(passwordPolicyValidator(new FormControl('a1'))).toEqual({ password: true });
    // 30 Devanagari letters (3 bytes each) + digit: <= 64 chars but > 72 bytes
    expect(passwordPolicyValidator(new FormControl('क'.repeat(30) + '1'))).toEqual({ passwordBytes: true });
  });

  it('rejects names with digits and accepts Indic scripts', () => {
    expect(fullNameValidator(new FormControl('Ravi Kumar'))).toBeNull();
    expect(fullNameValidator(new FormControl('రవి కుమార్'))).toBeNull();
    expect(fullNameValidator(new FormControl('Ravi 2'))).toEqual({ fullName: true });
  });

  it('flags a password equal to the mobile number in any format', () => {
    const group = new FormGroup(
      { mobileNumber: new FormControl('+91 98765 43210'), password: new FormControl('9876543210') },
      { validators: passwordNotMobileValidator('mobileNumber', 'password') },
    );
    expect(group.controls.password.hasError('passwordEqualsMobile')).toBe(true);
    group.controls.password.setValue('different1');
    expect(group.controls.password.hasError('passwordEqualsMobile')).toBe(false);
  });

  it('maps server field errors, including nested paths, onto form controls', () => {
    const form = new FormGroup({
      soil: new FormGroup({ ph: new FormControl(11) }),
      climate: new FormGroup({ humidityPct: new FormControl(120) }),
    });
    const unmatched = applyServerErrors(form, [
      { field: 'soil.ph', message: 'must be at most 10' },
      { field: 'climate.humidityPct', message: 'must be at most 100' },
      { field: 'unknownField', message: 'x' },
    ]);
    expect(form.get('soil.ph')?.errors?.['server']).toBe('must be at most 10');
    expect(form.get('climate.humidityPct')?.touched).toBe(true);
    expect(unmatched.map((u) => u.field)).toEqual(['unknownField']);
  });
});
