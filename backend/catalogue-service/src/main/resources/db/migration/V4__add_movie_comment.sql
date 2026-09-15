-- Movie comments (V2-13). Append-only: no update/soft-delete path, comments
-- are physically removed only via the owning movie's cascade delete.

CREATE TABLE movie_comment (
    id UUID PRIMARY KEY,
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    author_display_name VARCHAR(50) NOT NULL,
    text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (author_display_name <> ''),
    CHECK (text <> '')
);

CREATE INDEX ix_movie_comment_movie_created ON movie_comment (movie_id, created_at DESC, id DESC);
