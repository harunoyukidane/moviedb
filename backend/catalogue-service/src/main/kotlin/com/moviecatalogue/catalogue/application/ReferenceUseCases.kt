package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.reference.CreditRoleCode
import com.moviecatalogue.catalogue.reference.CreditRoleCodeRepository
import com.moviecatalogue.catalogue.reference.GenreCode
import com.moviecatalogue.catalogue.reference.GenreCodeRepository
import com.moviecatalogue.catalogue.reference.LanguageCode
import com.moviecatalogue.catalogue.reference.LanguageCodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Reference-table reads (§7.2 controlled code tables, ADR-5). */
@Service
class ReferenceUseCases(
    private val genres: GenreCodeRepository,
    private val roles: CreditRoleCodeRepository,
    private val languages: LanguageCodeRepository,
    private val credits: CreditRepository,
    private val hydrator: PersonHydrator,
) {

    @Transactional(readOnly = true)
    fun listGenres(activeOnly: Boolean): List<GenreCode> =
        if (activeOnly) genres.findAllByActiveTrueOrderByDisplayOrderAsc()
        else genres.findAllByOrderByDisplayOrderAsc()

    @Transactional(readOnly = true)
    fun listLanguages(activeOnly: Boolean): List<LanguageCode> =
        if (activeOnly) languages.findAllByActiveTrueOrderByDisplayOrderAsc()
        else languages.findAllByOrderByDisplayOrderAsc()

    fun languageByCode(code: String): LanguageCode? = languages.findById(code).orElse(null)

    @Transactional(readOnly = true)
    fun listCreditRoles(category: CreditCategory?, activeOnly: Boolean): List<CreditRoleCode> = when {
        category != null && activeOnly -> roles.findAllByCategoryAndActiveTrueOrderByDisplayOrderAsc(category)
        category != null -> roles.findAllByCategoryOrderByDisplayOrderAsc(category)
        activeOnly -> roles.findAllByActiveTrueOrderByDisplayOrderAsc()
        else -> roles.findAllByOrderByDisplayOrderAsc()
    }

    fun genreByCode(code: String): GenreCode? = genres.findById(code).orElse(null)

    fun roleByCode(code: String): CreditRoleCode? = roles.findById(code).orElse(null)

    /** Cast + creators for one movie, person-hydrated in a single batched call. */
    @Transactional(readOnly = true)
    fun creditsForMovie(movieId: UUID): List<CreditView> =
        hydrator.toCreditViews(credits.findAllByMovieId(movieId))
}
