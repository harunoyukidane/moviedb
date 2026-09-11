# ADR-13: Credit last-write-wins

Status: accepted

## Context
Movies and people use optimistic locking (`@Version` + `expectedVersion`). Credits are small rows subordinate to a movie and rarely edited concurrently by two users.

## Decision
`updateMovieCredit` does not take an `expectedVersion`; last-write-wins is accepted for credit updates. Movies and people retain optimistic locking.

## Consequences
- Simpler credit mutation API.
- A theoretical lost update on concurrent credit edits is accepted as proportionate.
- If concurrent credit editing becomes real, add a `version` column to `movie_credit` and an `expectedVersion` argument.
