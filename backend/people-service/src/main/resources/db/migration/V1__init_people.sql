-- People database schema (TECHNICAL_SPECIFICATION §7.3).
-- Primary keys are application-generated UUIDv7 (ADR-10); no DB-side default.

CREATE TABLE person (
    id UUID PRIMARY KEY,
    tmdb_id BIGINT UNIQUE,
    name VARCHAR(300) NOT NULL,
    biography TEXT NOT NULL DEFAULT '',
    birth_date DATE,
    death_date DATE,
    place_of_birth VARCHAR(300),
    profile_path VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (death_date IS NULL OR birth_date IS NULL OR death_date >= birth_date)
);

-- Case-insensitive name search support. Names are intentionally NOT unique:
-- different people can share a name; dedup for imports uses tmdb_id.
CREATE INDEX ix_person_name_lower ON person (lower(name));
