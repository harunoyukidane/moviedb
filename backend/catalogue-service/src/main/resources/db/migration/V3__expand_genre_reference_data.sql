-- Expand controlled genre reference data (V2-07). Append-only: never edit V2.
-- TMDB genre ids are the standard TMDB movie-genre list ids, used by the
-- importer's GENRE_MAP (demo/importer/src/mappings.ts) to assign genres from
-- imported TMDB content.

INSERT INTO genre_code
  (code, tmdb_id, title, description, display_order)
VALUES
  ('ACTION', 28, 'Action',
   'A genre centered on physical feats, conflict, and high-stakes set pieces.', 30),
  ('COMEDY', 35, 'Comedy',
   'A genre primarily intended to amuse and provoke laughter.', 40),
  ('CRIME', 80, 'Crime',
   'A genre centered on the planning, commission, or investigation of crime.', 50),
  ('DRAMA', 18, 'Drama',
   'A genre emphasizing realistic character development and emotional themes.', 60),
  ('MYSTERY', 9648, 'Mystery',
   'A genre centered on solving a puzzle, crime, or unexplained event.', 70),
  ('ROMANCE', 10749, 'Romance',
   'A genre centered on romantic relationships as the primary plot.', 80),
  ('THRILLER', 53, 'Thriller',
   'A genre built around suspense, tension, and high stakes.', 90);
