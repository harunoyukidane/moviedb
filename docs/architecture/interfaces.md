# Interfaces

```yaml
status: current
canonical_for: api-semantics
last_verified: 2026-09-19
```

This file explains *semantics and contracts that aren't obvious from reading the
schema/proto alone* — error codes, batching guarantees, media endpoints. For exact
current field shapes, read the source:

- GraphQL SDL: [`schema.graphqls`](../../backend/catalogue-service/src/main/resources/graphql/schema.graphqls)
- gRPC/protobuf: [`people.proto`](../../backend/contracts/src/main/proto/catalogue/people/v1/people.proto)

## GraphQL conventions

- Input types for movie/person fields are explicit rather than reusing output
  types.
- Pagination `limit` is clamped to 1–100.
- Blank search text is rejected or treated as a normal list, consistently.
- Movie filters combine with AND semantics; changing a filter is a frontend
  navigation concern that resets offset to zero.
- Comments are ordered by `(createdAt DESC, id DESC)`, use server-generated
  timestamps, and reject blank/over-limit author or text values.

### Error contract

Expected errors use stable `extensions.code` values:

| Code | Meaning |
|---|---|
| `BAD_USER_INPUT` | Field/rule validation failed; includes field errors |
| `NOT_FOUND` | Requested movie/person/credit does not exist |
| `CONFLICT` | Duplicate record or stale optimistic-lock version |
| `PERSON_IN_USE` | Delete rejected because credits exist |
| `PAYLOAD_TOO_LARGE` | Artwork exceeds the configured limit |
| `UNSUPPORTED_MEDIA_TYPE` | Artwork signature/type is not allowed |
| `DEPENDENCY_UNAVAILABLE` | People Service failed or timed out |
| `INTERNAL_ERROR` | Unexpected failure; no stack trace exposed |

**Validation message text is, today, accidentally part of the contract**
(V2.7-04). `extensions.code: BAD_USER_INPUT` is the stable, intended
contract — the frontend's `messageForCode` always has a code-only fallback —
but [`errors.ts`](../../frontend/src/lib/errors.ts) additionally parses the
GraphQL error `message` string with regexes to render more specific copy
(field name, character class, bound). Nothing currently binds that prose to
the two services' `domain/TextRules.kt` / `Rules.kt`/`PersonRules.kt`
messages other than a same-day check; a `TextRulesFrontendContractTest` in
each service pins the current wording so a reword fails a backend test
instead of silently degrading to the generic `BAD_USER_INPUT` copy in the UI.
The structural fix — carrying a stable rule code in `extensions` alongside
`field`, so the frontend stops parsing English — is tracked but not done; see
[plans/v2.7/README.md](../plans/v2.7/README.md#v27-04-stop-parsing-backend-prose-in-the-frontend).

## Artwork/photo HTTP endpoints

Binary bytes never go through GraphQL — streaming, multipart transfer,
range/caching semantics, and binary responses fit HTTP better. GraphQL returns
only the resulting metadata and URL.

```text
PUT    /api/movies/{movieId}/artwork   multipart/form-data field `file`
GET    /api/artwork/{artworkId}        cacheable binary response
DELETE /api/movies/{movieId}/artwork   remove association and stored bytes
PUT    /api/people/{personId}/photo    multipart/form-data field `file`
GET    /api/people/{personId}/photo    cacheable binary response
DELETE /api/people/{personId}/photo    clear reference and remove stored bytes
```

## gRPC conventions

- Version the protobuf package (`v1`) and never reuse field numbers.
- Set deadlines on every client call (e.g. 1s for reads, 2s for mutations,
  locally).
- Map `INVALID_ARGUMENT`, `NOT_FOUND`, `ALREADY_EXISTS`, `ABORTED`,
  `FAILED_PRECONDITION`, and `UNAVAILABLE` deliberately.
- Cap batch size (e.g. 200 IDs), deduplicate IDs at the caller, and return
  results keyed by ID.
- Generate code during the build; do not hand-write duplicate DTO contracts.
- `CreatePersonRequest` includes `death_date` so a person can be created deceased
  in a single call — matches `PersonPatch` (update) and the `person` table.

## Request lifecycles

### Movie details (read)

1. Browser requests `/movies/{id}`.
2. SvelteKit sends a GraphQL query for the fields needed by the page.
3. Catalogue GraphQL resolver delegates to `GetMovieDetails`.
4. Catalogue repository loads the movie, credits, and artwork metadata in bounded
   queries.
5. The use case extracts distinct person IDs.
6. **One** batched `GetPeople` gRPC call is made with a deadline — no gRPC call
   per credit.
7. People Service queries its database in one `WHERE id IN (...)` query.
8. Catalogue maps people onto credits and returns the GraphQL projection.
9. Missing person IDs are represented as unavailable references and logged,
   rather than failing the whole movie page.
10. Svelte renders data and loads poster bytes from the media URL.

### Adding a credit (write)

1. UI selects an existing person, a controlled role code, and any role-specific
   character/order data.
2. GraphQL validates shape and required fields.
3. Catalogue calls `GetPerson` via gRPC to validate the logical reference.
4. A Catalogue DB transaction inserts the credit subject to uniqueness
   constraints.
5. The new credit is returned with hydrated person data.

There is no atomic transaction covering both services. The person can
theoretically be deleted after validation and before insertion; the race is made
very small because person deletion is orchestrated through the Catalogue GraphQL
API and rejected when referenced (see [data-model.md](data-model.md)). At
production scale, use a durable person-deletion workflow/event with idempotent
consumers rather than a distributed database transaction.

## Search

`search(query)` performs two operations within one bounded request: Catalogue DB
searches movie title/original title; People Service searches person names;
Catalogue DB also finds movies with credits for the returned person IDs. Results
are deduplicated and ranked: exact prefix title/name, substring title/name, then
related credit match. The frontend debounces input (~300ms) and cancels stale
requests; the server validates min/max query length and paginates results. `%`
and `_` are escaped so user input is literal. Escaped case-insensitive matching
remains the approach (ADR-9, no dedicated search engine), but the substring
`%term%` queries are now backed by `pg_trgm` GIN indexes rather than a scan —
see [V2.5-01](../plans/v2.5/README.md) for the benchmark that justified them.

## Media/MinIO upload path

1. Reject missing, empty, or oversized streams before fully buffering them.
2. Inspect magic bytes using an image decoder; do not trust filename or
   `Content-Type` alone.
3. Allow only JPEG, PNG, and WebP; decode to validate the file is an image.
4. Generate a server-side UUID storage key and safe extension — never the client
   path.
5. Stream to the object store through `ArtworkStore`, computing SHA-256 and byte
   size without trusting client-supplied metadata.
6. Insert/swap artwork metadata or the person photo reference in a service-local
   database transaction.
7. If the transaction rolls back, delete the new object as compensation. Delete
   the previous object only after commit; retry orphan cleanup later if needed.
8. Serve with correct `Content-Type`, `Content-Length`, caching/ETag, and
   `X-Content-Type-Options: nosniff`.

### Serving and WebP content negotiation (v2.5)

Uploads are stored in their validated primary format (JPEG/PNG/WebP). At upload
time the service additionally attempts a **best-effort WebP variant** via the
`cwebp` CLI; if the binary is missing or the encode fails, the asset simply has
no variant and is always served in its primary format. The variant is tracked by
nullable `webp_storage_key` / `webp_byte_size` / `webp_sha256` columns.

`GET /api/artwork/{id}` (and the person-photo equivalent) then negotiates:

- Serve the WebP variant when one exists **and** the request's `Accept` names
  `image/webp`; otherwise serve the primary asset. Absent or `*/*` `Accept`
  headers get the primary format.
- **`Vary: Accept` is set on every response, including the 304.** Without it a
  shared cache can hand one client's negotiated format to a client that asked
  for a different one.
- The `ETag` is the SHA-256 **of the bytes actually served**, so the primary and
  WebP representations carry different ETags and conditional requests stay
  correct across the negotiation.
- Caching is `public, max-age=31536000, immutable` — the storage key and its
  content never change for a given artwork id.

The BFF media proxy forwards the browser's `Accept` upstream and passes `Vary`
back through; without that forwarding the negotiation would never trigger behind
the BFF.

`ArtworkStore` (`put`, `open`, `delete`, `exists`, `listKeys`) is implemented by
`MinioArtworkStore` (deployed, ADR-14) and `LocalArtworkStore` (test adapter
only). Storage selection is configuration-driven; use cases and HTTP routes never
depend on MinIO SDK types directly. See [operations/runbook.md](../operations/runbook.md)
for bucket/identity topology.

## Related

- [Data model](data-model.md)
- [Requirements](../product/requirements.md)
- [ADR-3](../decisions/0003-graphql-external-grpc-internal.md), [ADR-4](../decisions/0004-artwork-local-store-http.md) (amended by ADR-14), [ADR-14](../decisions/0014-minio-object-storage.md)
