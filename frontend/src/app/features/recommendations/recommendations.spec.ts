import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RecommendationResponse } from '../../core/api/models';
import { authInterceptor, errorInterceptor } from '../../core/auth/interceptors';
import { RecommendationHistoryPage } from './history';
import { NewRecommendationPage } from './new-recommendation';
import { RecommendationResultPage } from './recommendation-result';

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
  };
}

const VALID = {
  soil: { nitrogenKgHa: 90, phosphorusKgHa: 42, potassiumKgHa: 43, ph: 6.5, moisturePct: null, soilType: 'LOAMY' },
  climate: { temperatureC: 21, humidityPct: 82, rainfallMm: 203 },
  season: 'KHARIF',
  irrigationAvailable: 'yes',
  previousCrop: 'maize',
  location: { state: ' Telangana ', district: '' },
};

const LOW_CONFIDENCE: RecommendationResponse = {
  id: 'rec-1',
  status: 'LOW_CONFIDENCE',
  requestedAt: '2026-11-17T10:15:00Z',
  model: { name: 'crop-recommendation-rf', version: '1.0.0', dataLabel: 'DEMO_PUBLIC_DATASET' },
  featuresUsedByModel: ['rainfallMm'],
  candidates: [
    {
      rank: 1,
      crop: { code: 'kidneybeans', name: 'Kidney beans' },
      suitabilityScore: 42,
      probability: 0.4192,
      confidence: 'MEDIUM',
      explanation: [{ feature: 'rainfallMm', value: 1500, typicalRange: [60, 150], fit: 'ABOVE' }],
      economics: { available: false, reason: 'No verified market or cost data yet.' },
    },
  ],
  warnings: [{ code: 'INPUT_OUTSIDE_TRAINING_RANGE', feature: 'rainfallMm' }],
  advisory: 'Do not rely on these suggestions: please consult your local agriculture officer or KVK.',
};

describe('NewRecommendationPage', () => {
  it('blocks submission when required values are missing or out of range', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(NewRecommendationPage);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page.form.patchValue({ soil: { ph: 11 }, climate: { humidityPct: 120 } });
    page.submit();
    await fixture.whenStable();

    http.expectNone('/api/recommendations');
    expect(page.form.get('soil.ph')!.hasError('max')).toBe(true);
    expect(page.form.get('climate.humidityPct')!.hasError('max')).toBe(true);
    expect(page.form.get('soil.nitrogenKgHa')!.hasError('required')).toBe(true);
    const text = (fixture.nativeElement as HTMLElement).textContent!;
    expect(text).toContain('Must be at most 10.');
    expect(text).toContain('Please correct the highlighted fields.');
  });

  it('sends a clean request with nulls for blank optional fields and opens the result', async () => {
    const { http, navigate } = setup();
    const fixture = TestBed.createComponent(NewRecommendationPage);
    await fixture.whenStable();
    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();

    const req = http.expectOne('/api/recommendations');
    expect(req.request.body).toEqual({
      soil: { nitrogenKgHa: 90, phosphorusKgHa: 42, potassiumKgHa: 43, ph: 6.5, soilType: 'LOAMY', moisturePct: null },
      climate: { temperatureC: 21, humidityPct: 82, rainfallMm: 203 },
      season: 'KHARIF',
      irrigationAvailable: true,
      previousCrop: 'maize',
      location: { state: 'Telangana', district: null },
    });
    req.flush(LOW_CONFIDENCE, { status: 201, statusText: 'Created' });
    expect(navigate).toHaveBeenCalledWith(['/recommendations', 'rec-1'], { state: { result: LOW_CONFIDENCE } });
  });

  it('maps nested server validation errors to the matching fields', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(NewRecommendationPage);
    await fixture.whenStable();
    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();
    http.expectOne('/api/recommendations').flush(
      { code: 'VALIDATION_FAILED', errors: [{ field: 'previousCrop', message: 'unknown crop code' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect(fixture.componentInstance.form.controls.previousCrop.errors?.['server']).toBe('unknown crop code');
  });

  it('explains when the ML service is unavailable', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(NewRecommendationPage);
    await fixture.whenStable();
    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();
    http.expectOne('/api/recommendations').flush({ code: 'ML_SERVICE_UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')!.textContent).toContain(
      'Crop advice is not available right now',
    );
  });
});

describe('RecommendationResultPage', () => {
  it('renders a LOW_CONFIDENCE result with warnings, advisory, demo label and no economics', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(RecommendationResultPage);
    fixture.componentRef.setInput('id', 'rec-1');
    await fixture.whenStable();
    http.expectOne('/api/recommendations/rec-1').flush(LOW_CONFIDENCE);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.status-banner')!.textContent).toContain('The model is not sure');
    expect(el.textContent).toContain('Demo model');
    expect(el.textContent).toContain('Your rainfall is outside the values the model learned from');
    expect(el.textContent).toContain('consult your local agriculture officer or KVK');
    expect(el.querySelector('.crop-name')!.textContent).toContain('Kidney beans');
    expect(el.querySelector('.chip-MEDIUM')!.textContent).toContain('Medium confidence');
    expect(el.textContent).toContain('above the usual range (60 to 150)');
    expect(el.textContent).toContain('Cost and profit information is not available yet.');
    expect(el.querySelector('[role="meter"]')!.getAttribute('aria-valuenow')).toBe('42');
  });

  it('shows a not-found error with retry', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(RecommendationResultPage);
    fixture.componentRef.setInput('id', 'missing');
    await fixture.whenStable();
    http.expectOne('/api/recommendations/missing').flush({ code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('We could not find this.');
    expect(el.querySelector('button')!.textContent).toContain('Try again');
  });
});

describe('RecommendationHistoryPage', () => {
  it('shows the empty state with a call to action', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(RecommendationHistoryPage);
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/recommendations/history').flush({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('You have not asked for crop advice yet.');
  });

  it('lists items and pages through history', async () => {
    const { http } = setup();
    const fixture = TestBed.createComponent(RecommendationHistoryPage);
    await fixture.whenStable();
    const first = http.expectOne((r) => r.url === '/api/recommendations/history');
    expect(first.request.params.get('page')).toBe('0');
    first.flush({
      items: [{ id: 'a', status: 'COMPLETED', requestedAt: '2026-11-17T10:00:00Z', topCrop: { code: 'rice', name: 'Rice' }, topConfidence: 'HIGH' }],
      page: 0,
      size: 10,
      totalItems: 11,
      totalPages: 2,
    });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Rice');
    expect(el.textContent).toContain('Ready');
    expect(el.textContent).toContain('Page 1 of 2');

    const next = Array.from(el.querySelectorAll<HTMLButtonElement>('.pager button')).find((b) => b.textContent!.includes('Next'))!;
    next.click();
    expect(http.expectOne((r) => r.url === '/api/recommendations/history').request.params.get('page')).toBe('1');
  });
});
