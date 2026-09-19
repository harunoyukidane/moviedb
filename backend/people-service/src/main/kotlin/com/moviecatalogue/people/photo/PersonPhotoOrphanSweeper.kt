package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.people.person.PersonRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Removes photo objects that no person metadata row references. */
@Component
class PersonPhotoOrphanSweeper(
    private val people: PersonRepository,
    private val store: ArtworkStore,
    private val meterRegistry: io.micrometer.core.instrument.MeterRegistry =
        io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
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
            store.listKeys()
        } catch (e: com.moviecatalogue.media.ArtworkStorageException) {
            log.warn("orphan sweep skipped this run; storage listing failed: {}", e.message)
            failureCounter.increment()
            return 0
        }
        var removed = 0
        onDisk.forEach { key ->
            if (key !in referenced) {
                try {
                    if (store.delete(key)) {
                        removed++
                        log.info("person photo orphan sweeper removed unreferenced object {}", key)
                    }
                } catch (e: com.moviecatalogue.media.ArtworkStorageException) {
                    log.warn("person photo orphan sweeper failed to remove object {}: {}", key, e.message)
                    failureCounter.increment()
                }
            }
        }
        if (removed > 0) removedCounter.increment(removed.toDouble())
        return removed
    }
}
