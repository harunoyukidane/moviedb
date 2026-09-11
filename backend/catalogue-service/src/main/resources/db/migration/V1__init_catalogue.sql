-- Catalogue database schema (TECHNICAL_SPECIFICATION §7.2).
-- Primary keys are application-generated UUIDv7 (ADR-10); no DB-side default.

CREATE TABLE movie (
    id UUID PRIMARY KEY,
    tmdb_id BIGINT UNIQUE,
    title VARCHAR(300) NOT NULL,
    original_title VARCHAR(300),
    synopsis TEXT NOT NULL DEFAULT '',
    release_date DATE,
    runtime_minutes INTEGER CHECK (runtime_minutes IS NULL OR runtime_minutes > 0),
    original_language VARCHAR(10),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE credit_category AS ENUM ('CAST', 'CREW');

CREATE TABLE credit_role_code (
    code VARCHAR(50) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    category credit_category NOT NULL,
    department VARCHAR(100),
    description TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (code, category),
    CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE TABLE genre_code (
    code VARCHAR(50) PRIMARY KEY,
    tmdb_id BIGINT UNIQUE,
    title VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE TABLE movie_genre (
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    genre_code VARCHAR(50) NOT NULL REFERENCES genre_code(code),
    PRIMARY KEY (movie_id, genre_code)
);

CREATE TABLE movie_credit (
    id UUID PRIMARY KEY,
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    person_id UUID NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    category credit_category NOT NULL,
    character_name VARCHAR(300),
    source_role_name VARCHAR(150),
    billing_order INTEGER CHECK (billing_order IS NULL OR billing_order >= 0),
    tmdb_credit_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (role_code, category)
      REFERENCES credit_role_code(code, category),
    CHECK (
      (category = 'CAST' AND character_name IS NOT NULL)
      OR
      (category = 'CREW' AND character_name IS NULL)
    )
);

CREATE UNIQUE INDEX uq_movie_credit_tmdb
    ON movie_credit (movie_id, tmdb_credit_id)
    WHERE tmdb_credit_id IS NOT NULL;

CREATE UNIQUE INDEX uq_movie_credit_manual
    ON movie_credit (
      movie_id,
      person_id,
      role_code,
      COALESCE(character_name, '')
    );

CREATE INDEX ix_movie_title_lower ON movie (lower(title));
CREATE INDEX ix_movie_genre_genre ON movie_genre (genre_code, movie_id);
CREATE INDEX ix_credit_movie_order ON movie_credit (movie_id, category, role_code, billing_order);
CREATE INDEX ix_credit_person ON movie_credit (person_id);
CREATE INDEX ix_credit_person_role ON movie_credit (person_id, role_code);

CREATE TABLE artwork_asset (
    id UUID PRIMARY KEY,
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 5242880),
    sha256 CHAR(64) NOT NULL,
    width INTEGER CHECK (width IS NULL OR width > 0),
    height INTEGER CHECK (height IS NULL OR height > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_movie_primary_artwork ON artwork_asset (movie_id);
