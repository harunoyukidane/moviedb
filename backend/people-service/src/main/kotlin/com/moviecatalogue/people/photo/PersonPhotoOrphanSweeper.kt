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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT30M")
    fun sweep(): Int = sweepOnce()

    fun sweepOnce(): Int {
        val referenced = people.findAllProfilePaths().toHashSet()
        var removed = 0
        store.listKeys().forEach { key ->
            if (key !in referenced && store.delete(key)) {
                removed++
                log.info("person photo orphan sweeper removed unreferenced object {}", key)
            }
        }
        return removed
    }
}
