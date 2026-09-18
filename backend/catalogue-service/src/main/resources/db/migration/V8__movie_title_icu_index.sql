-- Alphabet-jump pagination (V2.4) needs the movie list ordered so an accented
-- title interleaves with its base letter instead of trailing after every
-- ASCII title - the database's default libc collation compares by raw code
-- point, so e.g. "Amelie" would otherwise sort after "Zodiac". This Postgres
-- build has ICU compiled in (the "und-x-icu" collation already exists with no
-- extension needed); this index lets ORDER BY/comparisons on
-- `lower(title) COLLATE "und-x-icu"` stay index-backed instead of forcing a
-- sort/seq scan.
CREATE INDEX ix_movie_title_lower_icu ON movie (lower(title) COLLATE "und-x-icu");
