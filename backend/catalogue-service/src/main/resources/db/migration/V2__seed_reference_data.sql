-- Seed controlled reference data (TECHNICAL_SPECIFICATION §7.2).
-- Reference/code tables are maintained through Flyway; active=false retires a
-- code without invalidating historical rows.

INSERT INTO credit_role_code
  (code, title, category, department, description, display_order)
VALUES
  ('ACTOR', 'Cast Member', 'CAST', 'Acting',
   'Performs one or more characters in the movie.', 10),
  ('DIRECTOR', 'Director', 'CREW', 'Directing',
   'Leads the movie''s creative interpretation and directs its production.', 20),
  ('WRITER', 'Writer', 'CREW', 'Writing',
   'Develops the story, screenplay, or other written material used by the production.', 30),
  ('PRODUCER', 'Producer', 'CREW', 'Production',
   'Coordinates business and production responsibilities such as financing, staffing, schedule, and delivery; the exact remit varies by production.', 40);

INSERT INTO genre_code
  (code, tmdb_id, title, description, display_order)
VALUES
  ('HORROR', 27, 'Horror',
   'A genre intended primarily to evoke fear, dread, shock, or unease.', 10),
  ('PSYCHOLOGICAL_HORROR', NULL, 'Psychological Horror',
   'A horror subgenre emphasizing mental or emotional distress, identity, perception, paranoia, or unreliable experience over primarily physical threats.', 20);
