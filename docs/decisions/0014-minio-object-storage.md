# ADR-14: Use MinIO object storage for deployed artwork

Status: implemented
Date: 2026-09-13

## Context

V1 stores movie artwork and person photos in service-specific mounted filesystem volumes behind the shared `ArtworkStore` interface. This is safe for a single local node, but couples stored bytes to that node and does not exercise an object-storage deployment model. V2 requires an S3-compatible MinIO backend without changing observable upload or retrieval behavior.

The database remains the authority for media ownership and metadata. MinIO stores only validated bytes addressed by a server-generated key. PostgreSQL and MinIO cannot participate in one atomic transaction, so replacement and deletion must preserve the existing compensation and orphan-sweep behavior.

## Decision

- Add `MinioArtworkStore` in `backend/media` as an implementation of the existing `ArtworkStore` port.
- Use MinIO for Docker Compose and deployed service profiles.
- Use one MinIO deployment with separate `catalogue-artwork` and `people-photos` buckets.
- Give each application a separate identity and least-privilege policy scoped to only its bucket. Reserve root/admin credentials for bootstrap administration and never supply them to either service.
- Keep `LocalArtworkStore` for isolated development/unit tests, not as the normal Compose backend.
- Keep all external media HTTP routes and GraphQL metadata shapes stable.
- Generate object keys on the server from UUID plus a validator-approved extension. Client filenames never form keys.
- Store bucket names and credentials in service configuration/environment variables; never persist MinIO URLs or credentials in domain/database records.
- Use a one-shot administrative bootstrap to prepare buckets, service identities, and policies idempotently. Applications only validate their scoped access. Expose object-storage readiness separately from process liveness.
- Preserve database-first ownership semantics: upload new object, transactionally swap metadata, compensate the new object on rollback, delete the old object after commit, and sweep unreferenced objects later.

## Migration assumption

Existing v1 local media is assessment/demo data and will be discarded during cutover. Stale media references/data are cleared and the expanded deterministic importer reseeds posters and profile photos through service endpoints into MinIO. A future deployment with irreplaceable media must instead add a resumable copy-and-verify migration before switching configuration. Such a utility must preserve keys, verify size/SHA-256, and leave source files intact until the cutover is confirmed.

## Rollback

Switching `ARTWORK_STORAGE_TYPE` back to `local` is safe only when no `artwork_asset`/person `profile_path` row references an object that exists solely in MinIO. Once real uploads have gone to MinIO, flipping the config back without a data migration leaves metadata pointing at keys that don't exist on the local filesystem — every affected artwork/photo immediately 404s. Safe rollback requires either (a) reverting before any post-cutover MinIO uploads occurred, or (b) building the resumable copy-and-verify migration utility this ADR already defers (see "Migration assumption") to copy objects back to local storage first.

## Consequences

- Service containers become stateless with respect to media bytes and can be recreated without losing images.
- Both services reuse the same adapter without sharing metadata ownership.
- MinIO adds another operational dependency, credentials, health checks, and backup responsibility.
- `listKeys` may require paginated object listing; the adapter must hide pagination from callers.
- There is still no distributed transaction. Compensation, idempotent deletion, observability, and orphan sweeping remain necessary.
- S3 compatibility provides an evolution path to managed object storage, but MinIO-specific behavior must not leak into application use cases.

MinIO's documented multi-tenancy approach uses a shared deployment with dedicated buckets and IAM policies for isolation. Its IAM model denies operations that are not explicitly granted and supports scoped application access keys. This supports the selected one-cluster, two-bucket, two-identity topology: [MinIO multi-tenancy](https://docs.min.io/aistor/administration/multi-tenancy/) and [MinIO identity and access management](https://docs.min.io/aistor/administration/iam/).

## Alternatives considered

- **Keep mounted local volumes:** simplest, but does not meet the v2 requirement and remains node-bound.
- **Store bytes in PostgreSQL:** gives transactional metadata/bytes but increases database size and serving load and works poorly for cacheable binary delivery.
- **Expose presigned URLs directly:** could reduce service bandwidth, but would change the current same-origin security/caching boundary. It is deferred until access control and CDN requirements justify it.
- **Separate MinIO deployments per service:** strongest infrastructure isolation but disproportionate for the local topology. Use it only when compliance, geography, availability, or blast-radius requirements outweigh the operational cost; separate buckets and service identities otherwise retain the ownership boundary.
