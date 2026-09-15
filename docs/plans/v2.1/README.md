# V2.1: post-v2 UI fixes

```yaml
status: current
canonical_for: v2.1-backlog
last_verified: 2026-09-15
```

Small UI fixes identified after using v2 (see [plans/v2/README.md](../v2/README.md)
for the v2 backlog these build on; [release.md](../v2/release.md) hardening
work is still separately open and unaffected by this file).

| Item | Status |
|---|---|
| Movie credits: cluster view, click-through to person page | ✅ done |
| People list: cluster view with a bigger photo | ✅ done |
| Add-credit dialog: scrollable person search results | ✅ done |
| Date fields: calendar widget | ✅ done (already satisfied by existing `<input type="date">`) |

## V2.1-01: Movie credits cluster view — done

The cast/creators lists on the movie detail page used a plain vertical list
(`CreditPersonRow`/`CreditSection`) with a small 2.5rem circular photo and no
navigation — visually inconsistent with the movie list's cluster view
([`MovieClusterView.svelte`](../../../frontend/src/lib/features/movies/MovieClusterView.svelte))
and a dead end (a credited person couldn't be reached from the movie page).

`CreditPersonRow.svelte` is now a photo-forward card matching the movie
poster-card idiom: a large square photo (fallback: the shared
`PersonPhotoFallback`), name, and role/character text, laid out in a grid.
`CreditSection.svelte`'s `<ul>` switched from a bespoke `.credit-section`
list to the new shared `.photo-grid` class in `app.css` (mirrors
`.poster-grid`, sized for photo cards instead of 2:3 posters).

The card links to `/people/{id}` only when the credited person is
**available**; an unavailable/deleted People reference (already shown as
"Unknown person") has no valid detail page and stays inert — rendered via
`<svelte:element this={available ? 'a' : 'span'}>` rather than a second
component, since Svelte 4 (this project's version) has no snippets to share
markup across a conditional tag without one.

Covered by `CreditPersonRow.test.ts` (new: links an available person to
`/people/{id}`; does not link an unavailable person) and the existing
`CreditSection.test.ts`/`CreditSection.hydration.test.ts`, unchanged and
still passing (markup content assertions, not layout-specific).

## V2.1-02: People list cluster view — done

The People list (`routes/people/+page.svelte`) rendered a full-width
horizontal row per person (`PersonListRow.svelte`) with a small 2.5rem photo
— most of the row was empty space, and the photo (the only genuinely
identifying visual) was too small to be useful.

`PersonListRow.svelte` is now a card: full-width circular photo (fallback:
`PersonPhotoFallback`, larger glyph), name, and dates, in the same
`.photo-grid` layout as credits. The page's `<ul>` switched from the
`.people-list` vertical-stack class to `.photo-grid`.

Covered by the existing `PersonListRow.test.ts`, updated only for the
renamed `.row-dates` → `.card-dates` class; all assertions (link href,
photo/fallback rendering, name/dates text) are unchanged and still pass.

## V2.1-03: Add-credit dialog scrollable search results — done

`CreditDialog.svelte`'s person-search suggestions list
(`.suggestions`) grew the whole dialog taller as more matches came back,
shifting the Role/Character/Billing fields and the Add/Cancel buttons
further down the page on every keystroke.

Added `max-height: 12rem; overflow-y: auto` to `.suggestions` so the
dialog's own size is fixed and the result list scrolls internally instead.
No markup or behavior change; covered by the existing `CreditDialog.test.ts`
(autocomplete selection still works unchanged).

## V2.1-04: Date fields use a calendar widget — done, no change needed

Checked all four date fields (movie release date, person birth/death date,
on both the `new` and `edit` forms in
`routes/movies/new+page.svelte`/`routes/movies/[id]/edit/+page.svelte`/
`routes/people/new/+page.svelte`/`routes/people/[id]/edit/+page.svelte`):
all already use `<input type="date">`, which every supported browser
(Chrome, Edge, Firefox, Safari) renders with a native calendar
date-picker widget. No code change was needed for this item.
