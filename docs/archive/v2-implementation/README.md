# V2 implementation plans — historical

```yaml
status: archived
canonical_for: none
last_verified: 2026-09-15
```

These three files were the active v2 feature plans for storage, catalogue,
and frontend work. All tasks in them are complete (see
[verification/v2-acceptance.md](../../verification/v2-acceptance.md) for
evidence). Kept for historical reasoning only — do not load them as current
context, and do not treat anything in them as still-open work.

- `storage.md` — MinIO adapter, config-driven selection, Compose cutover,
  failure/rollback verification (V2-01 through V2-04).
- `catalogue.md` — genre/year filters, expanded seed, comment persistence and
  GraphQL API (V2-05 through V2-07, V2-13, V2-14).
- `frontend.md` — cluster/list view toggle, credit/people photos, action
  icons, comment section UI (V2-08 through V2-12, V2-15).

The remaining v2 backlog item, release hardening (V2-16/17/18: accessibility
review, E2E/Compose smoke coverage, documentation/release checklist), was
**not completed** and was not archived — see
[plans/v2/release.md](../../plans/v2/release.md), still active. v2.1 UI
fixes (movie credits cluster view, people cluster view, scrollable add-credit
search, calendar date inputs) are tracked separately in
[plans/v2.1/README.md](../../plans/v2.1/README.md).
