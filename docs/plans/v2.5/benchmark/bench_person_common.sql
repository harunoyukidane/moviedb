-- Simulates the production searchByNamePattern query for a common term.
SELECT * FROM person p
WHERE lower(p.name) LIKE lower('%man%') ESCAPE '\'
ORDER BY lower(p.name) ASC, p.id ASC
LIMIT 20;
