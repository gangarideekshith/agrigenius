import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AppNotification,
  ChatMessage,
  ChatSession,
  CreateRecommendationRequest,
  FarmerProfile,
  LanguageCode,
  Page,
  RecommendationResponse,
  RecommendationSummary,
  Reminder,
  ReminderListFilter,
  ReminderRequest,
  ReminderSummary,
  SendMessageResponse,
  UpdateProfileRequest,
} from './models';

const API = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class FarmerApi {
  private readonly http = inject(HttpClient);

  me(): Observable<FarmerProfile> {
    return this.http.get<FarmerProfile>(`${API}/farmers/me`);
  }

  updateMe(request: UpdateProfileRequest): Observable<FarmerProfile> {
    return this.http.put<FarmerProfile>(`${API}/farmers/me`, request);
  }
}

@Injectable({ providedIn: 'root' })
export class RecommendationApi {
  private readonly http = inject(HttpClient);

  create(request: CreateRecommendationRequest): Observable<RecommendationResponse> {
    return this.http.post<RecommendationResponse>(`${API}/recommendations`, request);
  }

  get(id: string): Observable<RecommendationResponse> {
    return this.http.get<RecommendationResponse>(`${API}/recommendations/${encodeURIComponent(id)}`);
  }

  history(page: number, size: number): Observable<Page<RecommendationSummary>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<RecommendationSummary>>(`${API}/recommendations/history`, { params });
  }
}

@Injectable({ providedIn: 'root' })
export class ChatApi {
  private readonly http = inject(HttpClient);

  createSession(language: LanguageCode, title?: string): Observable<ChatSession> {
    return this.http.post<ChatSession>(`${API}/chat/sessions`, title ? { language, title } : { language });
  }

  sessions(page: number, size: number): Observable<Page<ChatSession>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ChatSession>>(`${API}/chat/sessions`, { params });
  }

  deleteSession(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/chat/sessions/${encodeURIComponent(id)}`);
  }

  messages(sessionId: string, page: number, size: number): Observable<Page<ChatMessage>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ChatMessage>>(`${API}/chat/sessions/${encodeURIComponent(sessionId)}/messages`, {
      params,
    });
  }

  send(sessionId: string, content: string): Observable<SendMessageResponse> {
    return this.http.post<SendMessageResponse>(`${API}/chat/sessions/${encodeURIComponent(sessionId)}/messages`, {
      content,
    });
  }
}

@Injectable({ providedIn: 'root' })
export class ReminderApi {
  private readonly http = inject(HttpClient);

  create(request: ReminderRequest): Observable<Reminder> {
    return this.http.post<Reminder>(`${API}/reminders`, request);
  }

  update(id: string, request: ReminderRequest): Observable<Reminder> {
    return this.http.put<Reminder>(`${API}/reminders/${encodeURIComponent(id)}`, request);
  }

  get(id: string): Observable<Reminder> {
    return this.http.get<Reminder>(`${API}/reminders/${encodeURIComponent(id)}`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/reminders/${encodeURIComponent(id)}`);
  }

  complete(id: string): Observable<Reminder> {
    return this.http.post<Reminder>(`${API}/reminders/${encodeURIComponent(id)}/complete`, null);
  }

  cancel(id: string): Observable<Reminder> {
    return this.http.post<Reminder>(`${API}/reminders/${encodeURIComponent(id)}/cancel`, null);
  }

  reopen(id: string): Observable<Reminder> {
    return this.http.post<Reminder>(`${API}/reminders/${encodeURIComponent(id)}/reopen`, null);
  }

  list(filter: ReminderListFilter, timeZone: string, page: number, size: number): Observable<Page<Reminder>> {
    let params = new HttpParams().set('page', page).set('size', size).set('timeZone', timeZone);
    for (const [key, value] of Object.entries(filter)) {
      if (value !== null && value !== undefined && String(value).trim() !== '') {
        params = params.set(key, String(value).trim());
      }
    }
    return this.http.get<Page<Reminder>>(`${API}/reminders`, { params });
  }

  today(timeZone: string): Observable<Reminder[]> {
    return this.http.get<Reminder[]>(`${API}/reminders/today`, { params: new HttpParams().set('timeZone', timeZone) });
  }

  upcoming(days?: number): Observable<Reminder[]> {
    const params = days ? new HttpParams().set('days', days) : undefined;
    return this.http.get<Reminder[]>(`${API}/reminders/upcoming`, { params });
  }

  overdue(): Observable<Reminder[]> {
    return this.http.get<Reminder[]>(`${API}/reminders/overdue`);
  }

  summary(timeZone: string): Observable<ReminderSummary> {
    return this.http.get<ReminderSummary>(`${API}/reminders/summary`, {
      params: new HttpParams().set('timeZone', timeZone),
    });
  }
}

@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);

  list(unreadOnly: boolean, page: number, size: number): Observable<Page<AppNotification>> {
    const params = new HttpParams().set('unreadOnly', unreadOnly).set('page', page).set('size', size);
    return this.http.get<Page<AppNotification>>(`${API}/notifications`, { params });
  }

  unreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${API}/notifications/unread-count`);
  }

  markRead(id: string): Observable<AppNotification> {
    return this.http.post<AppNotification>(`${API}/notifications/${encodeURIComponent(id)}/read`, null);
  }

  markAllRead(): Observable<{ updated: number }> {
    return this.http.post<{ updated: number }>(`${API}/notifications/read-all`, null);
  }
}
