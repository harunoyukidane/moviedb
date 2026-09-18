package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.comment.CommentRepository
import com.moviecatalogue.catalogue.comment.MovieComment
import com.moviecatalogue.catalogue.common.OffsetPageRequest
import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.domain.MovieCommentRules
import com.moviecatalogue.catalogue.domain.MovieRules
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.movie.MovieRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class CommentUseCases(
    private val comments: CommentRepository,
    private val movies: MovieRepository,
) {

    @Transactional(readOnly = true)
    fun listComments(movieId: UUID, limit: Int, offset: Int): CommentPageView {
        if (!movies.existsById(movieId)) throw NotFoundException("movie '$movieId' not found")
        val clampedLimit = MovieRules.clampLimit(limit)
        val clampedOffset = MovieRules.clampOffset(offset)
        val pageable = OffsetPageRequest(clampedLimit, clampedOffset.toLong())
        val page = comments.findAllByMovieIdOrderByCreatedAtDescIdDesc(movieId, pageable)
        return CommentPageView(
            items = page.content.map { it.toView() },
            total = page.totalElements,
            limit = clampedLimit,
            offset = clampedOffset,
        )
    }

    /**
     * [seedKey] is the importer's idempotency key (V2.2-13), never set by the
     * UI. When present, upserts by it - exactly as `MovieUseCases.createMovie`
     * upserts by `tmdbId` - so a re-seed updates the existing row in place
     * rather than duplicating it. Absent (the only case the UI ever produces),
     * behavior is unchanged: always insert.
     */
    @Transactional
    fun addComment(movieId: UUID, authorDisplayName: String?, text: String?, seedKey: String? = null): CommentView {
        if (!movies.existsById(movieId)) throw NotFoundException("movie '$movieId' not found")
        val author = MovieCommentRules.normalizeAuthorDisplayName(authorDisplayName)
        val body = MovieCommentRules.normalizeText(text)

        val existing = seedKey?.let { comments.findBySeedKey(it) }
        if (existing != null) {
            existing.authorDisplayName = author
            existing.text = body
            return comments.saveAndFlush(existing).toView()
        }

        val saved = comments.saveAndFlush(
            MovieComment(
                id = UuidV7.generate(),
                movieId = movieId,
                authorDisplayName = author,
                text = body,
                createdAt = OffsetDateTime.now(),
                seedKey = seedKey,
            ),
        )
        return saved.toView()
    }
}

fun MovieComment.toView(): CommentView = CommentView(
    id = id,
    movieId = movieId,
    authorDisplayName = authorDisplayName,
    text = text,
    createdAt = createdAt,
)
