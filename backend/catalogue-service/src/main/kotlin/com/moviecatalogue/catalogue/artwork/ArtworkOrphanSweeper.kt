package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.media.ArtworkStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Best-effort orphan sweeper (§13 safety net). Files can be orphaned if a DB swap
 * fails after a write (compensation covers the common case) or if deleting an old
 * file fails after a successful replacement. Periodically removes stored files
 * that no `artwork_asset` row references. Deliberately conservative: it only
 * deletes keys that are present in the store but absent from metadata, and
 * (V2.6-03) old enough that they can't be an upload still mid-flight - the
 * primary object is written, then WebP-encoded (up to ~10s), then committed to
 * metadata, so an unreferenced-but-fresh object is normal, not orphaned.
 */
@Component
class ArtworkOrphanSweeper(
    private val artworkRepository: ArtworkRepository,
    private val store: ArtworkStore,
    private val meterRegistry: io.micrometer.core.instrument.MeterRegistry =
        io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
    @Value("\${catalogue.artwork.sweep.min-age:PT15M}") private val minAge: Duration = Duration.ofMinutes(15),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val removedCounter = meterRegistry.counter("catalogue.artwork.sweep.removed")
    private val failureCounter = meterRegistry.counter("catalogue.artwork.sweep.failure")

    /** Runs periodically; the first run is delayed to let the app settle. */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT30M")
    fun sweep(): Int {
        return sweepOnce()
    }

    /**
     * Exposed for tests and manual triggering; returns the number of files removed. Tolerates a
     * transient object-store failure: a failed listing skips this run cleanly (the next
     * scheduled run retries); a failed per-key delete is logged/counted but does not abort the
     * rest of the sweep.
     */
    fun sweepOnce(): Int {
        val referenced = (artworkRepository.findAllStorageKeys() + artworkRepository.findAllWebpStorageKeys()).toHashSet()
        val onDisk = try {
            store.listKeysWithAge()
        } catch (e: com.moviecatalogue.media.ArtworkStorageException) {
            log.warn("orphan sweep skipped this run; storage listing failed: {}", e.message)
            failureCounter.increment()
            return 0
        }
        val cutoff = Instant.now(clock).minus(minAge)
        var removed = 0
        for (stored in onDisk) {
            if (stored.key in referenced) continue
            if (stored.lastModified.isAfter(cutoff)) continue
            try {
                if (store.delete(stored.key)) {
                    removed++
                    log.info("orphan sweeper removed unreferenced artwork file {}", stored.key)
                }
            } catch (e: com.moviecatalogue.media.ArtworkStorageException) {
                log.warn("orphan sweeper failed to remove artwork file {}: {}", stored.key, e.message)
                failureCounter.increment()
            }
        }
        if (removed > 0) {
            log.info("orphan sweeper removed {} unreferenced artwork file(s)", removed)
            removedCounter.increment(removed.toDouble())
        }
        return removed
    }
}
