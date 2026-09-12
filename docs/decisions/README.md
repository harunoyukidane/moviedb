# Architecture Decision Records

ADRs capture the significant, hard-to-reverse decisions and their rationale. Numbering follows §20 of the technical specification.

1. [Split Catalogue and People services by data ownership](0001-service-split-by-data-ownership.md)
2. [Use PostgreSQL and a normalized MovieCredit association](0002-postgresql-normalized-moviecredit.md)
3. [Expose GraphQL externally and gRPC internally](0003-graphql-external-grpc-internal.md)
4. [Store artwork locally behind an abstraction and serve bytes over HTTP](0004-artwork-local-store-http.md)
5. [Use controlled genre/credit-role code tables while keeping roles on MovieCredit](0005-controlled-code-tables.md)
6. [Physical deletion with reference protection under the no-retention assumption](0006-physical-deletion-reference-protection.md)
7. [Fixed TMDB IDs and an idempotent setup-time import through application interfaces](0007-fixed-tmdb-ids-idempotent-import.md)
8. Offset pagination for the stated scale, documented keyset evolution path.
9. [Defer dedicated cache/search/message infrastructure until measured requirements justify it](0009-defer-search-cache-messaging.md)
10. [Application-generated UUIDv7 primary keys](0010-uuidv7-primary-keys.md)
11. [SvelteKit server-side BFF](0011-sveltekit-bff.md)
12. [Person profile image hybrid](0012-person-image-hybrid.md)
13. [Credit last-write-wins](0013-credit-last-write-wins.md)

ADRs 1–9 are recorded with evidence as their phases land.
