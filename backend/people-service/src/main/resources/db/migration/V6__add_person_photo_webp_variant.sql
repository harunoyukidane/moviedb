-- Optional WebP variant generated alongside the primary JPEG/PNG photo at
-- upload time (Accept-header content negotiation, served by PersonPhotoController).
-- Nullable: generation is best-effort (cwebp may be unavailable), and existing
-- rows never had a variant generated for them.
ALTER TABLE person
    ADD COLUMN profile_path_webp VARCHAR(500);
