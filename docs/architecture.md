# Architecture

This is a stub completed in phase 8. See `spec/TECHNICAL_SPECIFICATION v0.1.md` for the authoritative design.

## Summary

- **SvelteKit web app (BFF)** — browser calls SvelteKit server routes; SvelteKit calls the Catalogue GraphQL API server-side.
- **Catalogue Service** (Kotlin/Spring Boot) — public GraphQL API, movie/credit/genre/artwork ownership, orchestration, gRPC client to People.
- **People Service** (Kotlin/Spring Boot) — authoritative people store, internal gRPC API only.
- **Two PostgreSQL databases** — one per service, separate credentials.
- **Artwork** — HTTP multipart upload / cacheable GET, stored on a local volume behind `ArtworkStore`.

```
SvelteKit (BFF) --GraphQL--> Catalogue Service --gRPC--> People Service
                              |  \--HTTP media (artwork)
                              v                                v
                      Catalogue PostgreSQL              People PostgreSQL
```

See `docs/decisions/` for ADRs.
