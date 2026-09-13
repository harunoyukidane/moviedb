package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.ValidatedImage
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
) {
    private val log = LoggerFactory.getLogger(javaClass)
    // §15 metric: artwork metadata-swap failures (bytes written but DB failed).
    private val artworkFailureCounter = meterRegistry.counter("catalogue.artwork.swap.failure")

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
        )

        // 6. swap metadata transactionally; compensate the new file if the DB write fails
        val saved = try {
            transactionTemplate.execute {
                previous?.let { artworkRepository.delete(it); artworkRepository.flush() }
                artworkRepository.saveAndFlush(newAsset)
            }!!
        } catch (e: Exception) {
            log.warn("artwork metadata swap failed for movie {}; compensating new file", movieId)
            artworkFailureCounter.increment()
            store.delete(stored.storageKey) // compensating delete of the just-written bytes
            throw e
        }

        // 7. delete the previous file only after commit; best-effort (sweeper retries)
        previous?.let {
            if (!store.delete(it.storageKey)) {
                log.warn("old artwork file {} not deleted; leaving for orphan sweeper", it.storageKey)
            }
        }
        return saved
    }

    @Transactional
    fun deleteMovieArtwork(movieId: UUID): UUID {
        val asset = artworkRepository.findByMovieId(movieId)
            ?: throw NotFoundException("movie '$movieId' has no artwork")
        val key = asset.storageKey
        artworkRepository.delete(asset)
        artworkRepository.flush()
        // remove bytes after the association is gone; sweeper covers a failed delete
        if (!store.delete(key)) {
            log.warn("artwork file {} not deleted on removal; leaving for orphan sweeper", key)
        }
        return asset.id
    }

    private fun safeFilename(original: String?, extension: String): String {
        // store a sanitized display filename only; never used for the storage path
        val base = original?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()
        return if (base.isNullOrEmpty() || base.length > 255) "artwork.$extension" else base
    }
}
