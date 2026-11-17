import { TestBed } from '@angular/core/testing';
import hi from '../../../assets/i18n/hi.json';
import kn from '../../../assets/i18n/kn.json';
import ta from '../../../assets/i18n/ta.json';
import te from '../../../assets/i18n/te.json';
import { I18nService, LANGUAGE_STORAGE_KEY } from './i18n.service';

describe('I18nService', () => {
  beforeEach(() => localStorage.clear());

  it('translates and substitutes parameters', () => {
    const i18n = TestBed.inject(I18nService);
    expect(i18n.t('dashboard.greeting', { name: 'Ravi' })).toBe('Namaste, Ravi');
  });

  it('falls back to English for keys missing in a draft language, then to the key itself', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.choose('te');
    expect(i18n.t('login.title')).toBe('సైన్ ఇన్ చేయండి');
    expect(i18n.t('rec.phHelp')).toContain('soil test'); // not translated yet -> English
    expect(i18n.t('no.such.key')).toBe('no.such.key');
  });

  it('remembers an explicit choice and updates the document language', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.choose('hi');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('hi');
    expect(document.documentElement.lang).toBe('hi');
  });

  it('uses the profile language only when the user has not chosen one', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.applyProfilePreference('kn');
    expect(i18n.language()).toBe('kn');
    i18n.choose('ta');
    i18n.applyProfilePreference('kn');
    expect(i18n.language()).toBe('ta');
  });

  it('marks every non-English dictionary as a draft needing review', () => {
    for (const dict of [hi, te, kn, ta]) {
      expect((dict as Record<string, unknown>)['_status']).toBe('DRAFT - needs native-speaker review');
    }
  });
});
