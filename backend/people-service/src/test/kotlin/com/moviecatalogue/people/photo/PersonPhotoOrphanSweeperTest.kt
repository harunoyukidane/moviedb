package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.people.person.PersonRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersonPhotoOrphanSweeperTest {

    private val people = mockk<PersonRepository>()
    private val store = mockk<ArtworkStore>()
    private val meterRegistry = SimpleMeterRegistry()
    private val sweeper = PersonPhotoOrphanSweeper(people, store, meterRegistry)

    @Test
    fun `a failed listing skips this run cleanly instead of propagating`() {
        every { people.findAllProfilePaths() } returns emptyList()
        every { store.listKeys() } throws ArtworkStorageException()

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(0)
        assertThat(meterRegistry.counter("people.photo.sweep.failure").count()).isEqualTo(1.0)
    }

    @Test
    fun `one key failing to delete does not abort the rest of the sweep`() {
        every { people.findAllProfilePaths() } returns emptyList()
        every { store.listKeys() } returns listOf("a.png", "b.png", "c.png")
        every { store.delete("a.png") } returns true
        every { store.delete("b.png") } throws ArtworkStorageException()
        every { store.delete("c.png") } returns true

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(2)
        assertThat(meterRegistry.counter("people.photo.sweep.failure").count()).isEqualTo(1.0)
        assertThat(meterRegistry.counter("people.photo.sweep.removed").count()).isEqualTo(2.0)
    }
}
