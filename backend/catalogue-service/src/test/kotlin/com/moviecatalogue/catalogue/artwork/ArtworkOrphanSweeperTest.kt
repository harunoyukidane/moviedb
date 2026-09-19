package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.StoredKey
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ArtworkOrphanSweeperTest {

    private val artworkRepository = mockk<ArtworkRepository>()
    private val store = mockk<ArtworkStore>()
    private val meterRegistry = SimpleMeterRegistry()
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sweeper = ArtworkOrphanSweeper(artworkRepository, store, meterRegistry, Duration.ofMinutes(15), clock)

    private fun old(key: String) = StoredKey(key, now.minus(Duration.ofMinutes(20)))
    private fun fresh(key: String) = StoredKey(key, now.minus(Duration.ofSeconds(5)))

    @Test
    fun `a failed listing skips this run cleanly instead of propagating`() {
        every { artworkRepository.findAllStorageKeys() } returns emptyList()
        every { artworkRepository.findAllWebpStorageKeys() } returns emptyList()
        every { store.listKeysWithAge() } throws ArtworkStorageException()

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(0)
        assertThat(meterRegistry.counter("catalogue.artwork.sweep.failure").count()).isEqualTo(1.0)
    }

    @Test
    fun `one key failing to delete does not abort the rest of the sweep`() {
        every { artworkRepository.findAllStorageKeys() } returns emptyList()
        every { artworkRepository.findAllWebpStorageKeys() } returns emptyList()
        every { store.listKeysWithAge() } returns listOf(old("a.png"), old("b.png"), old("c.png"))
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
        every { store.listKeysWithAge() } returns listOf(old("a.png"), old("a.webp"))

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(0)
    }

    @Test
    fun `an unreferenced object written just now survives the sweep (V2_6-03)`() {
        every { artworkRepository.findAllStorageKeys() } returns emptyList()
        every { artworkRepository.findAllWebpStorageKeys() } returns emptyList()
        every { store.listKeysWithAge() } returns listOf(fresh("a.png"))

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(0)
    }

    @Test
    fun `an old unreferenced object is still removed (V2_6-03)`() {
        every { artworkRepository.findAllStorageKeys() } returns emptyList()
        every { artworkRepository.findAllWebpStorageKeys() } returns emptyList()
        every { store.listKeysWithAge() } returns listOf(old("a.png"))
        every { store.delete("a.png") } returns true

        val removed = sweeper.sweepOnce()

        assertThat(removed).isEqualTo(1)
    }
}
