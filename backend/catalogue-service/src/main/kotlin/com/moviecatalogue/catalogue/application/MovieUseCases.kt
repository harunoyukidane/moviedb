package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.common.OffsetPageRequest
import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.domain.ConflictException
import com.moviecatalogue.catalogue.domain.CreditRules
import com.moviecatalogue.catalogue.domain.MovieRules
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.domain.ValidationException
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.movie.MovieGenre
import com.moviecatalogue.catalogue.movie.MovieGenreId
import com.moviecatalogue.catalogue.movie.MovieGenreRepository
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.catalogue.people.PeopleClient
import com.moviecatalogue.catalogue.reference.GenreCodeRepository
import com.moviecatalogue.catalogue.reference.LanguageCodeRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class MovieUseCases(
    private val movies: MovieRepository,
    private val movieGenres: MovieGenreRepository,
    private val genreCodes: GenreCodeRepository,
    private val languageCodes: LanguageCodeRepository,
    private val credits: CreditRepository,
    private val peopleClient: PeopleClient,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun getMovie(id: UUID): Movie =
        movies.findById(id).orElseThrow { NotFoundException("movie '$id' not found") }

    @Transactional(readOnly = true)
    fun listMovies(limit: Int, offset: Int, filter: MovieFilter? = null): MoviePageView {
        val clampedLimit = MovieRules.clampLimit(limit)
        val clampedOffset = MovieRules.clampOffset(offset)
        val genreCode = filter?.genreCode
        if (genreCode != null) {
            val genre = genreCodes.findById(genreCode).orElse(null)
            CreditRules.requireExistingCode(genre != null, "genre", genreCode, field = "genreCode")
        }
        MovieRules.validateReleaseYear(filter?.releaseYear)

        val pageable = OffsetPageRequest(clampedLimit, clampedOffset.toLong(), Sort.by("title").ascending())
        val page = movies.findAllByFilter(genreCode, filter?.releaseYear, pageable)
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
        validateLanguageCode(originalLanguage)
        val synopsis = MovieRules.normalizeSynopsis(command.synopsis)
        MovieRules.validateRuntime(command.runtimeMinutes)
        MovieRules.validateReleaseDate(command.releaseDate, clock)
        validateGenreCodes(command.genreCodes)

        // Idempotent upsert by TMDB provenance id (§12.3): update in place if present.
        val existing = command.tmdbId?.let { movies.findByTmdbId(it) }
        if (existing != null) {
            existing.title = title
            existing.originalTitle = originalTitle
            existing.synopsis = synopsis
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
                synopsis = synopsis,
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
            // Version numbers stay in the log, never the user-facing message (F23):
            // meaningless in a banner, useful for diagnosing a report.
            log.info(
                "movie {} version conflict: expected {}, is {}",
                movie.id, command.expectedVersion, movie.version,
            )
            throw ConflictException("movie was modified concurrently")
        }
        if (command.maskTitle) movie.title = MovieRules.normalizeTitle(command.title)
        if (command.maskOriginalTitle) movie.originalTitle = MovieRules.normalizeOptionalText(command.originalTitle, MovieRules.TITLE_MAX, "originalTitle")
        if (command.maskSynopsis) movie.synopsis = MovieRules.normalizeSynopsis(command.synopsis.orEmpty())
        if (command.maskReleaseDate) {
            MovieRules.validateReleaseDate(command.releaseDate, clock)
            if (command.releaseDate != null) validateCreditedPeopleAgainstReleaseDate(movie.id, command.releaseDate)
            movie.releaseDate = command.releaseDate
        }
        if (command.maskRuntime) {
            MovieRules.validateRuntime(command.runtimeMinutes)
            movie.runtimeMinutes = command.runtimeMinutes
        }
        if (command.maskOriginalLanguage) {
            val originalLanguage = MovieRules.normalizeOptionalText(command.originalLanguage, MovieRules.ORIGINAL_LANGUAGE_MAX, "originalLanguage")
            validateLanguageCode(originalLanguage)
            movie.originalLanguage = originalLanguage
        }

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
        // A plain existsById-then-deleteById check-then-act race lets two
        // concurrent deletes of the same movie both pass the check. Spring Data
        // JPA's deleteById is a silent no-op when the row is already gone (it
        // does NOT throw), so the loser used to "succeed" despite deleting
        // nothing. Loading the entity and deleting it with an explicit flush
        // instead relies on the existing @Version column: Hibernate scopes the
        // DELETE to `WHERE id = ? AND version = ?`, so a row removed by a
        // concurrent delete after our read still fails loudly here rather than
        // silently or as a raw INTERNAL_ERROR.
        val movie = movies.findById(id).orElseThrow { NotFoundException("movie '$id' not found") }
        try {
            // ON DELETE CASCADE removes credits, genres, and artwork (§7.2).
            movies.delete(movie)
            movies.flush()
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw NotFoundException("movie '$id' not found")
        }
        return id
    }

    /** A blank/absent language is allowed (unknown original language); a given code must exist and be active. */
    private fun validateLanguageCode(code: String?) {
        if (code == null) return
        val language = languageCodes.findById(code).orElse(null)
        CreditRules.requireActiveCode(language != null, language?.active ?: false, "language", code, field = "originalLanguage")
    }

    private fun validateGenreCodes(codes: List<String>) {
        for (code in codes.distinct()) {
            val genre = genreCodes.findById(code).orElse(null)
            CreditRules.requireActiveCode(genre != null, genre?.active ?: false, "genre", code, field = "genreCodes")
        }
    }

    private fun assignGenres(movieId: UUID, codes: List<String>) {
        codes.distinct().forEach { code ->
            movieGenres.save(MovieGenre(MovieGenreId(movieId, code)))
        }
        movieGenres.flush()
    }

    /**
     * V2.2-03b: moving a movie's release date must not put it before a
     * credited person's birth. One batched People fetch (no N+1); a People
     * outage refuses the save with DEPENDENCY_UNAVAILABLE rather than saving
     * unchecked, matching how CreditUseCases.addCredit already behaves.
     */
    private fun validateCreditedPeopleAgainstReleaseDate(movieId: UUID, releaseDate: LocalDate) {
        val personIds = credits.findAllByMovieId(movieId).map { it.personId }.distinct()
        if (personIds.isEmpty()) return
        val people = peopleClient.getPeople(personIds)
        val violators = personIds.mapNotNull { id ->
            people[id]?.takeIf { CreditRules.isBornAfterRelease(it.birthDate, releaseDate) }
        }
        if (violators.isEmpty()) return
        val named = violators.take(3).joinToString(", ") { it.name }
        val summary = if (violators.size > 3) " and ${violators.size - 3} others" else ""
        val verb = if (violators.size == 1) "was" else "were"
        throw ValidationException(
            "Can't save: $named$summary $verb born after this movie's new release date of $releaseDate. " +
                "Check the dates and try again.",
            field = "releaseDate",
        )
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
