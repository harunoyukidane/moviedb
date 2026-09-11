# ADR-12: Person profile image hybrid

Status: accepted

## Context
Seeded people carry a TMDB `profile_path`. Rendering it live would create a runtime TMDB dependency the design avoids; requiring uploads for every person would leave seeded people image-less.

## Decision
Hybrid model: the TMDB `profile_path` URL is captured once at import as provenance and displayed until replaced. The only in-app control is uploading a photo through the same validated `ArtworkStore` path used for movie artwork. An uploaded photo takes precedence over the stored URL. There is no in-app "paste URL" field.

## Consequences
- Seeded people have imagery without a live TMDB dependency.
- The create/amend/delete surface stays consistent and content-validated.
- Person-photo upload is sequenced after the core person/movie/credit flows.
