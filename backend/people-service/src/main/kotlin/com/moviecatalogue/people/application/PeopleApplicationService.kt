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
import java.time.Clock
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
    private val clock: Clock,
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
        val limit = PersonRules.clampLimit(command.limit)
        val offset = PersonRules.clampOffset(command.offset)
        val raw = command.query?.trim().orEmpty()

        // A blank query means "list all people" (paged), so the Catalogue's
        // `people` list query has a backing read. A non-blank query searches names.
        if (raw.isEmpty()) {
            val total = repository.count()
            val page = repository.findAll(
                PageRequest.of(offset / limit, limit, org.springframework.data.domain.Sort.by("name").ascending()),
            )
            // offset may not be a multiple of limit; slice defensively.
            val results = if (offset % limit == 0) {
                page.content
            } else {
                repository.findAll(PageRequest.of(0, offset + limit, org.springframework.data.domain.Sort.by("name").ascending()))
                    .content.drop(offset).take(limit)
            }
            return SearchPeopleResult(results.map { it.toView() }, total)
        }

        val query = PersonRules.normalizeQuery(raw)
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
            command.placeOfBirth, PersonRules.PLACE_OF_BIRTH_MAX, "placeOfBirth",
        )
        val profilePath = PersonRules.normalizeOptionalText(
            command.profilePath, PersonRules.PROFILE_PATH_MAX, "profilePath",
        )
        PersonRules.validateBirthDate(command.birthDate, clock)
        PersonRules.validateDeathDate(command.deathDate, clock)
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
        if ("birth_date" in command.maskPaths) {
            PersonRules.validateBirthDate(command.birthDate, clock)
            person.birthDate = command.birthDate
        }
        if ("death_date" in command.maskPaths) {
            PersonRules.validateDeathDate(command.deathDate, clock)
            person.deathDate = command.deathDate
        }
        if ("place_of_birth" in command.maskPaths) {
            person.placeOfBirth = PersonRules.normalizeOptionalText(
                command.placeOfBirth, PersonRules.PLACE_OF_BIRTH_MAX, "placeOfBirth",
            )
        }
        if ("profile_path" in command.maskPaths) {
            person.profilePath = PersonRules.normalizeOptionalText(
                command.profilePath, PersonRules.PROFILE_PATH_MAX, "profilePath",
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
        // See MovieUseCases.deleteMovie: an existsById-then-deleteById
        // check-then-act race lets two concurrent deletes of the same person
        // both pass the check, and Spring Data JPA's deleteById is a silent
        // no-op when the row is already gone (it does NOT throw) - so the
        // loser used to "succeed" despite deleting nothing. Loading the entity
        // and deleting it with an explicit flush instead relies on the
        // existing @Version column: Hibernate scopes the DELETE to
        // `WHERE id = ? AND version = ?`, so a row removed by a concurrent
        // delete after our read still fails loudly here.
        val person = repository.findById(id).orElseThrow { PersonNotFoundException() }
        try {
            repository.delete(person)
            repository.flush()
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw PersonNotFoundException()
        }
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
