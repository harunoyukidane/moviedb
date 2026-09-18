-- Current pattern: fetch offset+limit rows from position 0 (offset=20000, limit=20).
SELECT * FROM person p
WHERE lower(p.name) LIKE lower('%on%') ESCAPE '\'
ORDER BY lower(p.name) ASC, p.id ASC
LIMIT 20020;
