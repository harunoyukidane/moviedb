package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.media.ArtworkStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Best-effort orphan sweeper (§13 safety net). Files can be orphaned if a DB swap
 * fails after a write (compensation covers the common case) or if deleting an old
 * file fails after a successful replacement. Periodically removes stored files
 * that no `artwork_asset` row references. Deliberately conservative: it only
 * deletes keys that are present in the store but absent from metadata.
 */
@Component
class ArtworkOrphanSweeper(
    private val artworkRepository: ArtworkRepository,
    private val store: ArtworkStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Runs periodically; the first run is delayed to let the app settle. */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT30M")
    fun sweep(): Int {
        return sweepOnce()
    }

    /** Exposed for tests and manual triggering; returns the number of files removed. */
    fun sweepOnce(): Int {
        val referenced = artworkRepository.findAllStorageKeys().toHashSet()
        val onDisk = store.listKeys()
        var removed = 0
        for (key in onDisk) {
            if (key !in referenced) {
                if (store.delete(key)) {
                    removed++
                    log.info("orphan sweeper removed unreferenced artwork file {}", key)
                }
            }
        }
        if (removed > 0) log.info("orphan sweeper removed {} unreferenced artwork file(s)", removed)
        return removed
    }
}
