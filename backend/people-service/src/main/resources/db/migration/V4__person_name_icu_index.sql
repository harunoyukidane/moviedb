-- Alphabet-jump pagination (V2.4) needs the people list ordered so an
-- accented name interleaves with its base letter instead of trailing after
-- every ASCII name - the database's default libc collation compares by raw
-- code point, so e.g. "Alvaro" would otherwise sort after "Zendaya". This
-- Postgres build has ICU compiled in (the "und-x-icu" collation already
-- exists with no extension needed); this index lets ORDER BY/comparisons on
-- `lower(name) COLLATE "und-x-icu"` stay index-backed instead of forcing a
-- sort/seq scan.
CREATE INDEX ix_person_name_lower_icu ON person (lower(name) COLLATE "und-x-icu");
