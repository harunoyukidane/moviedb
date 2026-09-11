# ADR-10: Application-generated UUIDv7 primary keys

Status: accepted

## Context
All entities need stable, non-guessable primary keys. Random UUIDv4 keys scatter inserts across the B-tree, causing page splits and index fragmentation. External `tmdb_id` is nullable provenance and cannot be the PK (user-created data has no TMDB id).

## Decision
Generate UUIDv7 (time-ordered) in Kotlin application code at entity creation. DB column type stays `UUID` with no DB-side default; do not use `gen_random_uuid()` (UUIDv4).

## Consequences
- Near-monotonic keys improve insert locality, reduce fragmentation and page splits, and speed recent-record range scans.
- Keys remain globally unique and opaque to clients.
- A small shared UUIDv7 utility is required in each service.
