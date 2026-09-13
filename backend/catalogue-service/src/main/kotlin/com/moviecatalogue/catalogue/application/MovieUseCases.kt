package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.common.OffsetPageRequest
import com.moviecatalogue.catalogue.domain.ConflictException
import com.moviecatalogue.catalogue.domain.CreditRules
import com.moviecatalogue.catalogue.domain.MovieRules
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.movie.MovieGenre
import com.moviecatalogue.catalogue.movie.MovieGenreId
import com.moviecatalogue.catalogue.movie.MovieGenreRepository
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.catalogue.reference.GenreCodeRepository
import org.springframework.data.domain.Sort
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MovieUseCases(
    private val movies: MovieRepository,
    private val movieGenres: MovieGenreRepository,
    private val genreCodes: GenreCodeRepository,
) {

    @Transactional(readOnly = true)
    fun getMovie(id: UUID): Movie =
        movies.findById(id).orElseThrow { NotFoundException("movie '$id' not found") }

    @Transactional(readOnly = true)
    fun listMovies(limit: Int, offset: Int): MoviePageView {
        val clampedLimit = MovieRules.clampLimit(limit)
        val clampedOffset = MovieRules.clampOffset(offset)
        val page = movies.findAll(
            OffsetPageRequest(clampedLimit, clampedOffset.toLong(), Sort.by("title").ascending()),
        )
        return MoviePageView(
            items = page.content.map { it.toView() },
            total = page.totalElements,
            limit = clampedLimit,
            offset = clampedOffset,
        )
    }

    @Transactional(readOnly = true)
    fun genreCodesForMovie(movieId: UUID): List<String> =
        movieGenres.findAllByIdMovieId(movieId).map { it.id.genreCode }

    @Transactional
    fun createMovie(command: CreateMovieCommand): Movie {
        val title = MovieRules.normalizeTitle(command.title)
        val originalTitle = MovieRules.normalizeOptionalText(command.originalTitle, MovieRules.TITLE_MAX, "originalTitle")
        val originalLanguage = MovieRules.normalizeOptionalText(command.originalLanguage, MovieRules.ORIGINAL_LANGUAGE_MAX, "originalLanguage")
        MovieRules.validateRuntime(command.runtimeMinutes)
        validateGenreCodes(command.genreCodes)

        // Idempotent upsert by TMDB provenance id (§12.3): update in place if present.
        val existing = command.tmdbId?.let { movies.findByTmdbId(it) }
        if (existing != null) {
            existing.title = title
            existing.originalTitle = originalTitle
            existing.synopsis = command.synopsis.trim()
            existing.releaseDate = command.releaseDate
            existing.runtimeMinutes = command.runtimeMinutes
            existing.originalLanguage = originalLanguage
            val saved = movies.saveAndFlush(existing)
            movieGenres.deleteByIdMovieId(saved.id)
            movieGenres.flush()
            assignGenres(saved.id, command.genreCodes)
            return saved
        }

        val movie = movies.saveAndFlush(
            Movie(
                id = UuidV7.generate(),
                tmdbId = command.tmdbId,
                title = title,
                originalTitle = originalTitle,
                synopsis = command.synopsis.trim(),
                releaseDate = command.releaseDate,
                runtimeMinutes = command.runtimeMinutes,
                originalLanguage = originalLanguage,
            ),
        )
        assignGenres(movie.id, command.genreCodes)
        return movie
    }

    @Transactional
    fun updateMovie(command: UpdateMovieCommand): Movie {
        val movie = movies.findById(command.id).orElseThrow { NotFoundException("movie '${command.id}' not found") }
        if (movie.version != command.expectedVersion) {
            throw ConflictException("movie was modified concurrently (expected ${command.expectedVersion}, is ${movie.version})")
        }
        if (command.maskTitle) movie.title = MovieRules.normalizeTitle(command.title)
        if (command.maskOriginalTitle) movie.originalTitle = MovieRules.normalizeOptionalText(command.originalTitle, MovieRules.TITLE_MAX, "originalTitle")
        if (command.maskSynopsis) movie.synopsis = command.synopsis?.trim().orEmpty()
        if (command.maskReleaseDate) movie.releaseDate = command.releaseDate
        if (command.maskRuntime) {
            MovieRules.validateRuntime(command.runtimeMinutes)
            movie.runtimeMinutes = command.runtimeMinutes
        }
        if (command.maskOriginalLanguage) movie.originalLanguage = MovieRules.normalizeOptionalText(command.originalLanguage, MovieRules.ORIGINAL_LANGUAGE_MAX, "originalLanguage")

        val saved = try {
            movies.saveAndFlush(movie)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw ConflictException("movie was modified concurrently")
        }

        if (command.maskGenres) {
            val codes = command.genreCodes ?: emptyList()
            validateGenreCodes(codes)
            movieGenres.deleteByIdMovieId(saved.id)
            movieGenres.flush()
            assignGenres(saved.id, codes)
        }
        return saved
    }

    @Transactional
    fun deleteMovie(id: UUID): UUID {
        if (!movies.existsById(id)) throw NotFoundException("movie '$id' not found")
        // ON DELETE CASCADE removes credits, genres, and artwork (§7.2).
        movies.deleteById(id)
        return id
    }

    private fun validateGenreCodes(codes: List<String>) {
        for (code in codes.distinct()) {
            val genre = genreCodes.findById(code).orElse(null)
            CreditRules.requireActiveCode(genre != null, genre?.active ?: false, "genre", code)
        }
    }

    private fun assignGenres(movieId: UUID, codes: List<String>) {
        codes.distinct().forEach { code ->
            movieGenres.save(MovieGenre(MovieGenreId(movieId, code)))
        }
        movieGenres.flush()
    }
}

fun Movie.toView(): MovieView = MovieView(
    id = id,
    title = title,
    originalTitle = originalTitle,
    synopsis = synopsis,
    releaseDate = releaseDate,
    runtimeMinutes = runtimeMinutes,
    originalLanguage = originalLanguage,
    version = version,
)
