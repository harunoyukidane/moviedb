-- Comment seeding idempotency key (V2.2-13). Every other importer write is
-- idempotent by a provenance id (tmdb_id, tmdb_credit_id); comments had no
-- such key and cannot be individually deleted (v2 design decision 5), so a
-- naive importer rerun would duplicate the entire seeded set and break the
-- "import rerun produces the same counts" property the verification
-- checklist claims.
--
-- Nullable, with a PARTIAL unique index (WHERE seed_key IS NOT NULL) so
-- ordinary user-submitted comments - which never carry one - are unaffected.
ALTER TABLE movie_comment ADD COLUMN seed_key VARCHAR(100);

CREATE UNIQUE INDEX ux_movie_comment_seed_key ON movie_comment (seed_key) WHERE seed_key IS NOT NULL;
