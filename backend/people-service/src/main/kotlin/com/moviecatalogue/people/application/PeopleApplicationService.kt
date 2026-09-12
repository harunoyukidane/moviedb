package com.moviecatalogue.people.application

import com.moviecatalogue.people.common.UuidV7
import com.moviecatalogue.people.domain.DuplicateTmdbIdException
import com.moviecatalogue.people.domain.PersonNotFoundException
import com.moviecatalogue.people.domain.PersonRules
import com.moviecatalogue.people.domain.PersonSearch
import com.moviecatalogue.people.domain.PreconditionException
import com.moviecatalogue.people.domain.ValidationException
import com.moviecatalogue.people.domain.VersionConflictException
import com.moviecatalogue.people.person.Person
import com.moviecatalogue.people.person.PersonRepository
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * People use cases (§6.4 application layer). Enforces the request-shaping rules
 * (batch cap, dedup, limit/offset clamp, query length), tmdb_id idempotency, and
 * optimistic-locking, translating persistence conflicts into domain exceptions
 * that the gRPC adapter maps to status codes.
 */
@Service
class PeopleApplicationService(
    private val repository: PersonRepository,
) {

    /** Known field-mask paths for UpdatePerson; anything else is a precondition failure. */
    private val allowedMaskPaths = setOf(
        "name", "biography", "birth_date", "death_date", "place_of_birth", "profile_path",
    )

    @Transactional(readOnly = true)
    fun getPerson(id: UUID): PersonView {
        val person = repository.findById(id).orElseThrow { PersonNotFoundException() }
        return person.toView()
    }

    /**
     * Batch lookup. IDs are de-duplicated and capped at 200; a single `IN` query is
     * issued. Results are returned keyed by ID so callers never rely on ordering,
     * and missing IDs are simply absent from the map.
     */
    @Transactional(readOnly = true)
    fun getPeople(ids: List<UUID>): Map<UUID, PersonView> {
        val distinct = PersonRules.dedupeAndCap(ids)
        if (distinct.isEmpty()) return emptyMap()
        return repository.findAllByIdIn(distinct).associate { it.id to it.toView() }
    }

    @Transactional(readOnly = true)
    fun searchPeople(command: SearchPeopleCommand): SearchPeopleResult {
        val query = PersonRules.normalizeQuery(command.query)
        val limit = PersonRules.clampLimit(command.limit)
        val offset = PersonRules.clampOffset(command.offset)

        val pattern = PersonSearch.containsPattern(query)
        val total = repository.countByNamePattern(pattern)
        // offset may not be a multiple of limit, so fetch offset+limit rows in a
        // stable order and drop the offset prefix. Fine for the catalogue's scale.
        val fetched = repository.searchByNamePattern(pattern, PageRequest.of(0, offset + limit))
        val results = fetched.drop(offset).take(limit)
        return SearchPeopleResult(results.map { it.toView() }, total)
    }

    @Transactional
    fun createPerson(command: CreatePersonCommand): PersonView {
        val name = PersonRules.normalizeName(command.name)
        val placeOfBirth = PersonRules.normalizeOptionalText(
            command.placeOfBirth, PersonRules.PLACE_OF_BIRTH_MAX, "place_of_birth",
        )
        val profilePath = PersonRules.normalizeOptionalText(
            command.profilePath, PersonRules.PROFILE_PATH_MAX, "profile_path",
        )
        PersonRules.validateLifeDates(command.birthDate, command.deathDate)

        // tmdb_id idempotency: reject a create that would duplicate an existing provenance id.
        command.tmdbId?.let {
            if (repository.existsByTmdbId(it)) throw DuplicateTmdbIdException(it)
        }

        val person = Person(
            id = UuidV7.generate(),
            tmdbId = command.tmdbId,
            name = name,
            biography = command.biography.trim(),
            birthDate = command.birthDate,
            deathDate = command.deathDate,
            placeOfBirth = placeOfBirth,
            profilePath = profilePath,
        )
        return try {
            repository.saveAndFlush(person).toView()
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            // Unique tmdb_id race lost at the DB level.
            if (command.tmdbId != null) throw DuplicateTmdbIdException(command.tmdbId) else throw e
        }
    }

    @Transactional
    fun updatePerson(command: UpdatePersonCommand): PersonView {
        val unknown = command.maskPaths - allowedMaskPaths
        if (unknown.isNotEmpty()) {
            throw PreconditionException("unknown field mask path(s): ${unknown.sorted()}")
        }
        if (command.maskPaths.isEmpty()) {
            throw ValidationException("update_mask must select at least one field")
        }

        val person = repository.findById(command.id).orElseThrow { PersonNotFoundException() }
        if (person.version != command.expectedVersion) {
            throw VersionConflictException(
                "expected version ${command.expectedVersion} but current is ${person.version}",
            )
        }

        if ("name" in command.maskPaths) person.name = PersonRules.normalizeName(command.name)
        if ("biography" in command.maskPaths) person.biography = command.biography?.trim().orEmpty()
        if ("birth_date" in command.maskPaths) person.birthDate = command.birthDate
        if ("death_date" in command.maskPaths) person.deathDate = command.deathDate
        if ("place_of_birth" in command.maskPaths) {
            person.placeOfBirth = PersonRules.normalizeOptionalText(
                command.placeOfBirth, PersonRules.PLACE_OF_BIRTH_MAX, "place_of_birth",
            )
        }
        if ("profile_path" in command.maskPaths) {
            person.profilePath = PersonRules.normalizeOptionalText(
                command.profilePath, PersonRules.PROFILE_PATH_MAX, "profile_path",
            )
        }
        PersonRules.validateLifeDates(person.birthDate, person.deathDate)

        return try {
            repository.saveAndFlush(person).toView()
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw VersionConflictException()
        }
    }

    @Transactional
    fun deletePerson(id: UUID) {
        if (!repository.existsById(id)) throw PersonNotFoundException()
        repository.deleteById(id)
    }
}

private fun Person.toView(): PersonView = PersonView(
    id = id,
    tmdbId = tmdbId,
    name = name,
    biography = biography,
    birthDate = birthDate,
    deathDate = deathDate,
    placeOfBirth = placeOfBirth,
    profilePath = profilePath,
    version = version,
)
