# ADR-4: Store artwork locally behind an abstraction and serve bytes over HTTP

Status: accepted

## Context
Movies (and person photos) need binary imagery. Pushing bytes through GraphQL is
awkward and defeats HTTP caching; trusting client filename/Content-Type is unsafe;
and storing bytes in the database or in Git is undesirable. The storage backend
should be swappable (local now, object storage later) without touching callers.

## Decision
- Define an `ArtworkStore` abstraction (`put`/`open`/`delete`/`exists`/`listKeys`)
  in a shared `media` module. `LocalArtworkStore` writes to a mounted volume with a
  server-generated `UUID.ext` key (never the client filename/path); an S3 impl is a
  documented future swap.
- Serve and mutate bytes via small dedicated HTTP endpoints, not GraphQL
  (§8.1): `PUT/DELETE /api/movies/{movieId}/artwork`, `GET /api/artwork/{id}`, and
  the person-photo equivalents. GraphQL exposes only the resulting URL/metadata.
- Validate by content (magic bytes + full image decode), allow only JPEG/PNG/WebP,
  cap at 5 MiB, and swap metadata transactionally with compensating file cleanup.

## Evidence (phase 4)
- Shared module `backend/media`: `ArtworkStore`, `LocalArtworkStore` (temp file +
  SHA-256 + atomic move, path-traversal-safe keys), `ImageContentValidator`
  (signature + decode). Unit tests: `LocalArtworkStoreTest`, `ImageContentValidatorTest`
  (adversarial: empty/truncated/spoofed/GIF/SVG/oversized).
- Catalogue: `ArtworkUseCases` (transactional swap + compensating delete + delete-old-
  after-commit), `MovieArtworkController`, `ArtworkServingController` (ETag, immutable
  cache, `X-Content-Type-Options: nosniff`, 304, 404), `ArtworkOrphanSweeper`
  (`@Scheduled` best-effort). Tests: `ArtworkUseCasesTest` (compensation ordering),
  `ArtworkHttpIntegrationTest` (upload/replace/serve/304/delete/404, spoofed/GIF
  rejected, filename traversal never influences the key, sweeper removes orphans).
- Volumes: `compose.yaml` mounts `catalogue-artwork-data` and `person-artwork-data`
  at `/var/lib/artwork`; paths come from `ARTWORK_STORAGE_PATH` /
  `PERSON_ARTWORK_STORAGE_PATH`.
- `Movie.artwork` (GraphQL, phase 3) returns the URL/metadata once an upload lands.

## Consequences
- Binary transport stays out of GraphQL; HTTP caching works via strong ETags.
- Content validation blocks spoofed/oversized/disallowed uploads before storage.
- Failure paths never leak orphans permanently: compensation on DB failure plus a
  periodic sweeper.
- Swapping to object storage means one new `ArtworkStore` impl and a bean change.
