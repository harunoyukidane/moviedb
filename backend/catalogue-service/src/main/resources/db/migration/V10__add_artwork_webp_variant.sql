-- Optional WebP variant generated alongside the primary JPEG/PNG artwork at
-- upload time (Accept-header content negotiation, served by ArtworkServingController).
-- Nullable: generation is best-effort (cwebp may be unavailable), and existing
-- rows never had a variant generated for them.
ALTER TABLE artwork_asset
    ADD COLUMN webp_storage_key VARCHAR(500),
    ADD COLUMN webp_byte_size BIGINT CHECK (webp_byte_size IS NULL OR webp_byte_size > 0),
    ADD COLUMN webp_sha256 CHAR(64);
