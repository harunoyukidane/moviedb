# V2 plan

```yaml
status: current
canonical_for: v2-backlog
last_verified: 2026-09-15
```

Delivered as independently testable vertical slices. Preserve current public
media URLs and service ownership boundaries. A task is complete only when its
implementation, automated tests, configuration, and relevant documentation are
updated — then move its row here to ✅ and update [verification/v2-acceptance.md](../../verification/v2-acceptance.md).

Storage, catalogue, and frontend feature work are all done and have been
moved to [archive/v2-implementation/](../../archive/v2-implementation/README.md)
for the historical record. **[release.md](release.md) is the only item still
open.** v2.1 UI fixes (movie credits cluster view, people cluster view,
scrollable add-credit search, calendar date inputs) are tracked in
[plans/v2.1/README.md](../v2.1/README.md), not here.

## Status

| Area | File | Status |
|---|---|---|
| MinIO storage adapter, config-driven selection, Compose cutover, failure/rollback verification | [archive/v2-implementation/storage.md](../../archive/v2-implementation/storage.md) | ✅ done |
| Movie filters, expanded seed, comment persistence/GraphQL API | [archive/v2-implementation/catalogue.md](../../archive/v2-implementation/catalogue.md) | ✅ done |
| View toggle, cast/credits presentation, people photos, action icons, comment section UI | [archive/v2-implementation/frontend.md](../../archive/v2-implementation/frontend.md) | ✅ done |
| E2E, accessibility, Compose smoke test, docs/release checklist | [release.md](release.md) | ☐ not started (unblocked — all dependencies done) |

## Confirmed design decisions

These carry forward from the archived plans and remain in force for
[release.md](release.md) and any future v2.x work.

1. Discarded v1 local artwork during MinIO cutover and deterministically
   reseeded posters/profile photos into MinIO.
2. One MinIO deployment, separate service-owned buckets, separate
   least-privilege application identities; root/admin identity is bootstrap-only.
3. Expand the controlled genre seed and TMDB mapping together, then broaden the
   deterministic movie manifest to exercise those genres and multiple years.
4. Comments use an unauthenticated display name because authentication remains
   out of scope; names limited to 50 Unicode characters, text to 2,000.
5. V2 comments can be created and read but not edited or individually deleted.
6. Credit mutation (add/remove) lives only inside the Credit Editor, reached
   via an edit Icon Asset control on the movie/person detail page — matching
   the pre-existing read-only-detail-page structure. There is no separate
   per-Credit edit control for changing an existing credit's
   role/character/billing; add + remove together are the editing capability.
7. Removing a credit does not require confirmation: the person's data is
   untouched and the credit can be re-added at any time with no re-entry of
   data. Movie/person deletion keep their own labeled confirmation dialogs,
   never icon-only, because they destroy substantial hand-entered data that
   is materially harder to reconstruct.
8. Posting a comment is a form action directly on the Movie Detail page
   (`movies/[id]/+page.server.ts`), not on the `/edit` editor. This is a
   deliberate, narrow exception to the "detail page is read-only" pattern
   from decision 6: comments are additive/unmoderated content, not a "movie
   data" edit, so they don't belong behind the edit affordance.
