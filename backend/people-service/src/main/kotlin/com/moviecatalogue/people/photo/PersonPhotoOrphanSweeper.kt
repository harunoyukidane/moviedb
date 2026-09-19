package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.people.person.PersonRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Removes photo objects that no person metadata row references, but (V2.6-03)
 * only once they're old enough that they can't be an upload still mid-flight -
 * the primary object is written, then WebP-encoded (up to ~10s), then committed
 * to metadata, so an unreferenced-but-fresh object is normal, not orphaned.
 */
@Component
class PersonPhotoOrphanSweeper(
    private val people: PersonRepository,
    private val store: ArtworkStore,
    private val meterRegistry: io.micrometer.core.instrument.MeterRegistry =
        io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
    @Value("\${people.photo.sweep.min-age:PT15M}") private val minAge: Duration = Duration.ofMinutes(15),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val removedCounter = meterRegistry.counter("people.photo.sweep.removed")
    private val failureCounter = meterRegistry.counter("people.photo.sweep.failure")

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT30M")
    fun sweep(): Int = sweepOnce()

    /**
     * Tolerates a transient object-store failure: a failed listing skips this run cleanly (the
     * next scheduled run retries); a failed per-key delete is logged/counted but does not abort
     * the rest of the sweep.
     */
    fun sweepOnce(): Int {
        val referenced = (people.findAllProfilePaths() + people.findAllProfilePathsWebp()).toHashSet()
        val onDisk = try {
            store.listKeysWithAge()
        } catch (e: com.moviecatalogue.media.ArtworkStorageException) {
            log.warn("orphan sweep skipped this run; storage listing failed: {}", e.message)
            failureCounter.increment()
            return 0
        }
        val cutoff = Instant.now(clock).minus(minAge)
        var removed = 0
        onDisk.forEach { stored ->
            if (stored.key !in referenced && !stored.lastModified.isAfter(cutoff)) {
                try {
                    if (store.delete(stored.key)) {
                        removed++
                        log.info("person photo orphan sweeper removed unreferenced object {}", stored.key)
                    }
                } catch (e: com.moviecatalogue.media.ArtworkStorageException) {
                    log.warn("person photo orphan sweeper failed to remove object {}: {}", stored.key, e.message)
                    failureCounter.increment()
                }
            }
        }
        if (removed > 0) removedCounter.increment(removed.toDouble())
        return removed
    }
}
