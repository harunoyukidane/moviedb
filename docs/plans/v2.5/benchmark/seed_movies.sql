-- V2.5-01 benchmark seed: 100,000 synthetic movies (requirements.md ceiling).
-- Deterministic (setseed) so before/after runs compare the same data.
-- Titles are built from a small word list so common substrings ("man",
-- "love", "night") appear in a realistic double-digit-thousands fraction of
-- rows -- a search term that matches almost nothing tells us little about
-- scan cost; one that matches a meaningful slice is the interesting case.
SELECT setseed(0.42);

WITH words AS (
    SELECT ARRAY[
        'The','Last','Lost','Silent','Golden','Dark','Rising','Secret','Midnight',
        'Man','Woman','Love','Night','Day','Star','Shadow','Kingdom','Heart','War',
        'Dream','Fire','Ocean','City','Machine','Garden','River','Mountain','Storm',
        'Legacy','Journey','Empire','Circle','Mirror','Winter','Summer','Prophet'
    ] AS w
)
INSERT INTO movie (id, tmdb_id, title, original_title, synopsis, release_date, runtime_minutes, original_language, version, created_at, updated_at)
SELECT
    -- UUIDv7-shaped but random is fine here: only used as an opaque PK for this benchmark.
    gen_random_uuid(),
    100000000 + i,
    w[1 + floor(random() * array_length(w, 1))::int]
        || ' ' || w[1 + floor(random() * array_length(w, 1))::int]
        || CASE WHEN random() < 0.3 THEN ' ' || w[1 + floor(random() * array_length(w, 1))::int] ELSE '' END
        || ' ' || i::text,
    w[1 + floor(random() * array_length(w, 1))::int] || ' ' || w[1 + floor(random() * array_length(w, 1))::int] || ' (' || i::text || ')',
    '',
    date '1960-01-01' + (random() * 23000)::int,
    60 + floor(random() * 120)::int,
    (ARRAY['en','fr','ja','es','de'])[1 + floor(random() * 5)::int],
    0,
    now(),
    now()
FROM generate_series(1, 100000) AS i, words;

ANALYZE movie;
