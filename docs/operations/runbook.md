# Operations runbook

```yaml
status: current
canonical_for: operations
last_verified: 2026-09-19
```

Setup/commands live in the root [README.md](../../README.md) — this file covers
the TMDB seeding workflow, resilience/failure behaviour, security baseline, and
observability, none of which belong in a getting-started doc.

## TMDB demo-data bootstrap

### What's committed vs. not

Committed: `scripts/setup.sh`/`setup.ps1`, the importer implementation,
`demo/tmdb-movie-ids.txt` (a bounded set of stable numeric movie IDs spanning
multiple genres/years), and configuration/mapping rules. **Never** committed:
downloaded JSON or images. The full TMDB daily-ID export is intentionally not
used — it's a valid-ID list, not a data export, and far larger than this demo
needs. See TMDB's [getting-started guide](https://developer.themoviedb.org/docs/getting-started),
[daily ID export description](https://developer.themoviedb.org/docs/daily-id-exports),
and [movie details endpoint](https://developer.themoviedb.org/reference/movie-details)
(supports `append_to_response`, making bounded per-movie details+credits import
appropriate).

### Import algorithm

Per manifest ID:

1. Fetch `/3/movie/{tmdbId}?append_to_response=credits` with `Authorization: Bearer $TMDB_READ_TOKEN` ([app authentication](https://developer.themoviedb.org/v4/docs/authentication-application)).
2. Keep a bounded subset: top 12 cast by order, plus Director/Writer(Screenplay)/Producer/Director of Photography/Editor/Original Music Composer crew.
3. Map TMDB genre IDs and crew job names through explicit version-controlled mappings to internal `GenreCode`/`CreditRoleCode` values — never create uncontrolled code-table rows from arbitrary remote strings.
4. Deduplicate people by TMDB person ID; upsert people (People gRPC), movie+genres, and credits (all keyed by `tmdb_id`/`tmdb_credit_id`) — reruns update rather than duplicate.
5. Download one moderate-size poster per movie via configuration base URL + size + `poster_path` ([image basics](https://developer.themoviedb.org/docs/image-basics)) and upload it through the application's artwork-upload path.
6. Record per-item outcomes; exit non-zero if required items fail.

Concurrency 4, request deadlines, exponential backoff with jitter for transient
429/5xx, honoring `Retry-After`. TMDB's documented upper limit is around 40
req/s and can change, so the importer stays deliberately conservative (see
[rate limiting](https://developer.themoviedb.org/docs/rate-limiting)).

### Reproducibility and failure policy

- Fixed movie IDs keep the catalogue semantically stable even as remote metadata evolves.
- Import is resumable and idempotent per movie/person/credit; a failed import does not roll back already-imported movies.
- Secrets never appear in logs, command-history examples, images, or Git; `.env.example` documents variables without values.
- Missing token → setup starts an empty, usable application and prints exact seeding instructions. It never fakes success.

### Attribution

Display the approved TMDB logo in `/about`, less prominent than the app brand,
with TMDB's required notice: "This product uses the TMDB API but is not endorsed
or certified by TMDB." TMDB's developer API is free for non-commercial use with
attribution per its [FAQ](https://developer.themoviedb.org/docs/faq) — re-check
terms before any commercial deployment.

## Resilience and failure behaviour

| Failure | Required behaviour |
|---|---|
| People Service timeout on movie detail | Return movie core data; affected people become `available: false`; log correlation ID |
| People Service unavailable on credit mutation | Reject with `DEPENDENCY_UNAVAILABLE`; do not insert unvalidated credit |
| Stale update | Reject with `CONFLICT`; UI offers reload, never silently overwrites |
| Duplicate credit | DB constraint + friendly `CONFLICT` |
| DB unavailable | Health is DOWN; request returns sanitized internal error |
| Invalid artwork | Reject before permanent storage; remove temp file |
| MinIO write succeeds, DB fails | Compensating delete of new object; orphan sweeper is a safety net |
| Old artwork delete fails after replacement | New metadata remains valid; log/retry orphan cleanup |
| MinIO unavailable | Media mutation/read returns a sanitized dependency failure; readiness is DOWN while liveness stays UP |
| Metadata references missing MinIO object | Media GET returns 404 and records an integrity signal; never an empty 200 or leaked SDK error |
| Orphan listing paginated/transiently fails | Adapter consumes all pages; sweep is best-effort, retries next schedule |
| Person missing during hydration | Preserve credit, return unavailable placeholder; surface integrity metric/log |
| Concurrent person delete/add-credit | Acknowledged cross-service race; mitigated via one public orchestration path; production evolution uses a durable workflow |
| TMDB 401 | Stop import with "invalid/missing token"; never retry indefinitely |
| TMDB 429/5xx | Bounded retry with jitter; report unresolved items |

Avoid automatic retries on non-idempotent client mutations unless an idempotency
key is designed. gRPC reads may retry once on `UNAVAILABLE`; deadlines are
mandatory.

## Security baseline

Even with authentication out of scope:

- Keep tokens/passwords in environment variables and a Git-ignored `.env`.
- Bind databases and People gRPC to the internal Compose network; publish only the ports the reviewer needs.
- Validate all input at the boundary and business-rule layer; use parameterized ORM/repository queries; escape search wildcards.
- Limit GraphQL query depth/complexity and pagination size. Disable schema introspection only in a real hardened production profile, not the review environment.
- Apply upload byte/type limits and generated storage paths; block traversal and executable SVG.
- Do not expose internal exception details or PII in logs.
- Add standard browser security headers and a narrow CORS origin. Run containers as non-root where practical.

If authorization enters scope, add it coherently at the public boundary and
define mutation permissions — don't bolt on a fake login screen.

## Edge proxy

- A Caddy container (`proxy`, config in [`proxy/Caddyfile`](../../proxy/Caddyfile))
  is the **only published port** — `${FRONTEND_PORT:-4173}` maps to it, not to
  the BFF. Anything that previously curled `localhost:4173` expecting to reach
  Node directly now goes through Caddy. See ADR-15.
- It applies `zstd`/`gzip` to every response, including the SSR'd HTML document.
  Brotli would need a custom `xcaddy` build and is deliberately not used.
- The BFF trusts `X-Forwarded-For`/`X-Forwarded-Proto` from it
  (`ADDRESS_HEADER`/`PROTOCOL_HEADER`). **This is only safe while the proxy is
  the sole published entry point** — if `frontend` is ever republished to the
  host, remove those two env vars with it, or client IP/protocol become
  spoofable.
- The proxy currently has **no healthcheck**; it starts after `frontend` reports
  healthy, so a proxy that is up but not yet listening is a narrow startup race.
  If `setup` ever reports success against an unreachable UI, check this first.
- Frontend dev outside Docker (`npm run preview`) bypasses the proxy entirely and
  therefore serves uncompressed documents. Lighthouse runs must go through the
  Compose stack to reflect production.

## MinIO topology

- One MinIO deployment, separate `catalogue-artwork` and `people-photos` buckets. Running two storage deployments is reserved for a demonstrated compliance/region/availability/blast-radius requirement.
- Each service gets only its own endpoint, bucket, region/TLS mode, and credentials via environment-backed configuration, and its own least-privilege identity scoped to its bucket. Root/admin credentials are bootstrap-only and never given to an application.
- A one-shot infrastructure bootstrap idempotently creates buckets/identities/policies with admin credentials; applications only validate access. Object-storage readiness is reported separately from liveness.
- MinIO data uses a project-scoped named Compose volume; service containers have no artwork filesystem mounts after cutover.

## Observability

- `/actuator/health/liveness` checks process health; readiness checks DB and required gRPC connectivity.
- Log JSON fields: timestamp, level, service, correlation ID, operation, entity ID, duration, outcome.
- Correlation ID propagates from GraphQL HTTP headers into gRPC metadata.
- Micrometer counters/timers for GraphQL operations, gRPC calls, import outcomes, MinIO operations/compensation/orphan sweeps, artwork failures, and missing person references.
- Never log artwork bytes, bearer tokens, biographies, or entire GraphQL payloads.
- Compose health checks establish startup order; `depends_on` alone is not readiness.

## Related

- [ADR-14](../decisions/0014-minio-object-storage.md) (MinIO), [ADR-6](../decisions/0006-physical-deletion-reference-protection.md) (physical deletion)
- [verification/README.md](../verification/README.md) for what's actually been exercised
