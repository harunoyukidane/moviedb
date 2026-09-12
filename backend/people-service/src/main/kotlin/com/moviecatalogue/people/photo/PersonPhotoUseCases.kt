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
 * Person profile-photo upload/delete (ADR-12 hybrid). Uses the same validated
 * ArtworkStore path as movie artwork. The resulting storage key is written to
 * `person.profile_path`; an uploaded photo therefore takes precedence over any
 * imported TMDB URL. Replacing/deleting an uploaded photo removes the old local
 * file; a TMDB URL is external and is simply overwritten (nothing to delete).
 */
@Service
class PersonPhotoUseCases(
    private val people: PersonRepository,
    private val store: ArtworkStore,
    private val validator: ImageContentValidator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Storage keys are UUID + safe extension; a TMDB profile_path is a URL/path, not this. */
    private val uploadedKeyRegex = Regex("^[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")

    data class PhotoResult(val storageKey: String, val mediaType: String, val byteSize: Long)

    @Transactional
    fun uploadPhoto(personId: UUID, bytes: ByteArray): PhotoResult {
        val person = people.findById(personId).orElseThrow { PersonNotFoundException() }
        val validated = validator.validate(bytes)
        val stored = store.put(ByteArrayInputStream(validated.bytes), validated.format.extension)

        val previous = person.profilePath
        person.profilePath = stored.storageKey
        people.saveAndFlush(person)

        // remove the old file only if it was a previously uploaded local key
        if (previous != null && uploadedKeyRegex.matches(previous)) {
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
        if (current == null || !uploadedKeyRegex.matches(current)) {
            // nothing uploaded to remove; clear any value so the hybrid falls back cleanly
            person.profilePath = null
            people.saveAndFlush(person)
            return personId
        }
        person.profilePath = null
        people.saveAndFlush(person)
        if (!store.delete(current)) {
            log.warn("person photo {} not deleted on removal", current)
        }
        return personId
    }

    /** Returns the storage key if the person has an uploaded photo, else null. */
    @Transactional(readOnly = true)
    fun uploadedPhotoKey(personId: UUID): String? {
        val person = people.findById(personId).orElseThrow { PersonNotFoundException() }
        val p = person.profilePath
        return if (p != null && uploadedKeyRegex.matches(p)) p else null
    }

    fun openPhoto(storageKey: String) = store.open(storageKey)
}
