package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.artwork.ArtworkAsset
import com.moviecatalogue.catalogue.artwork.ArtworkRepository
import com.moviecatalogue.catalogue.credit.MovieCredit
import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.movie.MovieGenreRepository
import com.moviecatalogue.catalogue.reference.CreditRoleCode
import com.moviecatalogue.catalogue.reference.CreditRoleCodeRepository
import com.moviecatalogue.catalogue.reference.GenreCode
import com.moviecatalogue.catalogue.reference.GenreCodeRepository
import com.moviecatalogue.catalogue.reference.LanguageCode
import com.moviecatalogue.catalogue.reference.LanguageCodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read-side application boundary for the nested Movie GraphQL projection.
 *
 * Keeping these reads here prevents the transport adapter from assembling a
 * domain projection by reaching directly into persistence repositories. The
 * methods also centralize the stable ordering rules used by every caller.
 */
@Service
class MovieReadService(
    private val credits: CreditRepository,
    private val movieGenres: MovieGenreRepository,
    private val genreCodes: GenreCodeRepository,
    private val roleCodes: CreditRoleCodeRepository,
    private val languageCodes: LanguageCodeRepository,
    private val artwork: ArtworkRepository,
) {

    @Transactional(readOnly = true)
    fun genres(movieId: UUID): List<GenreCode> {
        val codes = movieGenres.findAllByIdMovieId(movieId).map { it.id.genreCode }
        if (codes.isEmpty()) return emptyList()
        return genreCodes.findAllById(codes).sortedBy { it.displayOrder }
    }

    @Transactional(readOnly = true)
    fun credits(movieId: UUID, category: CreditCategory): List<MovieCredit> {
        val matching = credits.findAllByMovieId(movieId).filter { it.category == category }
        return when (category) {
            CreditCategory.CAST -> matching.sortedWith(
                compareBy({ it.billingOrder ?: Int.MAX_VALUE }, { it.id }),
            )
            CreditCategory.CREW -> matching.sortedWith(
                compareBy({ it.roleCode }, { it.billingOrder ?: Int.MAX_VALUE }, { it.id }),
            )
        }
    }

    @Transactional(readOnly = true)
    fun artwork(movieId: UUID): ArtworkAsset? = artwork.findByMovieId(movieId)

    @Transactional(readOnly = true)
    fun role(code: String): CreditRoleCode? = roleCodes.findById(code).orElse(null)

    @Transactional(readOnly = true)
    fun language(code: String?): LanguageCode? = code?.let { languageCodes.findById(it).orElse(null) }
}
