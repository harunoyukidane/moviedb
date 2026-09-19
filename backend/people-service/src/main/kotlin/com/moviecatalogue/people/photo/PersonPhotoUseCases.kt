package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.StoredObject
import com.moviecatalogue.media.ValidatedImage
import com.moviecatalogue.media.WebpEncoder
import com.moviecatalogue.people.domain.PersonNotFoundException
import com.moviecatalogue.people.person.PersonRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
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
    private val transactionTemplate: TransactionTemplate,
    private val meterRegistry: io.micrometer.core.instrument.MeterRegistry =
        io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
    private val webpEncoder: WebpEncoder = WebpEncoder(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    // compensating delete (new object, after a swap failure) itself failed.
    private val compensationFailureCounter = meterRegistry.counter("people.photo.compensation.failure")
    // best-effort post-commit/post-delete object removal failed (old object leaks to the sweeper).
    private val deleteFailureCounter = meterRegistry.counter("people.photo.delete.failure")

    data class PhotoResult(val storageKey: String, val mediaType: String, val byteSize: Long)

    // internal (not private) so the use-case tests, which stub transactionTemplate.execute()'s
    // return value directly rather than running the real callback body, can construct one.
    internal data class PhotoKeys(val storageKey: String?, val webpStorageKey: String?)

    fun uploadPhoto(personId: UUID, bytes: ByteArray): PhotoResult {
        if (!people.existsById(personId)) throw PersonNotFoundException()
        val validated = validator.validate(bytes)
        val stored = store.put(ByteArrayInputStream(validated.bytes), validated.format.extension)

        // Best-effort WebP variant for Accept-negotiated serving (PersonPhotoController).
        // Never blocks the upload: a missing `cwebp` binary or a failed store.put here just
        // means this photo is served in its primary format only.
        val webpStored = encodeAndStoreWebp(validated)

        // The database swap owns the reference. If it fails, compensate the new
        // object(s) immediately; only delete the old object(s) after the commit.
        val previous = try {
            transactionTemplate.execute {
                val person = people.findById(personId).orElseThrow { PersonNotFoundException() }
                val old = PhotoKeys(person.profilePath, person.profilePathWebp)
                person.profilePath = stored.storageKey
                person.profilePathWebp = webpStored?.storageKey
                people.saveAndFlush(person)
                old
            }
        } catch (e: Exception) {
            try {
                if (!store.delete(stored.storageKey)) {
                    log.warn("failed to compensate new person photo {}", stored.storageKey)
                }
                webpStored?.let { store.delete(it.storageKey) }
            } catch (storageError: com.moviecatalogue.media.ArtworkStorageException) {
                log.warn(
                    "compensation failed for person photo {}; orphan will remain for sweeper: {}",
                    stored.storageKey,
                    storageError.message,
                )
                compensationFailureCounter.increment()
            }
            throw e
        }

        // The transaction has committed; the old object(s) are no longer referenced.
        if (previous != null) {
            if (previous.storageKey != null && previous.storageKey != stored.storageKey) {
                deleteBestEffort(previous.storageKey, "old person photo")
            }
            if (previous.webpStorageKey != null && previous.webpStorageKey != webpStored?.storageKey) {
                deleteBestEffort(previous.webpStorageKey, "old person photo webp file")
            }
        }
        return PhotoResult(stored.storageKey, validated.format.mediaType, stored.byteSize)
    }

    fun deletePhoto(personId: UUID): UUID {
        val current = transactionTemplate.execute {
            val person = people.findById(personId).orElseThrow { PersonNotFoundException() }
            val old = PhotoKeys(person.profilePath, person.profilePathWebp)
            person.profilePath = null
            person.profilePathWebp = null
            people.saveAndFlush(person)
            old
        }
        current?.storageKey?.let { deleteBestEffort(it, "person photo") }
        current?.webpStorageKey?.let { deleteBestEffort(it, "person photo webp file") }
        return personId
    }

    /**
     * Encodes [validated]'s bytes to WebP and stores the result, or returns null if `cwebp`
     * is unavailable, the encode fails, or the store write itself fails - all best-effort,
     * since the primary photo already satisfies every existing caller without this variant.
     */
    private fun encodeAndStoreWebp(validated: ValidatedImage): StoredObject? {
        val webpBytes = webpEncoder.encode(validated.bytes) ?: return null
        return try {
            store.put(ByteArrayInputStream(webpBytes), "webp")
        } catch (e: ArtworkStorageException) {
            log.warn("failed to store person photo WebP variant; serving primary format only: {}", e.message)
            null
        }
    }

    /**
     * Best-effort object removal after the metadata change has already committed. Never
     * propagates: a transient storage failure here must not surface as an error for a request
     * that already succeeded — the orphan sweeper is the safety net.
     */
    private fun deleteBestEffort(key: String, what: String) {
        try {
            if (!store.delete(key)) {
                log.warn("{} {} not deleted; leaving for cleanup", what, key)
            }
        } catch (e: com.moviecatalogue.media.ArtworkStorageException) {
            log.warn("{} {} not deleted (storage backend threw); leaving for cleanup: {}", what, key, e.message)
            deleteFailureCounter.increment()
        }
    }

    /** The person's primary and (if generated) WebP variant photo storage keys, in one lookup. */
    @Transactional(readOnly = true)
    internal fun photoKeys(personId: UUID): PhotoKeys {
        val person = people.findById(personId).orElseThrow { PersonNotFoundException() }
        return PhotoKeys(person.profilePath, person.profilePathWebp)
    }

    fun openPhoto(storageKey: String) = store.open(storageKey)
}
