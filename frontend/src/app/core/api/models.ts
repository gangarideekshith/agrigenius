/** Wire types for the Spring Boot API (docs/api/API_CONTRACT.md). */

export type LanguageCode = 'en' | 'hi' | 'te' | 'kn' | 'ta';
export const LANGUAGES: readonly LanguageCode[] = ['en', 'hi', 'te', 'kn', 'ta'];

export interface LoginRequest {
  mobileNumber: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: { id: string; role: string };
}

export interface RegisterRequest {
  fullName: string;
  mobileNumber: string;
  password: string;
  preferredLanguage: LanguageCode;
  email?: string | null;
  state?: string | null;
  district?: string | null;
  village?: string | null;
}

export interface FarmerProfile {
  id: string;
  fullName: string;
  mobileNumber: string;
  email: string | null;
  preferredLanguage: LanguageCode;
  state: string | null;
  district: string | null;
  village: string | null;
  addressLine: string | null;
  role: string;
  createdAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type SoilType = 'SANDY' | 'LOAMY' | 'CLAY' | 'SILT' | 'BLACK' | 'RED' | 'LATERITE' | 'ALLUVIAL';
export type Season = 'KHARIF' | 'RABI' | 'ZAID';

export interface CreateRecommendationRequest {
  soil: {
    nitrogenKgHa: number;
    phosphorusKgHa: number;
    potassiumKgHa: number;
    ph: number;
    soilType?: SoilType | null;
    moisturePct?: number | null;
  };
  climate: { temperatureC: number; humidityPct: number; rainfallMm: number };
  season?: Season | null;
  irrigationAvailable?: boolean | null;
  previousCrop?: string | null;
  location?: { state?: string | null; district?: string | null } | null;
}

export type RecommendationStatus = 'PENDING' | 'COMPLETED' | 'LOW_CONFIDENCE' | 'FAILED';
export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW';
export type Fit = 'WITHIN' | 'BELOW' | 'ABOVE';

export interface CropRef {
  code: string;
  name: string;
}

export interface RecommendationCandidate {
  rank: number;
  crop: CropRef;
  suitabilityScore: number;
  probability: number;
  confidence: Confidence;
  explanation: { feature: string; value: number; typicalRange: number[]; fit: Fit }[];
  economics: { available: boolean; reason: string | null };
}

export interface RecommendationResponse {
  id: string;
  status: RecommendationStatus;
  requestedAt: string;
  model: { name: string; version: string; dataLabel: string } | null;
  featuresUsedByModel: string[];
  candidates: RecommendationCandidate[];
  warnings: { code: string; feature: string | null }[];
  advisory: string | null;
}

export interface RecommendationSummary {
  id: string;
  status: RecommendationStatus;
  requestedAt: string;
  topCrop: CropRef | null;
  topConfidence: Confidence | null;
}

export type ChatMessageRole = 'USER' | 'ASSISTANT';
export type ChatMessageStatus = 'OK' | 'FAILED' | 'REFUSED' | 'NO_SOURCES';

export interface ChatSource {
  title: string;
  publisher: string;
  url: string;
  licence: string;
}

export interface ChatSession {
  id: string;
  title: string | null;
  language: LanguageCode;
  status: string;
  messageCount: number;
  createdAt: string;
  lastMessageAt: string | null;
}

export interface ChatMessage {
  id: string;
  role: ChatMessageRole;
  status: ChatMessageStatus;
  content: string;
  sources: ChatSource[];
  toolsUsed: string[];
  uncertainty?: string | null;
  language?: LanguageCode;
  createdAt: string;
}

export interface SendMessageResponse {
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
  usage: { remainingMessagesToday: number | null };
}

/** Crop codes known to the backend `crops` table (V3 seed). */
export const CROP_CODES: readonly string[] = [
  'apple', 'banana', 'blackgram', 'chickpea', 'coconut', 'coffee', 'cotton', 'grapes', 'jute', 'kidneybeans',
  'lentil', 'maize', 'mango', 'mothbeans', 'mungbean', 'muskmelon', 'orange', 'papaya', 'pigeonpeas',
  'pomegranate', 'rice', 'watermelon',
];
