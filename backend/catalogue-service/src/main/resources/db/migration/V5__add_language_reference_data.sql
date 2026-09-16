-- Controlled language reference data (v2.1). `movie.original_language` was a
-- free-text VARCHAR(10) with no controlled vocabulary; this adds a
-- `language_code` table (mirroring `genre_code`) and constrains the column to
-- it, so the frontend can offer a searchable dropdown instead of manual entry.
--
-- Codes are ISO 639-1, matching what TMDB's `original_language` field already
-- returns (see demo/importer/src/importer.ts), so existing/future imported
-- data is expected to match without translation.

CREATE TABLE language_code (
    code VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    CHECK (code ~ '^[a-z]{2,3}$')
);

INSERT INTO language_code (code, name, display_order) VALUES
  ('af', 'Afrikaans', 10), ('am', 'Amharic', 20), ('ar', 'Arabic', 30),
  ('as', 'Assamese', 40), ('az', 'Azerbaijani', 50), ('be', 'Belarusian', 60),
  ('bg', 'Bulgarian', 70), ('bn', 'Bengali', 80), ('bo', 'Tibetan', 90),
  ('bs', 'Bosnian', 100), ('ca', 'Catalan', 110), ('cs', 'Czech', 120),
  ('cy', 'Welsh', 130), ('da', 'Danish', 140), ('de', 'German', 150),
  ('el', 'Greek', 160), ('en', 'English', 170), ('eo', 'Esperanto', 180),
  ('es', 'Spanish', 190), ('et', 'Estonian', 200), ('eu', 'Basque', 210),
  ('fa', 'Persian', 220), ('fi', 'Finnish', 230), ('fr', 'French', 240),
  ('ga', 'Irish', 250), ('gl', 'Galician', 260), ('gu', 'Gujarati', 270),
  ('he', 'Hebrew', 280), ('hi', 'Hindi', 290), ('hr', 'Croatian', 300),
  ('ht', 'Haitian', 310), ('hu', 'Hungarian', 320), ('hy', 'Armenian', 330),
  ('id', 'Indonesian', 340), ('is', 'Icelandic', 350), ('it', 'Italian', 360),
  ('ja', 'Japanese', 370), ('jv', 'Javanese', 380), ('ka', 'Georgian', 390),
  ('kk', 'Kazakh', 400), ('km', 'Khmer', 410), ('kn', 'Kannada', 420),
  ('ko', 'Korean', 430), ('ku', 'Kurdish', 440), ('ky', 'Kyrgyz', 450),
  ('la', 'Latin', 460), ('lo', 'Lao', 470), ('lt', 'Lithuanian', 480),
  ('lv', 'Latvian', 490), ('mk', 'Macedonian', 500), ('ml', 'Malayalam', 510),
  ('mn', 'Mongolian', 520), ('mr', 'Marathi', 530), ('ms', 'Malay', 540),
  ('mt', 'Maltese', 550), ('my', 'Burmese', 560), ('ne', 'Nepali', 570),
  ('nl', 'Dutch', 580), ('no', 'Norwegian', 590), ('pa', 'Punjabi', 600),
  ('pl', 'Polish', 610), ('ps', 'Pashto', 620), ('pt', 'Portuguese', 630),
  ('ro', 'Romanian', 640), ('ru', 'Russian', 650), ('rw', 'Kinyarwanda', 660),
  ('si', 'Sinhala', 670), ('sk', 'Slovak', 680), ('sl', 'Slovenian', 690),
  ('so', 'Somali', 700), ('sq', 'Albanian', 710), ('sr', 'Serbian', 720),
  ('sv', 'Swedish', 730), ('sw', 'Swahili', 740), ('ta', 'Tamil', 750),
  ('te', 'Telugu', 760), ('tg', 'Tajik', 770), ('th', 'Thai', 780),
  ('tl', 'Tagalog', 790), ('tr', 'Turkish', 800), ('uk', 'Ukrainian', 810),
  ('ur', 'Urdu', 820), ('uz', 'Uzbek', 830), ('vi', 'Vietnamese', 840),
  ('wo', 'Wolof', 850), ('xh', 'Xhosa', 860), ('yi', 'Yiddish', 870),
  ('yo', 'Yoruba', 880), ('zh', 'Chinese', 890), ('zu', 'Zulu', 900);

-- Guard against any pre-existing free-text value that doesn't match the
-- controlled list (none expected against real TMDB-sourced data, but this
-- keeps the migration safe rather than failing outright on a stray value):
-- clear it, matching this schema's "no legal/audit retention" stance for
-- easily-recoverable metadata (the value can be re-entered from the dropdown).
UPDATE movie SET original_language = NULL
WHERE original_language IS NOT NULL
  AND original_language NOT IN (SELECT code FROM language_code);

ALTER TABLE movie
  ADD CONSTRAINT fk_movie_language FOREIGN KEY (original_language) REFERENCES language_code(code);
