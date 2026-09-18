-- V2.2-05: `biography` had no length bound at any layer (TEXT, no CHECK), so a
-- single request could store an arbitrarily large document. The app now
-- enforces PersonRules.BIOGRAPHY_MAX = 5000 UTF-16 code units; this CHECK is
-- the backstop outside that rule, not a mirror of it.
--
-- char_length() counts code points, while the app counts UTF-16 code units, so
-- this bound is deliberately looser than the app rule (a non-BMP character is
-- 1 here, 2 there) — the outer fence, never the authority. See
-- docs/plans/v2.2/README.md ("Text length, and how it is counted").
ALTER TABLE person
    ADD CONSTRAINT person_biography_length_check CHECK (char_length(biography) <= 5000);
