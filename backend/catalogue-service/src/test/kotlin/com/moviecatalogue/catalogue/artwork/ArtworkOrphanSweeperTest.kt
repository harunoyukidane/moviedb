package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArtworkOrphanSweeperTest {

    private val artworkRepository = mockk<ArtworkRepository>()
    private val store = mockk<ArtworkStore>()
    private val meterRegistry = SimpleMeterRegistry()
    private val sweeper = ArtworkOrphanSweeper(artworkRepository, store, meterRegistry)

    @Test
    fun `a failed listing skips this run cleanly instead of propagating`() {
        every { artworkRepository.findAllStorageKeys() } returns emptyList()
        every { artworkRepository.findAllWebpStorageKeys() } returns emptyList()
        every { store.listKeys() } throws ArtworkStorageException()

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(0)
        assertThat(meterRegistry.counter("catalogue.artwork.sweep.failure").count()).isEqualTo(1.0)
    }

    @Test
    fun `one key failing to delete does not abort the rest of the sweep`() {
        every { artworkRepository.findAllStorageKeys() } returns emptyList()
        every { artworkRepository.findAllWebpStorageKeys() } returns emptyList()
        every { store.listKeys() } returns listOf("a.png", "b.png", "c.png")
        every { store.delete("a.png") } returns true
        every { store.delete("b.png") } throws ArtworkStorageException()
        every { store.delete("c.png") } returns true

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(2)
        assertThat(meterRegistry.counter("catalogue.artwork.sweep.failure").count()).isEqualTo(1.0)
        assertThat(meterRegistry.counter("catalogue.artwork.sweep.removed").count()).isEqualTo(2.0)
    }

    @Test
    fun `a referenced webp variant is not treated as an orphan`() {
        every { artworkRepository.findAllStorageKeys() } returns listOf("a.png")
        every { artworkRepository.findAllWebpStorageKeys() } returns listOf("a.webp")
        every { store.listKeys() } returns listOf("a.png", "a.webp")

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(0)
    }
}
