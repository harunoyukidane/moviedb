package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.credit.MovieCredit
import com.moviecatalogue.catalogue.domain.ConflictException
import com.moviecatalogue.catalogue.domain.CreditRules
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.catalogue.people.PeopleClient
import com.moviecatalogue.catalogue.reference.CreditRoleCodeRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Credit mutations (§6.6). AddCredit validates the person reference over gRPC
 * before inserting; a People outage surfaces as DEPENDENCY_UNAVAILABLE and no row
 * is written. UpdateCredit is intentionally last-write-wins (no expectedVersion,
 * ADR-13).
 */
@Service
class CreditUseCases(
    private val credits: CreditRepository,
    private val movies: MovieRepository,
    private val roleCodes: CreditRoleCodeRepository,
    private val peopleClient: PeopleClient,
    private val hydrator: PersonHydrator,
) {

    @Transactional
    fun addCredit(command: AddCreditCommand): CreditView {
        // 1. shape validation
        val movie = movies.findById(command.movieId).orElseThrow { NotFoundException("movie '${command.movieId}' not found") }
        val role = roleCodes.findById(command.roleCode).orElse(null)
        CreditRules.requireActiveCode(role != null, role?.active ?: false, "role", command.roleCode, field = "roleCode")
        val category = role!!.category
        val characterName = CreditRules.normalizeCharacterName(command.characterName)
        CreditRules.validateCharacterForCategory(category, characterName)
        CreditRules.validateBillingOrder(command.billingOrder)

        // 2. validate the logical person reference over gRPC (throws DEPENDENCY_UNAVAILABLE
        //    on outage, NOT_FOUND if the person doesn't exist) — before any insert.
        val person = peopleClient.getPerson(command.personId)
        CreditRules.validateCreditAge(person.name, person.birthDate, movie.releaseDate, field = "personId")
        val sourceRoleName = CreditRules.normalizeSourceRoleName(command.sourceRoleName)

        // Idempotent upsert by TMDB credit id (§12.3): update in place if present.
        val existing = command.tmdbCreditId?.let {
            credits.findByMovieIdAndTmdbCreditId(command.movieId, it)
        }
        if (existing != null) {
            existing.personId = command.personId
            existing.roleCode = command.roleCode
            existing.category = category
            existing.characterName = characterName
            existing.billingOrder = command.billingOrder
            existing.sourceRoleName = sourceRoleName
            val saved = credits.saveAndFlush(existing)
            return saved.toView(PersonRef(person.id, person.name, available = true))
        }

        // 3. insert subject to uniqueness constraints (uq_movie_credit_manual)
        val credit = MovieCredit(
            id = UuidV7.generate(),
            movieId = command.movieId,
            personId = command.personId,
            roleCode = command.roleCode,
            category = category,
            characterName = characterName,
            billingOrder = command.billingOrder,
            sourceRoleName = sourceRoleName,
            tmdbCreditId = command.tmdbCreditId,
        )
        val saved = try {
            credits.saveAndFlush(credit)
        } catch (e: DataIntegrityViolationException) {
            throw ConflictException("this person already has this role on the movie")
        }
        // 4. return with hydrated person (already fetched)
        return saved.toView(PersonRef(person.id, person.name, available = true))
    }

    @Transactional
    fun updateCredit(command: UpdateCreditCommand): CreditView {
        val credit = credits.findById(command.id).orElseThrow { NotFoundException("credit '${command.id}' not found") }

        if (command.maskRole) {
            val role = roleCodes.findById(command.roleCode!!).orElse(null)
            CreditRules.requireActiveCode(role != null, role?.active ?: false, "role", command.roleCode, field = "roleCode")
            // role's category must still agree with the credit's category (can't move CAST<->CREW here)
            CreditRules.validateRoleCategoryAgreement(role!!.category, credit.category)
            credit.roleCode = command.roleCode
        }
        if (command.maskCharacter) {
            credit.characterName = CreditRules.normalizeCharacterName(command.characterName)
        }
        if (command.maskBilling) {
            CreditRules.validateBillingOrder(command.billingOrder)
            credit.billingOrder = command.billingOrder
        }
        // character/category rule must still hold after edits
        CreditRules.validateCharacterForCategory(credit.category, credit.characterName)

        val saved = try {
            credits.saveAndFlush(credit)
        } catch (e: DataIntegrityViolationException) {
            throw ConflictException("this person already has this role on the movie")
        }
        return hydrator.toCreditViews(listOf(saved)).first()
    }

    @Transactional
    fun removeCredit(id: UUID): UUID {
        if (!credits.existsById(id)) throw NotFoundException("credit '$id' not found")
        credits.deleteById(id)
        return id
    }
}
