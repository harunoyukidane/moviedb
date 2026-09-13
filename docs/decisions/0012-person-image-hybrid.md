# ADR-12: Person profile images — stored locally, seeded from TMDB

Status: accepted (supersedes the original "hybrid" framing)

## Context
Seeded people have a TMDB `profile_path`. Rendering it live would create a runtime
TMDB dependency the design avoids; leaving seeded people image-less is poor. The
original decision was a *hybrid*: capture the TMDB `profile_path` URL as provenance
and display it until an uploaded photo replaced it.

## Decision
All person photos are stored locally through the same validated `ArtworkStore`
path used for movie artwork. `person.profile_path` holds the uploaded photo's
**storage key** (or null) — never a raw TMDB URL. The demo importer downloads each
person's TMDB profile image at seed time and uploads it through the person-photo
endpoint, so seeded people get real, locally-served photos. There is no in-app
"paste URL" field; the only control is uploading a photo.

This supersedes the original hybrid: because the importer now uploads photos, the
"display the TMDB URL until replaced" branch is unnecessary, and the code no longer
distinguishes a raw TMDB path from an uploaded key.

## Consequences
- Seeded people have imagery with **no runtime TMDB dependency** (bytes are stored
  on the managed volume).
- `person.profile_path` has a single, unambiguous meaning: the local storage key.
- Serving/deleting a photo needs no URL-vs-key sniffing; the value is always a key.

## Evidence
- People service reuses the shared `media` module's `ArtworkStore` +
  `ImageContentValidator` (identical validated path to movie artwork).
- `PersonPhotoUseCases`/`PersonPhotoController`: `PUT/DELETE/GET
  /api/people/{personId}/photo`; `profile_path` stores the uploaded key.
- The importer (`importMovie`) downloads the TMDB profile image and uploads it via
  the person-photo endpoint (mirrors the movie-poster flow); a failure is non-fatal.
- Tests: `PersonPhotoHttpIntegrationTest` (upload stores+serves, spoofed rejected,
  replace-removes-old, delete clears path, no-photo 404); importer photo/dedup and
  non-fatal-failure tests.
