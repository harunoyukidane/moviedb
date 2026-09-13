# Implementation Phase 4 — Artwork (HTTP Media)

Status: ready to execute
Depends on: phase 1 (artwork_asset table), phase 3 (movie GraphQL, `Movie.artwork` field)
Reference: TECHNICAL_SPECIFICATION v0.1.md §8.1 (media endpoints), §10 (artwork design + person-image hybrid), §7.2 (artwork_asset), §13 (failure behaviour), §14 (security)

## Objective

Implement artwork upload, retrieval, and deletion as HTTP media endpoints (not GraphQL), behind an `ArtworkStore` abstraction. Validate by content (magic bytes/decode), enforce size/type limits, store outside Git on a managed volume, and swap metadata transactionally with compensating cleanup. Serve bytes with correct caching headers. Reuse the same validated path for person profile-photo upload (person-image hybrid, decision 1=b).

## Endpoints (§8.1)
```text
PUT    /api/movies/{movieId}/artwork    multipart/form-data field `file`
GET    /api/artwork/{artworkId}         cacheable binary response
DELETE /api/movies/{movieId}/artwork    remove association and stored bytes
```
Person photos reuse the same mechanism under a person-scoped route (e.g. `PUT /api/people/{personId}/photo`, `GET /api/artwork/{artworkId}`, `DELETE /api/people/{personId}/photo`). GraphQL projections expose the resulting `url`/metadata only. `Movie.artwork` (phase 3) is populated once this phase lands.

## `ArtworkStore` abstraction (§10)
- Interface: `put(stream, metadata) -> storageKey`, `open(storageKey) -> stream`, `delete(storageKey)`.
- `LocalArtworkStore` writes to a mounted volume; storage key is a server-generated UUID + safe extension (never the client filename/path). S3 impl is a documented future swap.

## Upload path (§10, in order)
1. Reject missing/empty/oversized streams before fully buffering (5 MiB limit -> `PAYLOAD_TOO_LARGE`).
2. Inspect magic bytes and decode with an image decoder; do not trust filename or `Content-Type` (`UNSUPPORTED_MEDIA_TYPE` on failure).
3. Allow only JPEG, PNG, WebP.
4. Server-generated UUID storage key + safe extension.
5. Stream to a temp file, compute SHA-256, atomically move into the managed volume.
6. Insert/swap `artwork_asset` metadata in a transaction (enforce `uq_movie_primary_artwork`, `byte_size` bounds).
7. Delete previous file only after replacement commits; orphan sweeper retries later.
8. Serve with `Content-Type`, `Content-Length`, caching/ETag, and `X-Content-Type-Options: nosniff`.

## Failure behaviour (§13)
- Invalid artwork rejected before permanent storage; temp file removed.
- File write succeeds but DB fails -> compensating delete of the new file; orphan sweeper as safety net.
- Old-artwork delete fails after replacement -> new metadata still valid; log/retry orphan cleanup.

## Tasks (TDD, in order)
1. `ArtworkStore` interface + `LocalArtworkStore` with unit tests (key generation, atomic move, delete, path-traversal rejection).
2. Content validator (magic-byte + decode) with unit tests across formats and adversarial inputs.
3. Upload/replace/delete use cases with transactional metadata swap + compensation; unit tests for compensation ordering.
4. Movie artwork HTTP controller + integration tests.
5. Person-photo controller reusing the same use cases + tests.
6. Serving controller with caching/ETag/nosniff headers + tests.
7. Orphan sweeper (scheduled best-effort) + test.

## Test requirements (adversarial/edge focus)
- Empty stream; truncated file; spoofed `Content-Type` (e.g. `.jpg` that is not an image); oversized (>5 MiB) -> `PAYLOAD_TOO_LARGE`; unsupported type (GIF/SVG/executable SVG blocked) -> `UNSUPPORTED_MEDIA_TYPE`.
- Filename traversal attempts (`../../etc/...`) never influence storage key/path.
- Duplicate artwork replacement: old file removed only after new commit; one primary artwork per movie enforced.
- Write-succeeds-DB-fails: new file compensated (deleted); no orphan leak after sweeper.
- Serving returns correct headers, 304 on matching ETag, 404 for unknown artwork id.
- Person photo: upload precedence over stored TMDB URL (hybrid); validation identical to movie path.

## Demo statement (phase acceptance)
- Upload, replace, and serve a movie poster and a person photo through validated multipart endpoints with correct caching headers.
- GraphQL `Movie.artwork` returns the URL/metadata after upload.
- All adversarial/edge tests pass; no orphaned files remain after failure-path tests.

## ADR
- ADR-4 (artwork stored locally behind abstraction, served over HTTP) — record with evidence. Note person-image hybrid (ADR-12) realized here.
