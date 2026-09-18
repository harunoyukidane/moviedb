-- V2.5-01 benchmark seed: 500,000 synthetic people (requirements.md ceiling).
-- Deterministic (setseed) so before/after runs compare the same data.
SELECT setseed(0.42);

WITH first_names AS (
    SELECT ARRAY[
        'James','Mary','John','Patricia','Robert','Jennifer','Michael','Linda',
        'William','Elizabeth','David','Barbara','Richard','Susan','Joseph','Jessica',
        'Thomas','Sarah','Charles','Karen','Christopher','Nancy','Daniel','Lisa',
        'Matthew','Margaret','Anthony','Sandra','Mark','Ashley'
    ] AS w
),
last_names AS (
    SELECT ARRAY[
        'Smith','Johnson','Williams','Brown','Jones','Garcia','Miller','Davis',
        'Rodriguez','Martinez','Hernandez','Lopez','Gonzalez','Wilson','Anderson',
        'Thomas','Taylor','Moore','Jackson','Martin','Lee','Perez','Thompson',
        'White','Harris','Sanchez','Clark','Ramirez','Lewis','Robinson'
    ] AS w
)
INSERT INTO person (id, tmdb_id, name, biography, birth_date, death_date, place_of_birth, profile_path, version, created_at, updated_at)
SELECT
    gen_random_uuid(),
    200000000 + i,
    f.w[1 + floor(random() * array_length(f.w, 1))::int] || ' '
        || l.w[1 + floor(random() * array_length(l.w, 1))::int]
        || CASE WHEN random() < 0.02 THEN ' ' || i::text ELSE '' END,  -- rare numeric suffix to allow some duplicate-name collisions without forcing every row unique-looking
    '',
    date '1920-01-01' + (random() * 38000)::int,
    NULL,
    NULL,
    NULL,
    0,
    now(),
    now()
FROM generate_series(1, 500000) AS i, first_names f, last_names l;

ANALYZE person;
