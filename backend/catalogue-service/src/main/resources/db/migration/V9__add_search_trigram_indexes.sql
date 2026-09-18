-- Trigram index for substring search (V2.5-01). `ix_movie_title_lower` is a
-- B-tree on lower(title): it accelerates prefix matches (`term%`) but gives
-- the planner no seek point for the leading wildcard in `MovieRepository`'s
-- `%term%` substring search, so it falls back to a full scan. A GIN trigram
-- index is index-accelerated regardless of where the substring sits.
-- Additive: the existing B-tree index is kept for prefix/exact lookups
-- elsewhere (TMDB import dedup) and is unaffected by this migration.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_movie_title_trgm ON movie USING GIN (lower(title) gin_trgm_ops);
CREATE INDEX ix_movie_original_title_trgm ON movie USING GIN (lower(original_title) gin_trgm_ops);
