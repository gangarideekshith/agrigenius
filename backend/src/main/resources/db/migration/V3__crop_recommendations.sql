-- AI crop recommendation (Phase 1, Milestone 3).
-- crops: reference data; codes are the crop model's class labels.
-- Requests keep a typed snapshot of every input plus model identity for traceability;
-- failed requests are kept too (status FAILED + failure_code).

CREATE TABLE crops (
    code        VARCHAR(40) PRIMARY KEY,
    name_en     VARCHAR(80) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO crops (code, name_en) VALUES
    ('apple', 'Apple'),
    ('banana', 'Banana'),
    ('blackgram', 'Black gram'),
    ('chickpea', 'Chickpea'),
    ('coconut', 'Coconut'),
    ('coffee', 'Coffee'),
    ('cotton', 'Cotton'),
    ('grapes', 'Grapes'),
    ('jute', 'Jute'),
    ('kidneybeans', 'Kidney beans'),
    ('lentil', 'Lentil'),
    ('maize', 'Maize'),
    ('mango', 'Mango'),
    ('mothbeans', 'Moth beans'),
    ('mungbean', 'Mung bean'),
    ('muskmelon', 'Muskmelon'),
    ('orange', 'Orange'),
    ('papaya', 'Papaya'),
    ('pigeonpeas', 'Pigeon peas'),
    ('pomegranate', 'Pomegranate'),
    ('rice', 'Rice'),
    ('watermelon', 'Watermelon');

CREATE TABLE crop_recommendation_requests (
    id                    UUID PRIMARY KEY,
    user_id               UUID         NOT NULL
        CONSTRAINT fk_crop_rec_requests_user REFERENCES users (id) ON DELETE CASCADE,
    farm_id               UUID,        -- foreign key added once farms exist
    status                VARCHAR(20)  NOT NULL
        CONSTRAINT ck_crop_rec_requests_status CHECK (status IN ('PENDING', 'COMPLETED', 'LOW_CONFIDENCE', 'FAILED')),
    nitrogen_kg_ha        NUMERIC(6, 2) NOT NULL CONSTRAINT ck_crop_rec_n CHECK (nitrogen_kg_ha BETWEEN 0 AND 300),
    phosphorus_kg_ha      NUMERIC(6, 2) NOT NULL CONSTRAINT ck_crop_rec_p CHECK (phosphorus_kg_ha BETWEEN 0 AND 300),
    potassium_kg_ha       NUMERIC(6, 2) NOT NULL CONSTRAINT ck_crop_rec_k CHECK (potassium_kg_ha BETWEEN 0 AND 300),
    soil_ph               NUMERIC(4, 2) NOT NULL CONSTRAINT ck_crop_rec_ph CHECK (soil_ph BETWEEN 3 AND 10),
    temperature_c         NUMERIC(5, 2) NOT NULL CONSTRAINT ck_crop_rec_temp CHECK (temperature_c BETWEEN -5 AND 55),
    humidity_pct          NUMERIC(5, 2) NOT NULL CONSTRAINT ck_crop_rec_humidity CHECK (humidity_pct BETWEEN 0 AND 100),
    rainfall_mm           NUMERIC(7, 2) NOT NULL CONSTRAINT ck_crop_rec_rainfall CHECK (rainfall_mm BETWEEN 0 AND 5000),
    soil_type             VARCHAR(30)
        CONSTRAINT ck_crop_rec_soil_type CHECK (soil_type IN
            ('SANDY', 'LOAMY', 'CLAY', 'SILT', 'BLACK', 'RED', 'LATERITE', 'ALLUVIAL')),
    soil_moisture_pct     NUMERIC(5, 2) CONSTRAINT ck_crop_rec_moisture CHECK (soil_moisture_pct BETWEEN 0 AND 100),
    season                VARCHAR(10)
        CONSTRAINT ck_crop_rec_season CHECK (season IN ('KHARIF', 'RABI', 'ZAID')),
    irrigation_available  BOOLEAN,
    previous_crop_code    VARCHAR(40)
        CONSTRAINT fk_crop_rec_previous_crop REFERENCES crops (code),
    state                 VARCHAR(60),
    district              VARCHAR(60),
    model_name            VARCHAR(80),
    model_version         VARCHAR(40),
    model_sha256          VARCHAR(64),
    model_data_label      VARCHAR(40),
    warnings              JSONB        NOT NULL DEFAULT '[]',
    failure_code          VARCHAR(40),
    requested_at          TIMESTAMPTZ  NOT NULL,
    completed_at          TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_crop_rec_requests_user_time ON crop_recommendation_requests (user_id, requested_at DESC);

CREATE TABLE crop_recommendation_results (
    request_id         UUID         NOT NULL
        CONSTRAINT fk_crop_rec_results_request REFERENCES crop_recommendation_requests (id) ON DELETE CASCADE,
    rank               SMALLINT     NOT NULL CONSTRAINT ck_crop_rec_results_rank CHECK (rank BETWEEN 1 AND 5),
    crop_code          VARCHAR(40)  NOT NULL CONSTRAINT fk_crop_rec_results_crop REFERENCES crops (code),
    probability        NUMERIC(5, 4) NOT NULL CONSTRAINT ck_crop_rec_results_probability CHECK (probability BETWEEN 0 AND 1),
    suitability_score  SMALLINT     NOT NULL CONSTRAINT ck_crop_rec_results_score CHECK (suitability_score BETWEEN 0 AND 100),
    confidence_level   VARCHAR(10)  NOT NULL
        CONSTRAINT ck_crop_rec_results_confidence CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW')),
    explanation        JSONB        NOT NULL,
    CONSTRAINT pk_crop_recommendation_results PRIMARY KEY (request_id, rank)
);
