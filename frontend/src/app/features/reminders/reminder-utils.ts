import { Pipe, PipeTransform, inject } from '@angular/core';
import { LanguageCode, ReminderCategory } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';

export const DEFAULT_TIME_ZONE = 'Asia/Kolkata';

/** The browser's IANA time zone (falls back to India Standard Time). */
export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || DEFAULT_TIME_ZONE;
  } catch {
    return DEFAULT_TIME_ZONE;
  }
}

/** Current local date (YYYY-MM-DD) and time (HH:mm) in a time zone, optionally shifted. */
export function nowInZone(timeZone: string, offsetMs = 0): { date: string; time: string } {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(Date.now() + offsetMs));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '00';
  return { date: `${get('year')}-${get('month')}-${get('day')}`, time: `${get('hour')}:${get('minute')}` };
}

/**
 * True when a local date + time in {@code timeZone} is before now (with the same small grace the
 * server allows). Compares ISO strings, which sort chronologically.
 */
export function isInPast(date: string, time: string, timeZone: string): boolean {
  const now = nowInZone(timeZone, -60_000);
  return `${date}T${time.slice(0, 5)}` < `${now.date}T${now.time}`;
}

const LOCALES: Record<LanguageCode, string> = { en: 'en-IN', hi: 'hi-IN', te: 'te-IN', kn: 'kn-IN', ta: 'ta-IN' };

const CATEGORY_ICONS: Record<ReminderCategory, string> = {
  IRRIGATION: '💧',
  FERTILIZER: '🧪',
  PESTICIDE: '🛡️',
  WEEDING: '🌿',
  CROP_INSPECTION: '🔍',
  SOIL_TESTING: '🟫',
  HARVESTING: '🌾',
  SOWING: '🌱',
  GENERAL: '📌',
  CUSTOM: '📝',
};

export function categoryIcon(category: ReminderCategory): string {
  return CATEGORY_ICONS[category] ?? '📌';
}

/**
 * Formats an instant for the farmer: date and time in the reminder's own time zone (or the browser's),
 * in the current UI language. Impure so it follows language changes.
 */
@Pipe({ name: 'farmTime', pure: false })
export class FarmTimePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(instant: string | null | undefined, timeZone?: string | null, style: 'full' | 'date' = 'full'): string {
    if (!instant) {
      return '';
    }
    const options: Intl.DateTimeFormatOptions =
      style === 'date' ? { dateStyle: 'medium' } : { dateStyle: 'medium', timeStyle: 'short' };
    try {
      return new Intl.DateTimeFormat(LOCALES[this.i18n.language()] ?? 'en-IN', {
        ...options,
        timeZone: timeZone || undefined,
      }).format(new Date(instant));
    } catch {
      return new Date(instant).toLocaleString();
    }
  }
}
