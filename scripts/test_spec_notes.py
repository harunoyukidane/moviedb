# -*- coding: utf-8 -*-
"""Plain-English notes used to describe each test in test-specs.xlsx.

This file is prose, not logic. It exists so the workbook's Description column
reads as English to someone who has never seen the codebase — a reviewer, an
assessor, a new joiner — instead of restating the test's own name back at them.

Three things live here:

* SUBJECTS   — for each module under test, what it is and what it is for.
* OVERRIDES  — for integration suites, whose subject is a boundary rather than
               a class, the subject to use instead of whatever the automatic
               "most-referenced symbol" guess found.
* GLOSSARY   — plain readings of the jargon that appears in test names.

Keep the blurbs short, concrete and free of internal vocabulary. If a blurb
needs a term from GLOSSARY to make sense, add the term rather than the jargon.
"""

# ---------------------------------------------------------------------------
# Integration suites test a boundary (an API, a database, an HTTP endpoint),
# not one class, so name that boundary explicitly.
# ---------------------------------------------------------------------------

OVERRIDES = {
    "CatalogueGraphQlIntegrationTest": "@catalogue-graphql",
    "CatalogueSchemaIntegrationTest": "@catalogue-schema",
    "CatalogueRepositoryIntegrationTest": "@catalogue-db",
    "PeopleGrpcContractTest": "@people-grpc",
    "PeopleSearchIntegrationTest": "@people-search-db",
    "PersonRepositoryIntegrationTest": "@people-db",
    "ArtworkHttpIntegrationTest": "@artwork-http",
    "ArtworkMinioHttpIntegrationTest": "@artwork-http",
    "PersonPhotoHttpIntegrationTest": "@photo-http",
    "PersonPhotoMinioHttpIntegrationTest": "@photo-http",
    "AcceptsWebpTest": "@accepts-webp",
    "ArtworkStoreMinioSelectionTest": "@store-config",
    "ArtworkStoreLocalSelectionTest": "@store-config",
    "ArtworkStoreFailFastTest": "@store-config",
    "PhotoStoreMinioSelectionTest": "@store-config",
    "PhotoStoreLocalSelectionTest": "@store-config",
    "PhotoStoreFailFastTest": "@store-config",
    "CrossBucketIsolationTest": "@bucket-isolation",
    "MinioArtworkStoreIntegrationTest": "@minio-roundtrip",
    "TextRulesFrontendContractTest": "@text-rules-contract",
    "CreditRulesFrontendContractTest": "@credit-rules-contract",
}

# ---------------------------------------------------------------------------
# (short name, what it is and what it is for)
# Keyed by a Kotlin symbol, a frontend file path, or an "@" boundary token.
# ---------------------------------------------------------------------------

SUBJECTS = {
    # -- boundaries -----------------------------------------------------
    "@catalogue-graphql": (
        "Catalogue GraphQL API",
        "the public API the website talks to: every movie, credit and comment "
        "request arrives here. Runs against a real PostgreSQL database"),
    "@catalogue-schema": (
        "Catalogue GraphQL schema",
        "the published shape of that API — which fields exist, which may be "
        "empty, and which arguments are required. Breaking it breaks callers"),
    "@catalogue-db": (
        "Catalogue database",
        "the movie tables and the queries over them, checked against a real "
        "PostgreSQL instance so constraints and indexes are genuinely exercised"),
    "@people-grpc": (
        "People service internal API",
        "the private service-to-service interface the Catalogue uses to look "
        "people up. Never reachable from a browser"),
    "@people-search-db": (
        "People name search",
        "searching and paging the people list by name, against a real database"),
    "@people-db": (
        "People database",
        "the person table and its queries, against a real PostgreSQL instance"),
    "@artwork-http": (
        "Movie poster upload and download",
        "the web endpoints that receive a poster image and serve it back. "
        "Image bytes travel over plain HTTP rather than the GraphQL API"),
    "@photo-http": (
        "Person photo upload and download",
        "the equivalent endpoints for a person's profile photo"),
    "@accepts-webp": (
        "Image format negotiation",
        "deciding whether to serve the smaller WebP copy of an image or the "
        "original, based on what the browser says it can display"),
    "@store-config": (
        "Image storage selection at startup",
        "which storage backend a service picks up when it boots, and its "
        "refusal to start at all if that storage is misconfigured"),
    "@bucket-isolation": (
        "Storage access separation",
        "proof that each service's storage credentials can reach only its own "
        "bucket — the Catalogue cannot touch people's photos, and vice versa"),
    "@minio-roundtrip": (
        "Image storage round-trip",
        "storing and retrieving real files against a real MinIO server"),
    "@text-rules-contract": (
        "Error-wording agreement (text rules)",
        "a guard tying the server's validation wording to the phrasing the "
        "website relies on, so a reworded message cannot silently degrade the "
        "error a user sees"),
    "@credit-rules-contract": (
        "Error-wording agreement (credit rules)",
        "the same guard for the rules about cast and crew credits"),

    # -- backend: domain rules -------------------------------------------
    "MovieRules": (
        "Movie validation rules",
        "the checks every movie must pass before it can be saved — title "
        "length, a sensible release date, a positive running time"),
    "CreditRules": (
        "Cast and crew rules",
        "the checks on linking a person to a movie: cast need a character "
        "name, crew do not, and the job must match the cast/crew category"),
    "MovieCommentRules": (
        "Comment rules",
        "the checks on a posted comment — an author name and text, both "
        "present and within length"),
    "TextRules": (
        "Unsafe-character screening (movies)",
        "rejecting text that should never be stored: null bytes, invisible "
        "characters, and right-to-left overrides that can disguise what a "
        "value really says"),
    "PersonRules": (
        "Person validation rules",
        "the checks on a person's details — name, biography length, and dates "
        "that make sense (no future birthday, death after birth, no 200-year life)"),
    "DateHelpers": (
        "Date parsing",
        "reading and writing dates in the standard YYYY-MM-DD form"),
    "PersonSearch": (
        "Person search terms",
        "turning what a user typed into a safe database search pattern, so "
        "characters like % and _ are treated as text rather than wildcards"),
    "SearchPattern": (
        "Search terms",
        "turning typed text into a safe database search pattern"),

    # -- backend: use cases ----------------------------------------------
    "MovieUseCases": (
        "Movie create, update and delete",
        "the workflow behind saving a movie: apply the rules, check the "
        "genres exist, and write it in one transaction"),
    "MovieReadService": (
        "Movie page data",
        "loading everything one movie page needs — genres, credits, poster — "
        "in bounded queries"),
    "CreditUseCases": (
        "Adding and removing credits",
        "linking a person to a movie: confirm the person really exists in the "
        "People service first, then write the credit"),
    "PersonUseCases": (
        "Person create, update and delete",
        "the Catalogue's side of person changes, including refusing to delete "
        "someone who is still credited on a movie"),
    "CommentUseCases": (
        "Posting and listing comments",
        "adding a comment with a server-side timestamp, and reading comments "
        "back newest first"),
    "SearchUseCases": (
        "Site-wide search",
        "the single search box that returns both movies and people, including "
        "movies matched by a credited person's name"),
    "PersonHydrator": (
        "Filling in person details on credits",
        "a movie stores only a person's id, so their names are fetched from "
        "the People service — all of them in one batched call, not one per credit"),
    "ReferenceUseCases": (
        "Controlled lists",
        "serving the fixed vocabularies — genres, job titles, languages, "
        "countries — that the dropdowns are built from"),
    "PeopleApplicationService": (
        "Person records (People service)",
        "the authoritative create, read, update, delete and search for people. "
        "The Catalogue owns no person data of its own"),
    "PeopleGrpcClient": (
        "Calling the People service",
        "the Catalogue's client for the internal person API: batching lookups, "
        "and turning a timeout or outage into a clear failure rather than a crash"),
    "PersonRepository": (
        "Person storage queries",
        "the database queries behind the person list, name search and paging"),

    # -- backend: media ---------------------------------------------------
    "ArtworkStore": (
        "Image storage interface",
        "the narrow contract both services use to store images, so the actual "
        "backend can be swapped without touching upload logic"),
    "MinioArtworkStore": (
        "Image storage (MinIO)",
        "the deployed image store. Generates its own safe file keys and "
        "refuses any key that tries to escape its bucket"),
    "LocalArtworkStore": (
        "Image storage (local disk)",
        "the fast stand-in used by tests. Not used in a deployment"),
    "ImageContentValidator": (
        "Image checking",
        "confirming an upload really is a JPEG or PNG — by decoding it, not by "
        "trusting its filename — and is within the size limit"),
    "WebpEncoder": (
        "WebP conversion",
        "making a smaller WebP copy of an uploaded image. Best effort: if the "
        "converter is missing or fails, the upload still succeeds"),
    "ArtworkUseCases": (
        "Movie poster workflow",
        "replacing a poster in the right order — validate, store the new file, "
        "swap the database record, then remove the old file — and cleaning up "
        "the new file if the database step fails"),
    "PersonPhotoUseCases": (
        "Person photo workflow",
        "the same validate-store-swap-clean-up sequence for a profile photo"),
    "ArtworkOrphanSweeper": (
        "Leftover poster cleanup",
        "a scheduled sweep that deletes stored images no movie refers to any "
        "more, with an age guard so an upload in progress is never caught"),
    "PersonPhotoOrphanSweeper": (
        "Leftover photo cleanup",
        "the same scheduled sweep for profile photos"),
    "ArtworkExceptionAdvice": (
        "Poster upload error responses",
        "turning an upload failure into the right HTTP status instead of a "
        "generic server error"),
    "PhotoExceptionAdvice": (
        "Photo upload error responses",
        "the same mapping for profile photo uploads"),

    # -- backend: plumbing -------------------------------------------------
    "UuidV7": (
        "Record id generation",
        "generating the time-ordered unique ids used as primary keys, so new "
        "rows land together in the index instead of scattering"),
    "OffsetPageRequest": (
        "Paging arithmetic",
        "converting a page size and a starting position into a database query, "
        "with the bounds that stop an absurd request"),
    "parseId": (
        "Id parsing",
        "reading an id from a request and reporting a malformed one as a user "
        "input error rather than an internal fault"),
    "PersonData": (
        "Person data mapping",
        "converting a person from the People service's wire format into the "
        "shape the Catalogue's API returns"),
    "Movie": (
        "Movie record",
        "the stored movie row and its fields"),
    "UpdatePersonCommand": (
        "Person update request",
        "the set of changes submitted when editing a person"),
    "SearchPeopleCommand": (
        "People search request",
        "a name query with its paging position"),
    "CatalogueServiceApplication": (
        "Catalogue service startup",
        "how the Catalogue service boots and wires itself together"),
    "PeopleServiceApplication": (
        "People service startup",
        "how the People service boots and wires itself together"),

    # -- importer (demo seeding) -------------------------------------------
    "demo/importer/src/importer.ts": (
        "Demo data importer",
        "filling an empty installation with a browsable catalogue by calling the "
        "application's own APIs — the same person, movie, credit, artwork and "
        "comment endpoints a user goes through, never the database directly. It has "
        "to be safe to run twice (a second run must not duplicate anything) and it "
        "must keep going when one movie's data is incomplete"),
    "demo/importer/src/tmdb.ts": (
        "TMDB download client",
        "fetching movie, cast and poster data from the public TMDB service, and "
        "coping with everything a remote service does wrong — rate limits, "
        "outages, timeouts, missing posters and malformed replies — without "
        "taking the whole import down with it"),

    # -- frontend: shared logic --------------------------------------------
    "frontend/src/lib/errors.ts": (
        "Error messages shown to users",
        "turning a failure from the server into a sentence a person can act "
        "on, with a safe generic fallback when the cause is not recognised"),
    "frontend/src/lib/format.ts": (
        "Display formatting",
        "presenting dates and other stored values in readable form"),
    "frontend/src/lib/numberInput.ts": (
        "Whole-number field guard",
        "blocking the keystrokes that would put a negative or fractional value "
        "in a running-time or billing-order box, so it can never be typed at all"),
    "frontend/src/lib/dateBounds.ts": (
        "Date limits on forms",
        "the earliest and latest dates a date field will accept"),
    "frontend/src/lib/stores/navigation.ts": (
        "In-app Back link",
        "remembering where the user actually came from — the filtered list, "
        "the letter they were on, the movie they clicked a credit from — so "
        "Back returns there rather than to a reset list"),
    "frontend/src/app.css": (
        "Site-wide styling rules",
        "the shared stylesheet, including the rule that lets an unbroken "
        "300-character word wrap instead of stretching the page"),
    "frontend/src (whole source tree — repository-wide guard)": (
        "Repository-wide safety check",
        "a standing scan of the whole frontend for the few patterns that could "
        "bypass escaping and open a cross-site-scripting hole"),

    # -- frontend: BFF server layer ----------------------------------------
    "frontend/src/lib/server/operations.ts": (
        "Server-side API calls",
        "the typed set of queries and commands the website sends to the "
        "Catalogue"),
    "frontend/src/lib/server/request.ts": (
        "Shared request handling",
        "one place that attaches a correlation id to every outgoing call and "
        "converts a backend failure into a consistent page error"),
    "frontend/src/lib/server/validation.ts": (
        "Form checking before submission",
        "catching a malformed date or number in the browser request, with a "
        "clearer message than the server could give. The server still has the "
        "final say"),
    "frontend/src/lib/server/media-proxy.ts": (
        "Image relay",
        "passing an image through from the owning service to the browser "
        "without buffering it, keeping caching headers intact"),
    "frontend/src/lib/server/security-headers.ts": (
        "Security headers",
        "the protective headers set on every page response"),
    "frontend/src/lib/server/media.ts": (
        "Upload forwarding",
        "sending an uploaded image on to the service that owns it"),

    # -- frontend: shared components ---------------------------------------
    "frontend/src/lib/components/CodeCombobox.svelte": (
        "Searchable dropdown",
        "the type-to-filter picker behind genre, language and country fields. "
        "Built to the standard accessibility pattern, so it works by keyboard "
        "and announces itself to a screen reader"),
    "frontend/src/lib/components/LanguageSelect.svelte": (
        "Language picker",
        "choosing a movie's original language from the controlled list"),
    "frontend/src/lib/components/CountrySelect.svelte": (
        "Country picker",
        "choosing a person's country of birth from the controlled list"),
    "frontend/src/lib/components/GenreMultiSelect.svelte": (
        "Genre picker",
        "choosing one or more genres for a movie"),
    "frontend/src/lib/components/CreditDialog.svelte": (
        "Add-credit dialog",
        "the pop-up for crediting a person on a movie: find the person, pick "
        "the job, and name the character if it is a cast role"),
    "frontend/src/lib/components/DateField.svelte": (
        "Date field",
        "a date box with a calendar button, its allowed range, and its error "
        "message"),
    "frontend/src/lib/components/CharCounter.svelte": (
        "Character counter",
        "the live count under a length-limited box, counting in the same units "
        "the server enforces so the number never disagrees with what is accepted"),
    "frontend/src/lib/components/SearchBox.svelte": (
        "Search box",
        "the search input, including discarding a slow reply that arrives "
        "after a newer one"),
    "frontend/src/lib/components/ConfirmDialog.svelte": (
        "Confirmation dialog",
        "the 'are you sure?' step in front of a deletion"),
    "frontend/src/lib/components/StateBanner.svelte": (
        "Status banner",
        "the success, warning and error messages above a form, including "
        "moving focus to them so they are not missed"),
    "frontend/src/lib/components/StalenessBanner.svelte": (
        "Someone-else-edited warning",
        "an early warning that the record changed while this form was open. "
        "The server still does the real conflict check"),
    "frontend/src/lib/components/ArtworkUpload.svelte": (
        "Image upload control",
        "choosing a file, showing upload progress, and reporting the result"),
    "frontend/src/lib/components/AlphabetPager.svelte": (
        "A–Z jump bar",
        "jumping the people or movie list straight to a letter"),
    "frontend/src/lib/components/BackLink.svelte": (
        "Back link",
        "the link that uses the remembered previous page, falling back to a "
        "sensible default before any navigation has happened"),
    "frontend/src/lib/components/IconButton.svelte": (
        "Icon button",
        "an icon-only button that always carries a spoken name for screen "
        "readers and a tooltip for everyone else"),
    "frontend/src/lib/components/IconLink.svelte": (
        "Icon link",
        "the same, for a link rather than a button"),
    "frontend/src/lib/components/FieldError.svelte": (
        "Field error text",
        "the message shown under an individual form field"),

    # -- frontend: feature components --------------------------------------
    "frontend/src/lib/features/movies/MovieFilters.svelte": (
        "Movie filters",
        "filtering the movie list by genre and year, and clearing those filters"),
    "frontend/src/lib/features/movies/MovieListToolbar.svelte": (
        "Movie list toolbar",
        "the controls above the movie list — filters, view switch, paging"),
    "frontend/src/lib/features/movies/MovieListView.svelte": (
        "Movie list (rows)",
        "the compact one-per-row rendering of the movie list"),
    "frontend/src/lib/features/movies/MovieClusterView.svelte": (
        "Movie list (posters)",
        "the default poster-grid rendering, including the placeholder shown "
        "when a movie has no poster"),
    "frontend/src/lib/features/movies/MovieViewToggle.svelte": (
        "List/grid switch",
        "switching between the two movie layouts while keeping the current "
        "filters and position"),
    "frontend/src/lib/features/movies/MoviePosterFallback.svelte": (
        "Missing-poster placeholder",
        "what is drawn in place of a poster that does not exist"),
    "frontend/src/lib/features/credits/CreditSection.svelte": (
        "Cast and crew section",
        "the credits block on a movie page, grouped into cast and crew"),
    "frontend/src/lib/features/credits/CreditPersonRow.svelte": (
        "One credit row",
        "a single credited person — photo, name, role — and its remove action"),
    "frontend/src/lib/features/comments/CommentSection.svelte": (
        "Comments section",
        "reading and posting comments on a movie page"),
    "frontend/src/lib/features/people/PersonListRow.svelte": (
        "One person row",
        "a single person in the people list — photo, name, dates"),
    "frontend/src/lib/features/people/PersonSearch.svelte": (
        "Person search field",
        "the type-ahead used to find a person when adding a credit"),
    "frontend/src/lib/features/people/PersonPhotoFallback.svelte": (
        "Missing-photo placeholder",
        "what is drawn in place of a profile photo that does not exist"),

    # -- frontend: pages ----------------------------------------------------
    "frontend/src/routes/movies/+page.server.ts": (
        "Movie list page (server)",
        "reading the filters, view and page position out of the URL and "
        "loading the matching movies"),
    "frontend/src/routes/movies/+page.svelte": (
        "Movie list page",
        "how the movie list page is assembled and rendered"),
    "frontend/src/routes/movies/new/+page.server.ts": (
        "New movie form (server)",
        "checking a submitted new movie and creating it"),
    "frontend/src/routes/movies/[id]/+page.server.ts": (
        "Movie page (server)",
        "loading one movie with its credits, poster and comments"),
    "frontend/src/routes/movies/[id]/edit/+page.server.ts": (
        "Edit movie form (server)",
        "saving movie changes, adding and removing credits, uploading a "
        "poster, and deleting the movie"),
    "frontend/src/routes/movies/[id]/edit/+page.svelte": (
        "Edit movie form",
        "how the movie editor is assembled, including its confirmation and "
        "feedback behaviour"),
    "frontend/src/routes/people/+page.server.ts": (
        "People list page (server)",
        "loading the people list with its search term, letter and page position"),
    "frontend/src/routes/people/new/+page.server.ts": (
        "New person form (server)",
        "checking a submitted new person and creating them"),
    "frontend/src/routes/people/[id]/+page.server.ts": (
        "Person page (server)",
        "loading one person with their photo and the movies they are credited on"),
    "frontend/src/routes/people/[id]/edit/+page.server.ts": (
        "Edit person form (server)",
        "saving person changes, uploading a photo, and deleting the person"),
    "frontend/src/routes/people/[id]/edit/+page.svelte": (
        "Edit person form",
        "how the person editor is assembled, including its confirmation and "
        "feedback behaviour"),
    "frontend/src/routes/about/+page.svelte": (
        "About page",
        "the static information page"),
    "frontend/src/routes/+error.svelte": (
        "Error page",
        "what a user sees when a page cannot be loaded"),
    "frontend/src/routes/+layout.svelte": (
        "Page frame",
        "the header, navigation and shell around every page"),

    # -- end-to-end ---------------------------------------------------------
    "frontend/e2e/journey.spec.ts (no single production module)": (
        "Whole system, through a real browser",
        "the full stack running in Docker, driven through the actual user "
        "interface — nothing mocked"),
    "frontend/e2e/security-headers.spec.ts (no single production module)": (
        "Security headers, on a real response",
        "the protective headers as a real browser receives them, which can "
        "only be checked against a genuinely built and served page"),
}

# ---------------------------------------------------------------------------
# Jargon that shows up inside test names. Only terms a non-specialist reader
# would stumble on; at most two are appended to any one description.
# ---------------------------------------------------------------------------

GLOSSARY = [
    (r"\bUUIDv7\b|\bUUID-shaped\b|\bUuidV7\b", "UUIDv7 is a unique id with a timestamp built into it"),
    (r"\bsha256\b|\bdigest", "a sha256 digest is a fingerprint of a file's contents"),
    (r"\bgRPC\b", "gRPC is the private connection between the two backend services"),
    (r"\bGraphQL\b", "GraphQL is the API the website queries"),
    (r"\bDataLoader\b|\bbatched?\b", "batching means one combined lookup instead of one per item"),
    (r"optimistic|expectedVersion|\bversion has moved\b|stale.*version|CONFLICT",
     "two people editing the same record at once is detected by a version number"),
    (r"\bBAD_USER_INPUT\b", "BAD_USER_INPUT is the error code meaning the submitted value was not valid"),
    (r"\bNOT_FOUND\b", "NOT_FOUND is the error code meaning the record does not exist"),
    (r"\bPERSON_IN_USE\b", "PERSON_IN_USE means the person is still credited on a movie"),
    (r"\bDEPENDENCY_UNAVAILABLE\b", "DEPENDENCY_UNAVAILABLE means the other service could not be reached"),
    (r"\btraversal\b|\.\./", "path traversal is a filename crafted to escape its folder"),
    (r"\bidempotent?\b|\brerun\b", "idempotent means running it twice changes nothing the second time"),
    (r"\bcascade", "a cascade delete removes the dependent rows along with the parent"),
    (r"\btrigram\b|\bpg_trgm\b", "a trigram index is what makes mid-word search fast"),
    (r"\bICU\b|\bcollat", "collation is the sort order that puts accented letters with their base letter"),
    (r"\bARIA\b|\baria-|\brole=|\brole '|\bcombobox\b|\blistbox\b|\boption role\b",
     "ARIA roles are what tell a screen reader which control this is"),
    (r"\bCSP\b|Content-Security-Policy", "CSP is a browser rule limiting what a page is allowed to load"),
    (r"\bSSR\b|server-render|\bhydrat", "SSR means the page is built on the server before the browser gets it"),
    (r"\bWebP\b|\bcwebp\b", "WebP is a smaller image format modern browsers accept"),
    (r"\bMinIO\b|\bbucket\b", "MinIO is the file store holding posters and photos; a bucket is one store area"),
    (r"\bTestcontainers\b", "Testcontainers runs a real database or file store in Docker for the test"),
    (r"\bdebounce", "debouncing waits for typing to pause before searching"),
    (r"\borphan", "an orphan is a stored file nothing refers to any more"),
    (r"\bcompensat|\brollback\b", "compensating means undoing a step that cannot be rolled back automatically"),
    (r"\bzero-width\b|\bbidi\b|\bNUL\b|\bcontrol character", "these are invisible or text-reversing characters that should never be stored"),
    (r"\bZWJ\b", "a ZWJ is the invisible joiner inside a multi-part emoji"),
    (r"\bUTF-16\b|code unit|code point", "characters can be counted in more than one way; the count must match what the server enforces"),
    (r"\bTMDB\b|\btmdb", "TMDB is the external movie database the demo data is imported from"),
    (r"\bcorrelation\b", "a correlation id ties the log lines of one request together"),
    (r"\bfield mask\b|update_mask", "a field mask says which fields an update is actually changing"),
    (r"\bq-value", "a q-value is the browser's ranked preference for a format"),
    (r"\bseed ?key\b", "a seed key marks demo data so re-importing does not duplicate it"),
    (r"\bN\+1\b|without making any fetch", "N+1 means accidentally issuing one query per row instead of one for all"),
    (r"\bsemaphore\b|\bpermit\b", "a semaphore caps how many conversions run at once"),
    (r"\benhance\b|progressive", "the form still works with JavaScript switched off"),
]
