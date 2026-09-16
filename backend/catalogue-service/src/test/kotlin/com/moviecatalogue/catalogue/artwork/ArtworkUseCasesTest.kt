package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.StoredObject
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.moviecatalogue.catalogue.domain.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

class ArtworkUseCasesTest {

    private val movies = mockk<MovieRepository>()
    private val artworkRepository = mockk<ArtworkRepository>(relaxed = true)
    private val store = mockk<ArtworkStore>()
    private val validator = ImageContentValidator()
    private val meterRegistry = SimpleMeterRegistry()

    // A TransactionTemplate that just runs the callback (no real tx) but can be made to "fail".
    private val txTemplate = mockk<TransactionTemplate>()

    private val useCases = ArtworkUseCases(movies, artworkRepository, store, validator, txTemplate, meterRegistry)

    private fun pngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    @Test
    fun `db failure compensates by deleting the newly written file`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new-key.png", 100, "a".repeat(64))
        every { artworkRepository.findByMovieId(movieId) } returns null
        // simulate DB swap throwing
        every { txTemplate.execute(any<TransactionCallback<*>>()) } throws RuntimeException("db down")
        every { store.delete("new-key.png") } returns true

        assertThatThrownBy { useCases.uploadMovieArtwork(movieId, pngBytes(), "poster.png") }
            .isInstanceOf(RuntimeException::class.java)

        // compensating delete of the just-written file
        verify(exactly = 1) { store.delete("new-key.png") }
    }

    @Test
    fun `successful replace deletes old file only after commit`() {
        val movieId = UUID.randomUUID()
        val old = ArtworkAsset(
            id = UUID.randomUUID(), movieId = movieId, storageKey = "old-key.png",
            originalFilename = "old.png", mediaType = "image/png", byteSize = 50, sha256 = "b".repeat(64),
        )
        every { movies.existsById(movieId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new-key.png", 100, "c".repeat(64))
        every { artworkRepository.findByMovieId(movieId) } returns old
        val newAsset = slotAsset()
        every { txTemplate.execute(any<TransactionCallback<ArtworkAsset>>()) } answers {
            // emulate the tx body result
            newAsset
        }
        every { store.delete("old-key.png") } returns true

        useCases.uploadMovieArtwork(movieId, pngBytes(), "poster.png")

        // new file written before the old one is removed; old removed after commit
        verifyOrder {
            store.put(any(), any())
            txTemplate.execute(any<TransactionCallback<ArtworkAsset>>())
            store.delete("old-key.png")
        }
        // the new file is NOT deleted on the success path
        verify(exactly = 0) { store.delete("new-key.png") }
    }

    @Test
    fun `old file delete throwing after commit does not fail the request`() {
        val movieId = UUID.randomUUID()
        val old = ArtworkAsset(
            id = UUID.randomUUID(), movieId = movieId, storageKey = "old-key.png",
            originalFilename = "old.png", mediaType = "image/png", byteSize = 50, sha256 = "b".repeat(64),
        )
        every { movies.existsById(movieId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new-key.png", 100, "c".repeat(64))
        every { artworkRepository.findByMovieId(movieId) } returns old
        every { txTemplate.execute(any<TransactionCallback<ArtworkAsset>>()) } answers { slotAsset() }
        every { store.delete("old-key.png") } throws ArtworkStorageException()

        val result = useCases.uploadMovieArtwork(movieId, pngBytes(), "poster.png")

        assertThat(result.storageKey).isEqualTo("new-key.png")
        assertThat(meterRegistry.counter("catalogue.artwork.delete.failure").count()).isEqualTo(1.0)
    }

    @Test
    fun `compensating delete throwing does not mask the original db failure`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new-key.png", 100, "a".repeat(64))
        every { artworkRepository.findByMovieId(movieId) } returns null
        every { txTemplate.execute(any<TransactionCallback<*>>()) } throws RuntimeException("db down")
        every { store.delete("new-key.png") } throws ArtworkStorageException()

        assertThatThrownBy { useCases.uploadMovieArtwork(movieId, pngBytes(), "poster.png") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("db down")

        assertThat(meterRegistry.counter("catalogue.artwork.compensation.failure").count()).isEqualTo(1.0)
    }

    @Test
    fun `upload throws NotFound when the movie is deleted concurrently mid-transaction`() {
        // Regression test (V2-16 load test finding): existsById was only checked
        // once, before validation/storage I/O; a deleteMovie racing in after that
        // check used to reach the metadata INSERT and violate the movie_id FK,
        // surfacing as a raw internal error (HTTP 500 with no body) instead of a
        // clean NOT_FOUND. The use case now re-checks inside the transaction.
        val movieId = UUID.randomUUID()
        // fast-path check passes; the movie is deleted before the in-transaction re-check
        every { movies.existsById(movieId) } returnsMany listOf(true, false)
        every { store.put(any(), any()) } returns StoredObject("new-key.png", 100, "d".repeat(64))
        every { artworkRepository.findByMovieId(movieId) } returns null
        every { store.delete("new-key.png") } returns true
        val callback = slot<TransactionCallback<ArtworkAsset>>()
        every { txTemplate.execute(capture(callback)) } answers { callback.captured.doInTransaction(mockk(relaxed = true)) }

        assertThatThrownBy { useCases.uploadMovieArtwork(movieId, pngBytes(), "poster.png") }
            .isInstanceOf(NotFoundException::class.java)

        // the just-written bytes are still compensated even though the metadata
        // write never happened
        verify(exactly = 1) { store.delete("new-key.png") }
        verify(exactly = 0) { artworkRepository.saveAndFlush(any()) }
    }

    private fun slotAsset() = ArtworkAsset(
        id = UUID.randomUUID(), movieId = UUID.randomUUID(), storageKey = "new-key.png",
        originalFilename = "poster.png", mediaType = "image/png", byteSize = 100, sha256 = "c".repeat(64),
    )
}
