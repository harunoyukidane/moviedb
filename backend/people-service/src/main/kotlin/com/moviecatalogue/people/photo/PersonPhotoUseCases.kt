package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.people.domain.PersonNotFoundException
import com.moviecatalogue.people.person.PersonRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.util.UUID

/**
 * Person profile-photo upload/delete. `person.profile_path` holds the uploaded
 * photo's storage key (or null). The importer downloads TMDB profile images and
 * uploads them through this same validated path, so a seeded person's photo is a
 * real uploaded key — there is no raw-TMDB-URL variant to distinguish anymore.
 */
@Service
class PersonPhotoUseCases(
    private val people: PersonRepository,
    private val store: ArtworkStore,
    private val validator: ImageContentValidator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class PhotoResult(val storageKey: String, val mediaType: String, val byteSize: Long)

    @Transactional
    fun uploadPhoto(personId: UUID, bytes: ByteArray): PhotoResult {
        val person = people.findById(personId).orElseThrow { PersonNotFoundException() }
        val validated = validator.validate(bytes)
        val stored = store.put(ByteArrayInputStream(validated.bytes), validated.format.extension)

        val previous = person.profilePath
        person.profilePath = stored.storageKey
        people.saveAndFlush(person)

        // remove the previously stored file, if any
        if (previous != null && previous != stored.storageKey) {
            if (!store.delete(previous)) {
                log.warn("old person photo {} not deleted; leaving for cleanup", previous)
            }
        }
        return PhotoResult(stored.storageKey, validated.format.mediaType, stored.byteSize)
    }

    @Transactional
    fun deletePhoto(personId: UUID): UUID {
        val person = people.findById(personId).orElseThrow { PersonNotFoundException() }
        val current = person.profilePath
        person.profilePath = null
        people.saveAndFlush(person)
        if (current != null && !store.delete(current)) {
            log.warn("person photo {} not deleted on removal", current)
        }
        return personId
    }

    /** The person's photo storage key, or null if they have no photo. */
    @Transactional(readOnly = true)
    fun photoKey(personId: UUID): String? {
        val person = people.findById(personId).orElseThrow { PersonNotFoundException() }
        return person.profilePath
    }

    fun openPhoto(storageKey: String) = store.open(storageKey)
}
