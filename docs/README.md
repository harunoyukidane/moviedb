# Docs map

This is the only file agents/readers should load by default. Everything else is
loaded on demand, based on the task.

```yaml
status: current
canonical_for: doc-routing
last_verified: 2026-09-15
```

## Load by task

| Task | Load |
|---|---|
| Onboarding | root [README.md](../README.md) + [architecture/overview.md](architecture/overview.md) + [architecture/code-map.md](architecture/code-map.md) |
| Feature work | the relevant section of [product/requirements.md](product/requirements.md) + one file under [plans/v2/](plans/v2/) + the ADRs it names |
| API / database work | the real sources of truth — [schema.graphqls](../backend/catalogue-service/src/main/resources/graphql/schema.graphqls), [people.proto](../backend/contracts/src/main/proto/catalogue/people/v1/people.proto), Flyway migrations under `backend/*/src/main/resources/db/migration/` — plus [architecture/interfaces.md](architecture/interfaces.md) and [architecture/data-model.md](architecture/data-model.md) for semantics not visible in the source itself |
| Operations / deployment | root README + [operations/runbook.md](operations/runbook.md) |
| Deciding something hard to reverse | [decisions/README.md](decisions/README.md), then write a new ADR |
| Checking what's actually done | [verification/README.md](verification/README.md) |

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
├── plans/v2/                    active v2 backlog, split by feature area
└── archive/                     superseded content, historical reference only
```
