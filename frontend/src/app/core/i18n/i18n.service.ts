import { DOCUMENT } from '@angular/common';
import { Injectable, Pipe, PipeTransform, inject, signal } from '@angular/core';
import { LANGUAGES, LanguageCode } from '../api/models';
import en from '../../../assets/i18n/en.json';
import hi from '../../../assets/i18n/hi.json';
import kn from '../../../assets/i18n/kn.json';
import ta from '../../../assets/i18n/ta.json';
import te from '../../../assets/i18n/te.json';

type Dictionary = Record<string, unknown>;

const DICTIONARIES: Record<LanguageCode, Dictionary> = { en, hi, te, kn, ta };

/** Explicit choice made with the language switcher (not sensitive, so localStorage is fine). */
export const LANGUAGE_STORAGE_KEY = 'agrigenius.language';

/**
 * Tiny signal-based translation service. English is complete; the other languages are drafts and
 * fall back to English key by key. Templates use the `t` pipe.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);
  readonly language = signal<LanguageCode>('en');

  constructor() {
    const stored = this.readStoredChoice();
    this.apply(stored ?? 'en');
  }

  /** The language the user picked with the switcher, if any. */
  readStoredChoice(): LanguageCode | null {
    try {
      const value = localStorage.getItem(LANGUAGE_STORAGE_KEY);
      return isLanguage(value) ? value : null;
    } catch {
      return null;
    }
  }

  /** Called by the language switcher: applies and remembers the choice. */
  choose(language: LanguageCode): void {
    this.apply(language);
    try {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    } catch {
      // ignore unavailable storage
    }
  }

  /** Uses the profile's preferred language unless the user already chose one explicitly. */
  applyProfilePreference(language: LanguageCode | null | undefined): void {
    if (!this.readStoredChoice() && isLanguage(language)) {
      this.apply(language);
    }
  }

  /** Translates `key` (dot path), replacing `{name}` placeholders. Falls back to English, then the key. */
  t(key: string, params?: Record<string, string | number | null | undefined>): string {
    const raw = lookup(DICTIONARIES[this.language()], key) ?? lookup(DICTIONARIES.en, key) ?? key;
    if (!params) {
      return raw;
    }
    return raw.replace(/\{(\w+)\}/g, (match, name: string) => {
      const value = params[name];
      return value === undefined || value === null ? match : String(value);
    });
  }

  /** True if `key` exists in English (used to decide whether an error code has a message). */
  has(key: string): boolean {
    return lookup(DICTIONARIES.en, key) !== null;
  }

  private apply(language: LanguageCode): void {
    this.language.set(language);
    this.document.documentElement.lang = language;
  }
}

function lookup(dictionary: Dictionary, key: string): string | null {
  let node: unknown = dictionary;
  for (const part of key.split('.')) {
    if (typeof node !== 'object' || node === null) {
      return null;
    }
    node = (node as Dictionary)[part];
  }
  return typeof node === 'string' ? node : null;
}

export function isLanguage(value: unknown): value is LanguageCode {
  return typeof value === 'string' && (LANGUAGES as readonly string[]).includes(value);
}

/** `{{ 'login.title' | t }}` or `{{ 'chat.counter' | t: { count: n } }}`. Impure so it reacts to language changes. */
@Pipe({ name: 't', pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: string, params?: Record<string, string | number | null | undefined>): string {
    return this.i18n.t(key, params);
  }
}
