# AgriGenius Product and Engineering Specification

## 1. Product definition

**Name:** AgriGenius – AI-Powered Smart Agriculture Management System.

**Product type:** Full-stack web platform for farmers and agriculture ecosystem participants.

**Goal:** Help farmers make better decisions across farm setup, crop selection, cultivation, weather response, soil management, disease monitoring, finances and market access.

**Delivery intent:** Industry-quality, secure, responsive, maintainable software—not a static UI prototype.

## 2. Users and roles

- FARMER
- ADMIN
- AGRICULTURAL_EXPERT
- BUYER
- VENDOR

All data access must enforce authentication, role authorization and resource ownership.

## 3. Phase 1 functional scope

### 3.1 Farmer registration and profile management
- Registration, login, secure password hashing, JWT authentication, password recovery.
- Profile details, image, contact information, preferred language, state/district/village/address and location.
- Multiple farms/plots, land area, ownership, irrigation, water availability and previous crops.
- Farmer can manage only their own authorized profile and farms.

### 3.2 AI crop recommendation
Inputs may include N/P/K, pH, moisture, soil type, temperature, humidity, rainfall, location, district/region, season, water/irrigation and crop history; incorporate market data only when sourced and available.
- Generate top five crop candidates.
- For each candidate, provide crop name, suitability score, explanation, soil fit, water needs, cultivation duration, expected yield, investment, revenue, profit and risks where reliable data supports these.
- Separate agronomic suitability from economic profitability.
- Do not allow profitability to override agronomic unsuitability.
- Record model version, input snapshot and prediction timestamp for traceability.

### 3.3 AI agriculture chatbot
- Answer questions on crop cultivation, soil, fertilizers, pests/diseases, irrigation, weather and markets.
- Planned languages: Telugu, Hindi, Kannada, Tamil and English.
- Chat sessions/history and contextual follow-up.
- Spring AI ChatClient, chat memory, tool calling, document retrieval, RAG and vector store.
- Application tools may access farmer profile, farm, soil, weather, crop recommendations, market and lifecycle data only after backend authorization.
- No fabricated current prices, weather, government benefits or scientific claims.
- Provide source attribution or indicate uncertainty where possible.

### 3.4 Weather forecast and smart alerts
- Integrate reliable forecast sources (for example Open-Meteo and available official Indian data services).
- Current/hourly/daily conditions, rainfall, temperature, humidity and wind.
- Heavy rain, extreme temperature, storm/wind and drought-related alerts when supported by data.
- Use farm coordinates.
- Scheduled retrieval, persistence, configurable thresholds, alert deduplication, Firebase push notifications and notification history/read status.
- Display source, retrieval time and forecast period.

## 4. Phase 2 functional scope

1. Plant disease detection: image upload/capture; secure backend-to-ML flow; confidence, symptoms, causes, prevention, expert-verified treatment and expert escalation for uncertain results. Validate type and size. Never present uncertain output as confirmed diagnosis or automatically prescribe pesticides.
2. Soil health analysis and fertilizer recommendation: soil report capture, units/ranges, interpretation and qualified nutrient recommendations.
3. Smart irrigation: combine crop stage, soil moisture, water availability and weather to support irrigation planning.
4. Crop lifecycle and task management: land preparation, seed selection, sowing, germination, growth, fertilizer, irrigation, flowering, pest monitoring and harvest; daily/weekly tasks, reminders, progress and weather-aware adjustments.
5. Market price intelligence and prediction: sourced mandi/market prices, historical data and clearly qualified forecasts.
6. Farm expense and financial management: expense categories, crop-cycle costs, harvest records, revenue and summaries.

## 5. Phase 3 functional scope

1. Government schemes and agricultural services: sourced scheme catalogue, eligibility information, deadlines and official references.
2. Farmer–buyer marketplace: listings, buyer/seller profiles, order lifecycle and authorization.
3. Admin dashboard: user/farmer/role/expert/crop/scheme/market/weather/disease management, analytics and audit logs.
4. Agricultural expert dashboard: review uncertain disease reports and AI predictions, provide guidance, verify advisory content and answer queries.
5. Final integration, security hardening, performance work, testing, documentation, containerization, backups and production deployment.

## 6. Non-functional requirements

- Security: secure authentication, RBAC, ownership checks, input validation, secure uploads, rate limiting, CORS, secret management, HTTPS and audit logging.
- Reliability: global error handling, retries/timeouts for external services, clear provider failure states, idempotent alert creation.
- Performance: indexes, pagination, efficient queries, caching only where useful.
- Usability: mobile responsive, accessible, farmer-friendly language, regional language readiness.
- Maintainability: modular boundaries, DTOs, service/repository separation, migrations, tests and current docs.
- Observability: structured logs, health endpoints, metrics and traceable prediction/provider metadata.
- Data governance: provenance, licensing, retention and privacy rules documented.

## 7. Technology requirements

- Angular/TypeScript, standalone components, lazy loading, Reactive Forms, HttpClient, guards/interceptors.
- Java 21 baseline; compatible Spring Boot 4.x (4.1.2, ADR-0002); Spring MVC, Data JPA, Hibernate, Security, JWT, Maven, Jakarta Validation, OpenAPI, Actuator and Flyway.
- PostgreSQL; optional Redis when justified.
- Python/FastAPI for specialized ML only; scikit-learn, Random Forest, XGBoost, TensorFlow/PyTorch, CNN/MobileNetV2/EfficientNet/ResNet50, OpenCV and time-series models as justified.
- Spring AI for LLM integration and RAG.
- Firebase Cloud Messaging; optional WebSockets, MQTT/ESP32 and cloud object storage.
- Docker, Git and GitHub.

Exact compatible versions must be confirmed during environment inspection and recorded before implementation.

## 8. Architecture constraints

Angular → Spring Boot → PostgreSQL.
Spring Boot → private Python ML service.
Spring Boot → external weather/market/notification providers.
Spring AI → vector store and verified knowledge sources.
Never expose provider secrets or private ML endpoints to Angular.
Begin as a modular monolith; split only with an ADR and a clear need.

## 9. Data model scope

Expected entities include users, farmer_profiles, farms, farm_locations, soil_details, soil_test_reports, crops, crop_recommendations, crop_cycles, farm_tasks, weather_records, weather_alerts, market_prices, mandis, disease_reports, irrigation_records, chat_sessions, chat_messages, notifications, expenses, harvest_records, government_schemes, marketplace_listings, buyers, orders and audit_logs.

Key relationships:
- Farmer 1—many farms.
- Farm 1—many soil reports.
- Farm 1—many crop cycles.
- Crop 1—many crop cycles.
- Crop cycle 1—many expenses and harvest records.
- Farmer 1—many chat sessions; session 1—many messages.
- Farm 1—many weather alerts.
- Each farm has its own location coordinates.

Finalize the ERD before implementing entity relationships; evolve the schema through Flyway.

## 10. UI/UX direction

- Dark green, leaf green and white.
- Responsive sidebar and dashboards.
- Clear cards, maps, charts, weather warnings and crop recommendation comparisons.
- Authentication, farmer, admin, expert and buyer/vendor layouts.
- Farmer dashboard: farm overview, crop recommendations, weather, alerts, crop lifecycle, market prices, financial summary and chatbot entry.
- Every control must have a real action and loading/empty/error/success states.

## 11. Acceptance principle

Each feature needs explicit acceptance criteria, API contract, data model, security checks, tests and documentation. Planned does not mean implemented. Mark a feature complete only after evidence from code and passing verification.
