# V2 storage: MinIO migration

```yaml
status: current
canonical_for: v2-storage-plan
last_verified: 2026-09-15
```

**Status: done.** Kept for the record of what was built and tested; see
[verification/v2-acceptance.md](../../verification/v2-acceptance.md) for
evidence and [ADR-14](../../decisions/0014-minio-object-storage.md) for the decision.

## V2-01: MinIO storage adapter — done

Implemented the `ArtworkStore` contract with an S3-compatible MinIO backend in
`backend/media`: `MinioArtworkStore` (`put`/`open`/`delete`/`exists`/`listKeys`),
server-generated UUID keys with validated safe extensions (never the client
filename), streamed upload/download, SHA-256 + byte size computed while
storing, sanitized error mapping for connectivity/auth/backend failures.
Covered by MinIO Testcontainers tests for all five operations, missing keys,
repeated delete, listing, content integrity, and adversarial/traversal-like keys.

## V2-02: Configuration-driven storage selection — done

Explicit properties (storage type, endpoint, access key, secret key, bucket,
region, TLS flag); `MinioArtworkStore` selected for Compose/runtime,
`LocalArtworkStore` retained for focused unit tests. Startup fails clearly if
MinIO config is incomplete or the pre-provisioned bucket is unavailable —
application credentials cannot create buckets/policies. Separate buckets
(`catalogue-artwork`, `people-photos`) and separate least-privilege identities
per service. No secrets in committed YAML; `.env.example` + Compose
interpolation. Readiness indicator for object storage, kept out of liveness.

## V2-03: Compose cutover — done

MinIO service with pinned version, health check, one-shot admin init service
(idempotent bucket/identity/policy provisioning). Catalogue/People depend on
MinIO readiness. `catalogue-artwork-data`/`person-artwork-data` local mounts
removed. Named MinIO data volume; setup/reset scripts manage only this
project's volume. Confirmed: local artwork data was discarded during cutover,
stale metadata/data reset, and the deterministic importer rerun so posters and
photos live in MinIO — no rows reference discarded local keys.

## V2-04: Failure and rollback verification — done

Verified: failed DB metadata swap compensates by deleting the newly uploaded
object; old objects deleted only after DB commit; orphan sweep tolerates
pagination/transient object-store failure; metadata-with-missing-object returns
404 not 500; `ETag`/cache headers/content-type/length/`nosniff` unchanged.
Metrics/logging added for MinIO operation failures, compensation failures, and
orphan sweep outcomes.
