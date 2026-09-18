-- Simulates the production searchByTitlePattern query for a common term (~15% selectivity),
-- as SearchUseCases would issue it for a shallow page (fetchWindow = offset+limit, here 20).
SELECT * FROM movie m
WHERE lower(m.title) LIKE lower('%man%') ESCAPE '\'
   OR lower(m.original_title) LIKE lower('%man%') ESCAPE '\'
ORDER BY lower(m.title) ASC, m.id ASC
LIMIT 20;
