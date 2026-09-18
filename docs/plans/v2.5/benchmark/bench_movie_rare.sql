-- Simulates a distinctive/rare search term (near-unique substring), the
-- pathological case for the B-tree-walk-in-title-order plan. :n varies per
-- iteration so the buffer cache can't trivially memoize a single row.
\set n random(1, 100000)
SELECT * FROM movie m
WHERE lower(m.title) LIKE lower('%' || :n || '%') ESCAPE '\'
   OR lower(m.original_title) LIKE lower('%' || :n || '%') ESCAPE '\'
ORDER BY lower(m.title) ASC, m.id ASC
LIMIT 20;
