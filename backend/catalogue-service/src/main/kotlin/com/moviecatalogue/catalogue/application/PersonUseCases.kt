package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.domain.PersonInUseException
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.catalogue.people.CreatePersonData
import com.moviecatalogue.catalogue.people.PeopleClient
import com.moviecatalogue.catalogue.people.PersonData
import com.moviecatalogue.catalogue.people.UpdatePersonData
import com.moviecatalogue.catalogue.domain.CreditCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Person orchestration (§6.6). Person data lives in the People Service; the
 * Catalogue only orchestrates over gRPC and enforces the reference-protection
 * rule on delete (safe delete -> PERSON_IN_USE). Removing a person from a movie
 * is a credit removal, never a person delete.
 */
@Service
class PersonUseCases(
    private val peopleClient: PeopleClient,
    private val credits: CreditRepository,
    private val movies: MovieRepository,
) {

    fun getPerson(id: UUID): PersonData = peopleClient.getPerson(id)

    fun createPerson(
        name: String,
        biography: String,
        birthDate: LocalDate?,
        deathDate: LocalDate?,
        placeOfBirth: String?,
    ): PersonData = peopleClient.createPerson(
        CreatePersonData(name, biography, birthDate, deathDate, placeOfBirth),
    )

    fun updatePerson(command: UpdatePersonData): PersonData = peopleClient.updatePerson(command)

    /**
     * Safe delete: if the person is still referenced by any local credit, reject
     * with PERSON_IN_USE. Only when unreferenced do we call People DeletePerson.
     */
    @Transactional
    fun deletePerson(id: UUID): UUID {
        if (credits.existsByPersonId(id)) throw PersonInUseException()
        peopleClient.deletePerson(id)
        return id
    }

    /** Person.credits projection: which movies/roles reference this person. */
    @Transactional(readOnly = true)
    fun creditsForPerson(personId: UUID): List<PersonCreditView> {
        val personCredits = credits.findAllByPersonId(personId)
        if (personCredits.isEmpty()) return emptyList()
        val movieTitles = movies.findAllById(personCredits.map { it.movieId }.distinct())
            .associate { it.id to it.title }
        return personCredits.map {
            PersonCreditView(
                movieId = it.movieId,
                movieTitle = movieTitles[it.movieId] ?: "",
                category = it.category,
                roleCode = it.roleCode,
                characterName = it.characterName,
            )
        }
    }
}

data class PersonCreditView(
    val movieId: UUID,
    val movieTitle: String,
    val category: CreditCategory,
    val roleCode: String,
    val characterName: String?,
)
