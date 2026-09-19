# V2.6: accessibility regression + media-path hardening

```yaml
status: current
canonical_for: v2.6-backlog
last_verified: 2026-09-19
```

This plan is the output of a code review and gap analysis run on 2026-09-19
against `main` at `e4dd993`, covering the work in `d3837f7..e4dd993` (alphabet
pagination, search optimization, Lighthouse optimization). All eight items
below have since been implemented (see [Status](#status)).

**Read this first if you are picking the work up cold.** The review verified a
lot of this release as *correct* — see [What is already
right](#what-is-already-right) before touching the media path, so you don't
"fix" something that was done deliberately.

## Status

| Item | Area | Severity | Status |
|---|---|---|---|
| [V2.6-01](#v26-01-restore-keyboard-access-to-the-comboboxes) | Keyboard lockout in 3 combobox components | **P1 — WCAG 2.1.1 Level A** | done |
| [V2.6-02](#v26-02-make-the-a11y-gate-able-to-fail-again) | `svelte-ignore` defeats the a11y gate | P2 | done |
| [V2.6-03](#v26-03-give-the-orphan-sweeper-a-minimum-age-guard) | Sweeper can delete in-flight uploads | P2 | done |
| [V2.6-04](#v26-04-take-webp-encoding-off-the-request-thread) | Synchronous unbounded `cwebp` in upload path | P3 | done |
| [V2.6-05](#v26-05-stop-leaving-cwebps-output-stream-unconsumed) | Unread pipe on `cwebp` process | P3 | done |
| [V2.6-06](#v26-06-honour-accept-q-values-when-negotiating-webp) | `Accept: image/webp;q=0` still gets WebP | P4 | done |
| [V2.6-07](#v26-07-make-the-eager-image-count-responsive) | `EAGER_COUNT = 6` assumes desktop width | P4 | done |
| [V2.6-08](#v26-08-give-the-edge-proxy-a-healthcheck) | Edge proxy has no healthcheck | P4 | done |

## Implementation notes (2026-09-19)

- **V2.6-01/02**: keyboard pattern (`ArrowUp`/`ArrowDown`/`Enter`/`Escape`,
  `aria-activedescendant`) added to `CodeCombobox`, `CreditDialog`, and
  `PersonSearch`; options stay non-focusable `<li role="option">`. The 14
  failing tests now assert `role="option"` and each component gained a
  keyboard-path test. The blanket `svelte-ignore` comments are gone; the two
  remaining ones (dismiss-on-click dialog backdrops in `ConfirmDialog` /
  `CreditDialog`) are now one rule each with an inline justification, enforced
  by a new guard test in `src/source-guard.test.ts`.
- **V2.6-03**: `ArtworkStore` gained `listKeysWithAge()` (returns `StoredKey`
  with a last-modified `Instant`) alongside the existing `listKeys()`, so both
  sweepers can skip anything younger than a configurable min-age (default 15
  minutes, `catalogue.artwork.sweep.min-age` / `people.photo.sweep.min-age`)
  without reaching into the MinIO SDK from the sweeper.
- **V2.6-04**: chose the bounded-executor shape over moving the encode out of
  the request. `WebpEncoder` now gates `encode()` behind a `Semaphore`
  (`maxConcurrent`, default 4 per service instance), so a burst of uploads can
  no longer fork an unbounded number of concurrent `cwebp` processes; each
  caller still pays its own encode latency, but worst-case resource usage is
  now capped and explicit rather than tracking request concurrency.
- **V2.6-05**: `WebpEncoder` now uses `redirectOutput/-Error(DISCARD)` instead
  of `redirectErrorStream(true)`.
- **V2.6-06**: both serving controllers now parse `Accept` with
  `MediaType.parseMediaTypes` and only serve WebP when an explicit
  `image/webp` entry has `qualityValue > 0`; absent/wildcard Accept still
  falls back to the primary asset unchanged.
- **V2.6-07**: `MovieClusterView` and the people list page now measure their
  own grid's rendered `grid-template-columns` count on mount (and on resize)
  instead of a hardcoded `6`.
- **V2.6-08**: `proxy` in `compose.yaml` has a `wget --spider` healthcheck so
  `docker compose up --wait` gates on Caddy actually serving.

Not verified here: the Testcontainers-backed integration tests (Minio,
Postgres, GraphQL/gRPC contract tests) require Docker, which was unavailable
in the environment this round of fixes was implemented in. All non-Docker
unit/component tests pass in every module, including the new/updated ones
listed above.

## Verification state at the time of review

- **Frontend: 14 failing tests on a clean `main`** (`npm test` → 5 files, 14
  failed / 210 passed). All 14 are one root cause — V2.6-01 below.
- **Backend: green.** `./gradlew test '-PdockerApiVersion=1.44'` passes in full.
  This supersedes the note in
  [plans/v2.5/README.md](../v2.5/README.md#v25-02-push-search-offsetlimit-to-the-database),
  which recorded `MovieRepository.findAllByFilter` as failing with
  `ERROR: syntax error at or near "order"` as of 2026-09-19. It is not failing;
  that note has been corrected.
- Note for PowerShell: the flag must be quoted (`'-PdockerApiVersion=1.44'`) or
  the shell splits it at `=` and Gradle reports `Task '.44' not found`.

## V2.6-01: restore keyboard access to the comboboxes

**Severity: P1.** Two of the three cases are complete functional lockouts for
keyboard-only users, not degradations.

Commit `0b27e95` ("lighthouse fix") replaced the focusable `<button>` inside
each `<li role="option">` with a click/mousedown handler on the non-focusable
`<li>`, and suppressed the resulting warnings with `svelte-ignore`. The
motivation was sound — an interactive `<button>` inside `role="option"` is a
real ARIA violation (nested interactive content) that Lighthouse flags — but the
replacement removed the only keyboard-reachable control without putting the
combobox keyboard pattern in its place.

**Affected:**

| Component | Line | Consequence |
|---|---|---|
| [`CodeCombobox.svelte`](../../../frontend/src/lib/components/CodeCombobox.svelte) | 73 | **Lockout.** No keydown handler at all. |
| [`CreditDialog.svelte`](../../../frontend/src/lib/components/CreditDialog.svelte) | 150 | **Lockout.** Window keydown handles only Escape. |
| [`PersonSearch.svelte`](../../../frontend/src/lib/features/people/PersonSearch.svelte) | 92 | Degraded; the GET form still submits. |

**Why the first two are lockouts, specifically:**

- `CodeCombobox`: `selectedCode` is assigned *only* in `pick()`, and `pick()` is
  reachable *only* from the `<li>`'s `on:mousedown`. `onBlur` then clears the
  typed text when `selectedCode` is null (line 48). So a keyboard-only user
  cannot set a **language or country on any movie/person form** — the hidden
  input submits empty. `LanguageSelect` and `CountrySelect` are thin wrappers
  around this component, so both inherit it.
- `CreditDialog`: `canSubmit` requires `selectedPersonId` (line 105), also only
  assigned in `pick()`. A keyboard-only user **cannot add a credit at all**; the
  submit button never enables.

**Steps:**

1. Implement the standard combobox keyboard pattern on the **input**, not the
   options: `ArrowDown`/`ArrowUp` to move an active index, `Enter` to `pick()`
   the active option, `Escape` to close, `Home`/`End` optional. Keep the click /
   mousedown handlers for pointer users.
2. Track the active option with `aria-activedescendant` on the input plus a
   stable `id` per `<li>`, and set `aria-selected` on the active option. Do not
   reintroduce a focusable child inside `role="option"` — that is the ARIA
   violation this commit was correctly trying to remove. The options stay
   non-focusable; focus remains on the input throughout.
3. Add `aria-autocomplete="list"` to the `CodeCombobox` and `CreditDialog`
   inputs. `PersonSearch` already has it.
4. Preserve the existing `on:mousedown|preventDefault` focus-keeping behaviour in
   `CodeCombobox`/`PersonSearch` — it exists so blur doesn't close the list
   before the click lands. Don't drop it while refactoring.
5. Update the 14 failing tests to assert against the new structure
   (`role="option"` + accessible name, not `role="button"`), and **add a
   keyboard-path test per component** — type, `ArrowDown`, `Enter`, assert the
   hidden input / `selectedPersonId` is set. The current tests only ever
   exercised the mouse path, which is why the regression passed review.
6. Remove the `svelte-ignore` comments once the handlers exist (see V2.6-02).

**Failing tests to fix (all one root cause):** `CodeCombobox.test.ts` (6),
`LanguageSelect.test.ts` (3), `CreditDialog.test.ts` (3), `PersonSearch.test.ts`
(1), `routes/people/[id]/edit/page.svelte.test.ts` (1). Every failure reads
`Unable to find role="button" and name "…"`.

## V2.6-02: make the a11y gate able to fail again

**Severity: P2.** `npm run check` currently reports **0 errors** on a codebase
with a Level A keyboard failure in it. The README presents this command as the
a11y gate ("svelte-check (type + a11y)"), but the three `svelte-ignore
a11y-click-events-have-key-events a11y-no-noninteractive-element-interactions`
comments suppress exactly the rules that would have caught V2.6-01.

**Steps:**

1. Delete the three `svelte-ignore` comments as part of V2.6-01. With focus and
   key handling on the input and non-interactive options, the rules should pass
   on their own rather than needing suppression.
2. If any suppression genuinely remains necessary, narrow it to the single rule
   and add a one-line justification on the same comment — a blanket two-rule
   ignore on an element that takes user input should not pass review.
3. Consider a cheap guard so this can't silently recur: a lint/CI step that
   fails on new `svelte-ignore a11y-*` comments, or a grep-based check in the
   frontend test suite. Decide whether that is worth the friction; the review's
   position is that one such guard is cheaper than the next regression.

## V2.6-03: give the orphan sweeper a minimum-age guard

**Severity: P2.** Silent, permanent media loss; low probability, no detection.

[`ArtworkOrphanSweeper.sweepOnce`](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkOrphanSweeper.kt)
(line 41) deletes any key present in the store but absent from metadata, **with
no age threshold**. The upload path in
[`ArtworkUseCases.uploadMovieArtwork`](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkUseCases.kt)
writes the primary object (steps 4-5), *then* encodes and stores the WebP variant
(line 59), *then* commits the metadata transaction. The primary object is
therefore unreferenced for the whole encode.

V2.5's WebP work widened that window from milliseconds to **up to ~10 seconds**
(the `cwebp` timeout in `WebpEncoder`). A sweep landing inside it deletes the
bytes of an upload that then commits successfully — a movie row pointing at a
storage key that no longer exists, with no error surfaced to anyone.
[`PersonPhotoUseCases`](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/photo/PersonPhotoUseCases.kt)
(lines 49-59) has the identical ordering and the identical exposure.

**Steps:**

1. Only sweep objects older than a configurable threshold (~15 minutes is
   comfortably clear of the 10s encode timeout plus slow uploads). Both
   sweepers need it.
2. This needs object age from the store. Check whether `ArtworkStore.listKeys`
   can expose last-modified without breaking the port's storage-neutrality —
   MinIO's `listObjects` returns it; `LocalArtworkStore` has filesystem mtime.
   Prefer widening the port's return type over reaching into the MinIO SDK from
   the sweeper.
3. Add a test that an object written "just now" but not yet referenced survives
   a sweep, and one that an old unreferenced object is still removed. The
   existing sweeper tests assert only the second half.

## V2.6-04: take WebP encoding off the request thread

**Severity: P3.** `encodeAndStoreWebp` runs synchronously inside the upload
request, forking a `cwebp` process and writing two temp files per upload, with
**no concurrency bound**. It can add up to the full 10s timeout to a single
upload's latency. `demo/loadtest/` deliberately drives concurrent artwork
uploads against a small hot pool, so this is reachable under the project's own
stress test.

**Steps:**

1. Decide the shape first: a bounded executor around the encode, or move the
   variant generation out of the request entirely (write the primary, commit,
   then generate the variant asynchronously and attach it in a second
   transaction). The second removes the V2.6-03 window as a side effect but adds
   a state where an asset exists without its variant — which the serving path
   already handles, since `webpStorageKey` is nullable.
2. Whichever is chosen, cap concurrent `cwebp` processes explicitly rather than
   letting it track request concurrency.
3. Re-run `demo/loadtest/` and confirm upload latency and the unexpected-error
   count are unchanged.

## V2.6-05: stop leaving `cwebp`'s output stream unconsumed

**Severity: P3.**
[`WebpEncoder.encode`](../../../backend/media/src/main/kotlin/com/moviecatalogue/media/WebpEncoder.kt)
(line 37) sets `redirectErrorStream(true)` and then never reads the merged
stream. If `cwebp` ever writes enough to fill the OS pipe buffer it blocks on
write, `waitFor` blocks, and the encode burns its full 10s timeout before being
force-killed. `-quiet` makes this unlikely and the timeout bounds the damage, so
this is a latent fault rather than an active bug.

**Steps:** replace `redirectErrorStream(true)` with
`.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)`,
or consume the stream on a separate thread if the output is wanted for logging.
`DISCARD` is the simpler fix and loses nothing the code currently uses.

## V2.6-06: honour `Accept` q-values when negotiating WebP

**Severity: P4.** Cosmetic in practice; no real browser sends this.
[`ArtworkServingController`](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkServingController.kt)
(line 47) negotiates with `accept?.contains("image/webp")`, a plain substring
test, so a client explicitly refusing the format with `Accept: image/webp;q=0`
is still served WebP. `PersonPhotoController` does the same.

**Steps:** parse with Spring's `MediaType.parseMediaTypes` +
`MediaType.sortBySpecificityAndQuality`, or at minimum treat an explicit `q=0`
as a refusal. Keep the current fallback behaviour for `*/*` and absent headers
(primary format), which is correct.

## V2.6-07: make the eager-image count responsive

**Severity: P4.**
[`MovieClusterView.svelte`](../../../frontend/src/lib/features/movies/MovieClusterView.svelte)
(line 10) sets `EAGER_COUNT = 6` with the comment "the grid caps rows at 6
posters wide … so the first row is always above the fold". That holds at desktop
width only. On a phone the grid is ~2 columns, so the first 6 images span three
rows and the eager loading extends well below the fold — the opposite of the
intent. [`PersonListRow.svelte`](../../../frontend/src/lib/features/people/PersonListRow.svelte)
hardcodes the same `< 6`.

**Steps:** either lower the constant to the mobile column count (safe, gives up
some desktop preloading) or drive it from the actual rendered column count.
Measure with Lighthouse mobile before and after — this is an optimization, so it
should be justified by a number, not by reasoning.

## V2.6-08: give the edge proxy a healthcheck

**Severity: P4.** Surfaced while writing [ADR-15](../../decisions/0015-edge-reverse-proxy-compression.md),
not during the code review.

The `proxy` service in `compose.yaml` has no `healthcheck`. `scripts/setup.sh`
and `setup.ps1` both finish with `docker compose up -d --build --wait`, and
`--wait` only waits for a service *without* a healthcheck to reach **running**,
not to be serving. Since `${FRONTEND_PORT:-4173}` now maps to the proxy rather
than the BFF, `setup` can print "Browser UI: http://localhost:4173" a moment
before Caddy is actually listening.

In practice Caddy binds almost immediately and exits outright on a bad
Caddyfile (which `--wait` would catch as not-running), so this is a narrow race
rather than a likely failure. Worth closing because it is two lines and because
`setup` otherwise makes a point of waiting on real health checks rather than
sleeps.

**Steps:** add a healthcheck to the `proxy` service that probes its own `:80`
and let `--wait` gate on it. The stock `caddy:2.8-alpine` image has no `curl`,
so either use `wget --spider` (present in the Alpine base) or add an
`admin`-endpoint probe.

### Constraint to preserve while touching the proxy

Not a defect — a property that must not be broken. The BFF sets
`ADDRESS_HEADER: X-Forwarded-For` / `PROTOCOL_HEADER: X-Forwarded-Proto`, which
tell `adapter-node` to trust those headers. That is sound **only because the
proxy is the sole published entry point**: with `XFF_DEPTH` at its default of 1,
`adapter-node` reads the rightmost `X-Forwarded-For` entry, which is the one
Caddy appends, so a client-supplied value cannot win.

If `frontend` is ever republished to the host, a direct client could set those
headers itself and be believed. Remove the two env vars in the same change that
republishes it.

Current blast radius is nil: `getClientAddress()` is not called anywhere in the
frontend, and `ORIGIN` is set explicitly (so `adapter-node` uses it for origin
computation and ignores `PROTOCOL_HEADER` for that purpose, leaving CSRF origin
checks unaffected). This becomes a live concern only if something starts
consuming the client address — rate limiting, audit logging, an IP allowlist —
which is exactly when nobody re-examines the proxy assumption.

## What is already right

Verified during the review; do not "fix" these.

- **The orphan sweeper does account for WebP keys.**
  `findAllStorageKeys() + findAllWebpStorageKeys()` are unioned before the
  unreferenced check, so variants are not swept as orphans. (The V2.6-03 issue
  is about *age*, not about which keys are referenced.)
- **The BFF forwards `Accept` upstream**
  ([`media-proxy.ts`](../../../frontend/src/lib/server/media-proxy.ts), line 18)
  and passes `Vary` back through. Without this the whole WebP feature would be
  inert behind the BFF.
- **`Vary: Accept` is set on the 304 path as well as the 200 path**, and the
  ETag is per-variant (`webpSha256` vs `sha256`). This is the shared-cache
  correctness case that is usually missed.
- **The V2.5-02 offset pushdown is correct**: a separate count query supplies
  `total`, `OffsetPageRequest` is passed unsorted against native queries that
  carry their own ICU-collated `ORDER BY`, and clamping guarantees
  `limit >= 1` / `offset >= 0`, so the `require()` in `OffsetPageRequest` cannot
  be tripped from a request.
- **CLS is handled**, despite the images carrying no `width`/`height`
  attributes: every image container sets CSS `aspect-ratio` (2/3 for posters,
  1/1 for photos) with `width: 100%`, which reserves the box before load.
- **`inlineStyleThreshold` does not break the CSP.** Verified by building and
  running the app: the response carries
  `style-src 'self' 'nonce-…'` and the inlined `<style nonce="…">` matches.

## Sequencing

V2.6-01 and V2.6-02 are one change and should land first — they are the only
items that are both user-visible and currently shipping broken, and they restore
`main` to a green frontend suite. V2.6-03 is the next most valuable (silent data
loss) and is independent. V2.6-04 subsumes part of V2.6-03's window, so if both
are planned, decide V2.6-04's shape before implementing V2.6-03's guard.
V2.6-05 through V2.6-07 are opportunistic.
