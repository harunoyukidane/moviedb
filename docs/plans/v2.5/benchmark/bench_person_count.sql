-- Simulates the production countByNamePattern query (no LIMIT) actually run
-- by PeopleApplicationService.searchPeople on every non-blank search.
SELECT count(*) FROM person p
WHERE lower(p.name) LIKE lower('%man%') ESCAPE '\';
