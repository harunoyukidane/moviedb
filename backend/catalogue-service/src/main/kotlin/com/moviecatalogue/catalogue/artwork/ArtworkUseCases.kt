package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.StoredObject
import com.moviecatalogue.media.ValidatedImage
import com.moviecatalogue.media.WebpEncoder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.util.UUID

/**
 * Movie artwork upload/replace/delete (§10). Ordering matters for safety (§13):
 * validate -> write new bytes to the volume -> swap metadata in a DB transaction
 * -> only after commit delete the previous file. If the DB write fails, the new
 * file is compensated (deleted) immediately; the orphan sweeper is the safety net.
 */
@Service
class ArtworkUseCases(
    private val movies: MovieRepository,
    private val artworkRepository: ArtworkRepository,
    private val store: ArtworkStore,
    private val validator: ImageContentValidator,
    private val transactionTemplate: TransactionTemplate,
    private val meterRegistry: io.micrometer.core.instrument.MeterRegistry =
        io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
    private val webpEncoder: WebpEncoder = WebpEncoder(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    // §15 metric: artwork metadata-swap failures (bytes written but DB failed).
    private val artworkFailureCounter = meterRegistry.counter("catalogue.artwork.swap.failure")
    // compensating delete (new object, after a swap failure) itself failed.
    private val compensationFailureCounter = meterRegistry.counter("catalogue.artwork.compensation.failure")
    // best-effort post-commit/post-delete object removal failed (old file leaks to the sweeper).
    private val deleteFailureCounter = meterRegistry.counter("catalogue.artwork.delete.failure")

    /**
     * Upload or replace the movie's primary artwork.
     * @param bytes the fully-read upload (the controller enforces the streaming size gate)
     */
    fun uploadMovieArtwork(movieId: UUID, bytes: ByteArray, originalFilename: String?): ArtworkAsset {
        if (!movies.existsById(movieId)) throw NotFoundException("movie '$movieId' not found")

        // 1-3. validate by content (throws PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE)
        val validated: ValidatedImage = validator.validate(bytes)

        // 4-5. store to the volume (server key + atomic move + sha256)
        val stored = store.put(ByteArrayInputStream(validated.bytes), validated.format.extension)

        // Best-effort WebP variant for Accept-negotiated serving (ArtworkServingController).
        // Never blocks the upload: a missing `cwebp` binary or a failed store.put here just
        // means this asset is served in its primary format only.
        val webpStored = encodeAndStoreWebp(validated)

        val previous = artworkRepository.findByMovieId(movieId)
        val newAsset = ArtworkAsset(
            id = UuidV7.generate(),
            movieId = movieId,
            storageKey = stored.storageKey,
            originalFilename = safeFilename(originalFilename, validated.format.extension),
            mediaType = validated.format.mediaType,
            byteSize = stored.byteSize,
            sha256 = stored.sha256,
            width = validated.width,
            height = validated.height,
            webpStorageKey = webpStored?.storageKey,
            webpByteSize = webpStored?.byteSize,
            webpSha256 = webpStored?.sha256,
        )

        // 6. swap metadata transactionally; compensate the new file if the DB write fails.
        // The existsById check above is a fast-path only — a concurrent deleteMovie can
        // still land between it and this block (or between this re-check and the physical
        // INSERT), which would otherwise violate the movie_id FK and surface as a raw
        // internal error. Re-check inside the transaction, and treat the FK violation
        // itself as the same "movie no longer exists" case rather than letting it leak.
        val saved = try {
            transactionTemplate.execute {
                if (!movies.existsById(movieId)) throw NotFoundException("movie '$movieId' not found")
                previous?.let { artworkRepository.delete(it); artworkRepository.flush() }
                artworkRepository.saveAndFlush(newAsset)
            }!!
        } catch (e: Exception) {
            log.warn("artwork metadata swap failed for movie {}; compensating new file", movieId)
            artworkFailureCounter.increment()
            try {
                store.delete(stored.storageKey) // compensating delete of the just-written bytes
                webpStored?.let { store.delete(it.storageKey) }
            } catch (storageError: com.moviecatalogue.media.ArtworkStorageException) {
                log.warn(
                    "compensation failed for artwork file {}; orphan will remain for sweeper: {}",
                    stored.storageKey,
                    storageError.message,
                )
                compensationFailureCounter.increment()
            }
            if (e is NotFoundException) throw e
            if (e is org.springframework.dao.DataIntegrityViolationException && !movies.existsById(movieId)) {
                throw NotFoundException("movie '$movieId' not found")
            }
            throw e
        }

        // 7. delete the previous file(s) only after commit; best-effort (sweeper retries)
        previous?.let {
            deleteBestEffort(it.storageKey, "old artwork file")
            it.webpStorageKey?.let { webpKey -> deleteBestEffort(webpKey, "old artwork webp file") }
        }
        return saved
    }

    @Transactional
    fun deleteMovieArtwork(movieId: UUID): UUID {
        val asset = artworkRepository.findByMovieId(movieId)
            ?: throw NotFoundException("movie '$movieId' has no artwork")
        val key = asset.storageKey
        val webpKey = asset.webpStorageKey
        artworkRepository.delete(asset)
        artworkRepository.flush()
        // remove bytes after the association is gone; sweeper covers a failed delete
        deleteBestEffort(key, "artwork file")
        webpKey?.let { deleteBestEffort(it, "artwork webp file") }
        return asset.id
    }

    /**
     * Encodes [validated]'s bytes to WebP and stores the result, or returns null if `cwebp`
     * is unavailable, the encode fails, or the store write itself fails - all best-effort,
     * since the primary asset already satisfies every existing caller without this variant.
     */
    private fun encodeAndStoreWebp(validated: ValidatedImage): StoredObject? {
        val webpBytes = webpEncoder.encode(validated.bytes) ?: return null
        return try {
            store.put(ByteArrayInputStream(webpBytes), "webp")
        } catch (e: ArtworkStorageException) {
            log.warn("failed to store WebP variant; serving primary format only: {}", e.message)
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
                log.warn("{} {} not deleted; leaving for orphan sweeper", what, key)
            }
        } catch (e: com.moviecatalogue.media.ArtworkStorageException) {
            log.warn("{} {} not deleted (storage backend threw); leaving for orphan sweeper: {}", what, key, e.message)
            deleteFailureCounter.increment()
        }
    }

    private fun safeFilename(original: String?, extension: String): String {
        // store a sanitized display filename only; never used for the storage path
        val base = original?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()
        return if (base.isNullOrEmpty() || base.length > 255) "artwork.$extension" else base
    }
}
