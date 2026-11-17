import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ChatMessage,
  ChatSession,
  CreateRecommendationRequest,
  FarmerProfile,
  LanguageCode,
  Page,
  RecommendationResponse,
  RecommendationSummary,
  SendMessageResponse,
} from './models';

const API = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class FarmerApi {
  private readonly http = inject(HttpClient);

  me(): Observable<FarmerProfile> {
    return this.http.get<FarmerProfile>(`${API}/farmers/me`);
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
