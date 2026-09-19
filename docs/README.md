# Docs map

This is the only file agents/readers should load by default. Everything else is
loaded on demand, based on the task.

```yaml
status: current
canonical_for: doc-routing
last_verified: 2026-09-19
```

## Load by task

| Task | Load |
|---|---|
| Onboarding | root [README.md](../README.md) + [architecture/overview.md](architecture/overview.md) + [architecture/code-map.md](architecture/code-map.md) |
| Feature work | the relevant section of [product/requirements.md](product/requirements.md) + one file under [plans/v2/](plans/v2/) or [plans/v2.2/](plans/v2.2/README.md) + the ADRs it names |
| API / database work | the real sources of truth — [schema.graphqls](../backend/catalogue-service/src/main/resources/graphql/schema.graphqls), [people.proto](../backend/contracts/src/main/proto/catalogue/people/v1/people.proto), Flyway migrations under `backend/*/src/main/resources/db/migration/` — plus [architecture/interfaces.md](architecture/interfaces.md) and [architecture/data-model.md](architecture/data-model.md) for semantics not visible in the source itself |
| Operations / deployment | root README + [operations/runbook.md](operations/runbook.md) |
| Deciding something hard to reverse | [decisions/README.md](decisions/README.md), then write a new ADR |
| Checking what's actually done | [verification/README.md](verification/README.md) |
| Input validation / error messages / error codes | [plans/v2.2/README.md](plans/v2.2/README.md) — the reviewed state of the whole validation path and the open backlog against it |
| Security hardening (CSP, injection defenses) | [plans/v2.3/README.md](plans/v2.3/README.md) — done |
| Search / pagination scaling for large datasets | [plans/v2.5/README.md](plans/v2.5/README.md) — done (trigram index benchmarked; offset pushdown shipped for people search) |
| Accessibility, or anything touching the combobox/suggestion components | [plans/v2.6/README.md](plans/v2.6/README.md) — done; V2.6-01's keyboard-access regression is fixed |
| Media/artwork upload, WebP variants, orphan sweeping | [plans/v2.6/README.md](plans/v2.6/README.md) — done, plus [architecture/interfaces.md](architecture/interfaces.md) for the serving contract |
| Unified search, pagination bounds, or anything reading `offset` | [plans/v2.7/README.md](plans/v2.7/README.md) — done; V2.7-01's offset bound is in place |
| Validation copy / error messages shown to users | [plans/v2.7/README.md](plans/v2.7/README.md) (V2.7-04) + [plans/v2.2/README.md](plans/v2.2/README.md) for the validation path itself |
| CI, test coverage gaps | [plans/v2.7/README.md](plans/v2.7/README.md) (V2.7-05) + `.github/workflows/ci.yml` |
| Long/unbroken user text breaking a layout, or save-confirmation feedback | [plans/v2.8/README.md](plans/v2.8/README.md) — done (V2.8-01, V2.8-02); the real layout assertion is the Playwright test in `frontend/e2e/journey.spec.ts`, not the CSS tripwire in `app.css.test.ts` |
| Person birth/death dates vs. the movies they're credited on | [plans/v2.8/README.md](plans/v2.8/README.md) (V2.8-03) — done; the check belongs in Catalogue, never People (ADR-1), and all three write paths enforce it (person edit, add credit, movie release date) |
| "Back" navigation, or anything reading the previous page | [plans/v2.8/README.md](plans/v2.8/README.md) (V2.8-06) — done; it is a stack that pops, in `$lib/stores/navigation.ts` — a single "previous page" slot ping-pongs |

## Rules

- Never load `archive/` unless historical reasoning is explicitly needed (e.g. "why did v1 do X before the MinIO cutover?").
- Never load `archive/technical-spec-v0.2.md` as general project context — it has been split into the files above; it is kept only until anything it said turns out to be missing from the split.
- Real schema/proto/migration files are authoritative for shape. Docs are authoritative for intent and rationale, not for the exact current field list.

## Structure

```text
docs/
├── README.md                    this file
├── product/requirements.md      what the system does, v1 + v2, with status
├── architecture/
│   ├── overview.md              system boundaries, quality priorities, layering
│   ├── code-map.md              where code lives, package rules
│   ├── data-model.md            ownership, DDL, transaction boundaries
│   └── interfaces.md            GraphQL/gRPC/HTTP semantics (points at real schema/proto)
├── operations/runbook.md        TMDB seeding, resilience, security, observability
├── verification/                acceptance evidence, kept separate from strategy
├── decisions/                   ADRs (unchanged, one per file)
├── plans/v2/                    active v2 backlog (release hardening only; features archived)
├── plans/v2.1/                  post-v2 UI fixes backlog
├── plans/v2.2/                  validation + error-handling backlog
├── plans/v2.3/                  injection hardening (done)
├── plans/v2.5/                  search/pagination scaling (done — trigram index benchmarked, offset pushdown shipped)
├── plans/v2.6/                  a11y regression + media-path hardening (done; from the 2026-09-19 review)
├── plans/v2.7/                  search offset bounds + v2.6 follow-ups (done; from the 2026-09-19 second review)
├── plans/v2.8/                  long-word layout, save feedback, person-date/credit conflicts, back navigation (done)
└── archive/                     superseded content, historical reference only
```
