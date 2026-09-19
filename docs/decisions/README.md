# Architecture Decision Records

```yaml
status: current
canonical_for: decision-log
last_verified: 2026-09-15
```

ADRs capture the significant, hard-to-reverse decisions and their rationale.
Numbering is historical (originally assigned in the technical specification,
now archived) and is kept stable for links; it no longer tracks a live
document's section numbers.

1. [Split Catalogue and People services by data ownership](0001-service-split-by-data-ownership.md)
2. [Use PostgreSQL and a normalized MovieCredit association](0002-postgresql-normalized-moviecredit.md)
3. [Expose GraphQL externally and gRPC internally](0003-graphql-external-grpc-internal.md)
4. [Store artwork locally behind an abstraction and serve bytes over HTTP](0004-artwork-local-store-http.md) — **amended by ADR-14**
5. [Use controlled genre/credit-role code tables while keeping roles on MovieCredit](0005-controlled-code-tables.md) — **amended by v2.1** to add `language_code`
6. [Physical deletion with reference protection under the no-retention assumption](0006-physical-deletion-reference-protection.md)
7. [Fixed TMDB IDs and an idempotent setup-time import through application interfaces](0007-fixed-tmdb-ids-idempotent-import.md)
8. [Offset pagination for the stated scale, documented keyset evolution path](0008-offset-pagination-keyset-evolution.md)
9. [Defer dedicated cache/search/message infrastructure until measured requirements justify it](0009-defer-search-cache-messaging.md)
10. [Application-generated UUIDv7 primary keys](0010-uuidv7-primary-keys.md)
11. [SvelteKit server-side BFF](0011-sveltekit-bff.md)
12. [Person profile images — stored locally, seeded from TMDB](0012-person-image-hybrid.md) — **amended by ADR-14**
13. [Credit last-write-wins](0013-credit-last-write-wins.md)
14. [Use MinIO object storage for deployed artwork](0014-minio-object-storage.md) — accepted and **implemented**; amends ADR-4 and ADR-12
15. [Put an edge reverse proxy in front of the BFF for response compression](0015-edge-reverse-proxy-compression.md) — accepted and **implemented**; the BFF is no longer published to the host directly (ADR-11 is otherwise unchanged)

Accepted ADRs include implementation evidence as their phases land. ADR-4 and
ADR-12 are kept as-written for their history rather than rewritten; each now
carries a status line pointing at ADR-14 for what changed. See
[verification/v2-acceptance.md](../verification/v2-acceptance.md) for current
implementation evidence.
