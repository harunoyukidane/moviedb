-- Trigram index for substring search (V2.5-01). `ix_person_name_lower` is a
-- B-tree on lower(name): it accelerates prefix matches (`term%`) but gives
-- the planner no seek point for the leading wildcard in `PersonRepository`'s
-- `%term%` substring search, so it falls back to a full scan. A GIN trigram
-- index is index-accelerated regardless of where the substring sits.
-- Additive: the existing B-tree index is kept for prefix/exact lookups
-- elsewhere (alphabet-jump pagination) and is unaffected by this migration.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_person_name_trgm ON person USING GIN (lower(name) gin_trgm_ops);
