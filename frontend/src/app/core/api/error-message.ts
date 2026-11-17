import { ApiError } from './api-error';
import { I18nService } from '../i18n/i18n.service';

/** A translated, user-facing description of an API error (never the raw server detail). */
export interface ErrorView {
  message: string;
  reference: string | null;
}

export function describeError(error: ApiError, i18n: I18nService): ErrorView {
  let message: string;
  if (error.code === 'TOO_MANY_REQUESTS' && error.retryAfterSeconds !== null) {
    message = i18n.t('errors.TOO_MANY_REQUESTS_WAIT', { seconds: error.retryAfterSeconds });
  } else if (i18n.has(error.messageKey)) {
    message = i18n.t(error.messageKey);
  } else if (error.status >= 500) {
    message = i18n.t('errors.INTERNAL_ERROR');
  } else {
    message = i18n.t('errors.UNKNOWN_ERROR');
  }
  const showReference = error.status >= 500 || error.code === 'INTERNAL_ERROR';
  return {
    message,
    reference: showReference && error.requestId ? i18n.t('common.requestId', { id: error.requestId }) : null,
  };
}
