# Requirements Document

## Introduction

This document specifies the "v2" enhancement set for the existing MovieDB application. MovieDB is a SvelteKit server-side-rendered frontend (BFF) backed by two Kotlin services: a `catalogue-service` that owns movies, credits, genres, and artwork (exposed via GraphQL), and a `people-service` that owns people (exposed via gRPC internally). A shared `media` module provides an `ArtworkStore` abstraction for validated artwork bytes; the current implementation is `LocalArtworkStore` (filesystem/mounted volume), with an S3-style swap recorded as future intent in ADR-4.

The v2 scope covers seven areas: iconography for credit actions and view controls; a cluster/list view toggle for the movie listing; expanded seeded content plus genre and year filtering; a MyDramaList-style cast and credits presentation on the movie detail page; a comment section on movie detail pages; a photo-augmented people list; and a migration of artwork storage from the local filesystem to a MinIO (S3-compatible) backend behind the existing `ArtworkStore` abstraction.

These requirements describe observable system behavior only. Technical realization (component structure, GraphQL schema additions, storage client selection) is deferred to the design phase, except where the existing architecture constrains the observable outcome.

## Glossary

- **Frontend**: The SvelteKit server-side-rendered application that is the browser's only entry point and calls the backends over the internal network.
- **Catalogue_Service**: The Kotlin service owning movies, credits, genres, and artwork metadata, exposed to the Frontend over GraphQL.
- **People_Service**: The Kotlin service owning person records, referenced by Catalogue_Service by id over gRPC.
- **Artwork_Store**: The `ArtworkStore` abstraction in the shared `media` module (`put`, `open`, `delete`, `exists`, `listKeys`) that persists validated artwork bytes and returns a server-generated storage key.
- **Local_Artwork_Store**: The current filesystem-backed `ArtworkStore` implementation (`LocalArtworkStore`).
- **MinIO_Artwork_Store**: A new S3-compatible `ArtworkStore` implementation backed by a MinIO server, introduced in this feature.
- **Movie_Listing**: The `/movies` page that displays a paginated set of movies.
- **Cluster_View**: The movie listing presentation showing movies as a poster/card grid (the current default).
- **List_View**: A new movie listing presentation showing one movie per row with poster, title, year, genre, and a truncated summary.
- **View_Mode**: The user-selected presentation of the Movie_Listing, either Cluster_View or List_View.
- **Movie_Detail**: The `/movies/{id}` read-only page for a single movie.
- **Credit**: A `MovieCredit` linking a movie to a person with a category (CAST or CREW) and a role, optionally a character name and billing order.
- **Credit_Editor**: The `/movies/{id}/edit` page where credits are added, edited, and removed.
- **Comment**: A user-submitted text note attached to a single movie, with author display name and creation timestamp.
- **People_Listing**: The `/people` page that displays a paginated set of people.
- **Genre_Code**: A controlled genre reference value (`GenreCode`) with a code and title.
- **Icon_Asset**: An SVG image file stored under `frontend/src/resources` used to represent an action or view control.
- **Storage_Key**: A server-generated identifier (UUID plus safe extension) that the Artwork_Store uses to address a stored object; the client filename is never used to form it.

## Requirements

### Requirement 1: Action and View Icons

**User Story:** As a catalogue maintainer, I want consistent icons for editing, deleting, and switching views, so that common actions are recognizable and take less screen space.

#### Acceptance Criteria

1. THE Frontend SHALL provide Icon_Assets under `frontend/src/resources` for edit, delete, list view, and cluster view.
2. WHERE a Credit is displayed in the Credit_Editor, THE Frontend SHALL display an edit Icon_Asset control that opens the edit interaction for that Credit.
3. WHERE a Credit is displayed in the Credit_Editor, THE Frontend SHALL display a delete Icon_Asset control that initiates removal of that Credit.
4. WHEN a user activates the edit Icon_Asset control for a Credit, THE Frontend SHALL open the existing Credit edit interaction for that Credit.
5. WHEN a user activates the delete Icon_Asset control for a Credit, THE Frontend SHALL request confirmation before removing that Credit.
6. THE Frontend SHALL render the movie delete action as a labeled confirmation dialog that states the movie title and warns that deletion is permanent, rather than as an icon-only control.
7. THE Frontend SHALL provide a text alternative for each Icon_Asset control that names the action the control performs.

### Requirement 2: Movie Listing View Mode Toggle

**User Story:** As a browser of the catalogue, I want to switch between a poster grid and a detailed list, so that I can scan posters or read summaries depending on my need.

#### Acceptance Criteria

1. THE Movie_Listing SHALL provide a View_Mode control offering Cluster_View and List_View.
2. WHILE the View_Mode is Cluster_View, THE Movie_Listing SHALL display movies as a poster/card grid.
3. WHILE the View_Mode is List_View, THE Movie_Listing SHALL display one movie per row with the poster positioned on the left and the title, release year, genres, and a truncated summary positioned on the right.
4. WHEN a user selects a View_Mode, THE Movie_Listing SHALL re-render the current set of movies in the selected View_Mode without changing the current pagination offset.
5. WHEN the Movie_Listing is loaded without a previously selected View_Mode, THE Movie_Listing SHALL display Cluster_View as the default.
6. WHILE the View_Mode is List_View AND a movie has no artwork, THE Movie_Listing SHALL display the existing poster fallback placeholder in the poster position.
7. WHILE the View_Mode is List_View AND a movie summary exceeds the displayed length, THE Movie_Listing SHALL truncate the displayed summary with an ellipsis indicator.

### Requirement 3: Expanded Content and Movie Filtering

**User Story:** As a browser of the catalogue, I want a larger and more varied movie set that I can filter by genre and year, so that I can find movies that match my interests.

#### Acceptance Criteria

1. THE Catalogue_Service SHALL provide a seed dataset of movies spanning multiple distinct Genre_Codes.
2. WHERE a genre filter value is supplied, THE Movie_Listing SHALL display only movies associated with the selected Genre_Code.
3. WHERE a release-year filter value is supplied, THE Movie_Listing SHALL display only movies whose release year equals the selected year.
4. WHERE both a genre filter value and a release-year filter value are supplied, THE Movie_Listing SHALL display only movies that match both the selected Genre_Code and the selected year.
5. WHEN a user changes a filter value, THE Movie_Listing SHALL reset the pagination offset to the first page of results.
6. WHEN a filter combination matches no movies, THE Movie_Listing SHALL display an empty-result message.
7. WHEN a user clears all filter values, THE Movie_Listing SHALL display the unfiltered set of movies.
8. THE Movie_Listing SHALL report the total count of movies matching the active filter values.

### Requirement 4: MyDramaList-Style Cast and Credits Display

**User Story:** As a viewer of a movie, I want cast and credits shown with each person's photo alongside their name and role, so that I can recognize people at a glance.

#### Acceptance Criteria

1. WHERE a Movie_Detail displays a Credit, THE Movie_Detail SHALL display the person's photo on the left and the person's name and role on the right.
2. WHEN a Credit has category CAST, THE Movie_Detail SHALL display the character name associated with that Credit as the role text.
3. WHEN a Credit has category CREW, THE Movie_Detail SHALL display the credit role title associated with that Credit as the role text.
4. IF a person referenced by a Credit has no photo, THEN THE Movie_Detail SHALL display a placeholder image in the photo position.
5. IF a person referenced by a Credit is unavailable from the People_Service, THEN THE Movie_Detail SHALL display an unavailable-person indicator in place of the name.
6. THE Movie_Detail SHALL order displayed cast Credits by their billing order.

### Requirement 5: Movie Comment Section

**User Story:** As a viewer of a movie, I want to read and add comments on the movie detail page, so that I can share and see opinions about the movie.

#### Acceptance Criteria

1. THE Movie_Detail SHALL display a comment section listing existing Comments for the movie.
2. WHEN a Movie_Detail is loaded, THE Frontend SHALL display each Comment with the author display name, the comment text, and the creation timestamp.
3. WHEN a user submits a Comment with non-empty text for a movie, THE Catalogue_Service SHALL persist the Comment associated with that movie and record its creation timestamp.
4. WHEN a Comment is successfully persisted, THE Movie_Detail SHALL display the new Comment in the comment section.
5. IF a user submits a Comment with empty or whitespace-only text, THEN THE Frontend SHALL reject the submission and display a validation message.
6. IF a user submits a Comment whose text exceeds the maximum comment length, THEN THE Catalogue_Service SHALL reject the Comment and return a validation error.
7. WHEN a movie has no Comments, THE Movie_Detail SHALL display an empty-state message inviting the first comment.
8. THE Movie_Detail SHALL display Comments in reverse chronological order by creation timestamp.

### Requirement 6: People List Photo Display

**User Story:** As a browser of people, I want each person shown with their photo next to their name, so that I can identify people visually.

#### Acceptance Criteria

1. WHERE the People_Listing displays a person, THE People_Listing SHALL display the person's photo on the left and the person's name on the right.
2. IF a person has no photo, THEN THE People_Listing SHALL display a placeholder image in the photo position.
3. WHEN a user activates a person entry in the People_Listing, THE Frontend SHALL navigate to that person's detail page.
4. THE People_Listing SHALL retain the existing pagination controls and displayed total count.

### Requirement 7: Artwork Storage Migration to MinIO

**User Story:** As an operator of MovieDB, I want artwork stored in a MinIO S3-compatible backend instead of the local filesystem, so that the deployment simulates cloud object storage without changing application behavior.

#### Acceptance Criteria

1. THE media module SHALL provide a MinIO_Artwork_Store implementation of the Artwork_Store abstraction.
2. WHEN artwork bytes are stored, THE MinIO_Artwork_Store SHALL persist the bytes to the MinIO backend and return a server-generated Storage_Key.
3. THE MinIO_Artwork_Store SHALL derive each Storage_Key from a server-generated identifier and a safe extension, and SHALL NOT use the client-supplied filename to form the Storage_Key.
4. WHEN a stored object is requested by Storage_Key, THE MinIO_Artwork_Store SHALL return the corresponding bytes from the MinIO backend.
5. WHEN a stored object is requested by an unknown Storage_Key, THE MinIO_Artwork_Store SHALL indicate that no object exists for that Storage_Key.
6. WHEN a stored object is deleted by Storage_Key, THE MinIO_Artwork_Store SHALL remove the object from the MinIO backend.
7. THE MinIO_Artwork_Store SHALL enumerate all Storage_Keys currently present in the MinIO backend for the orphan-sweep use case.
8. WHERE the MinIO storage backend is selected by configuration, THE Catalogue_Service and THE People_Service SHALL use the MinIO_Artwork_Store for artwork persistence in place of the Local_Artwork_Store.
9. THE MinIO_Artwork_Store SHALL preserve the existing artwork upload validation and HTTP serving behavior so that stored artwork remains retrievable through the existing media endpoints.
