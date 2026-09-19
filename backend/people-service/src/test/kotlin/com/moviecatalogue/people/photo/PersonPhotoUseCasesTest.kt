package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.StoredObject
import com.moviecatalogue.media.WebpEncoder
import com.moviecatalogue.people.person.PersonRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
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

class PersonPhotoUseCasesTest {
    private val people = mockk<PersonRepository>()
    private val store = mockk<ArtworkStore>()
    private val transactionTemplate = mockk<TransactionTemplate>()
    private val meterRegistry = SimpleMeterRegistry()
    // Encoding is off by default in these tests (returns null) so the existing scenarios
    // exercise the "no webp variant" path without depending on `cwebp` being installed.
    private val webpEncoder = mockk<WebpEncoder>()
    private val useCases = PersonPhotoUseCases(
        people,
        store,
        ImageContentValidator(),
        transactionTemplate,
        meterRegistry,
        webpEncoder,
    )

    init {
        every { webpEncoder.encode(any()) } returns null
    }

    @Test
    fun `database failure compensates newly stored photo`() {
        val personId = UUID.randomUUID()
        every { people.existsById(personId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new.png", 100, "a".repeat(64))
        every { transactionTemplate.execute(any<TransactionCallback<*>>()) } throws RuntimeException("db down")
        every { store.delete("new.png") } returns true

        assertThatThrownBy { useCases.uploadPhoto(personId, png()) }
            .isInstanceOf(RuntimeException::class.java)

        verify(exactly = 1) { store.delete("new.png") }
    }

    @Test
    fun `successful replace deletes old photo only after commit`() {
        val personId = UUID.randomUUID()
        every { people.existsById(personId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new.png", 100, "a".repeat(64))
        every { transactionTemplate.execute(any<TransactionCallback<PersonPhotoUseCases.PhotoKeys?>>()) } returns
            PersonPhotoUseCases.PhotoKeys("old.png", null)
        every { store.delete("old.png") } returns true

        useCases.uploadPhoto(personId, png())

        verifyOrder {
            store.put(any(), any())
            transactionTemplate.execute(any<TransactionCallback<PersonPhotoUseCases.PhotoKeys?>>())
            store.delete("old.png")
        }
        verify(exactly = 0) { store.delete("new.png") }
    }

    @Test
    fun `old photo delete throwing after commit does not fail the request`() {
        val personId = UUID.randomUUID()
        every { people.existsById(personId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new.png", 100, "a".repeat(64))
        every { transactionTemplate.execute(any<TransactionCallback<PersonPhotoUseCases.PhotoKeys?>>()) } returns
            PersonPhotoUseCases.PhotoKeys("old.png", null)
        every { store.delete("old.png") } throws ArtworkStorageException()

        val result = useCases.uploadPhoto(personId, png())

        assertThat(result.storageKey).isEqualTo("new.png")
        assertThat(meterRegistry.counter("people.photo.delete.failure").count()).isEqualTo(1.0)
    }

    @Test
    fun `stores a webp variant when the encoder succeeds`() {
        val personId = UUID.randomUUID()
        every { people.existsById(personId) } returns true
        every { store.put(any(), "png") } returns StoredObject("new.png", 100, "a".repeat(64))
        every { webpEncoder.encode(any()) } returns ByteArray(10)
        every { store.put(any(), "webp") } returns StoredObject("new.webp", 10, "e".repeat(64))
        every { transactionTemplate.execute(any<TransactionCallback<PersonPhotoUseCases.PhotoKeys?>>()) } returns
            PersonPhotoUseCases.PhotoKeys(null, null)

        useCases.uploadPhoto(personId, png())

        verify(exactly = 1) { store.put(any(), "webp") }
    }

    @Test
    fun `skips the webp variant when the encoder returns nothing`() {
        val personId = UUID.randomUUID()
        every { people.existsById(personId) } returns true
        every { store.put(any(), "png") } returns StoredObject("new.png", 100, "a".repeat(64))
        every { transactionTemplate.execute(any<TransactionCallback<PersonPhotoUseCases.PhotoKeys?>>()) } returns
            PersonPhotoUseCases.PhotoKeys(null, null)

        useCases.uploadPhoto(personId, png())

        verify(exactly = 0) { store.put(any(), "webp") }
    }

    @Test
    fun `db failure also compensates the newly written webp file`() {
        val personId = UUID.randomUUID()
        every { people.existsById(personId) } returns true
        every { store.put(any(), "png") } returns StoredObject("new.png", 100, "a".repeat(64))
        every { webpEncoder.encode(any()) } returns ByteArray(10)
        every { store.put(any(), "webp") } returns StoredObject("new.webp", 10, "e".repeat(64))
        every { transactionTemplate.execute(any<TransactionCallback<*>>()) } throws RuntimeException("db down")
        every { store.delete("new.png") } returns true
        every { store.delete("new.webp") } returns true

        assertThatThrownBy { useCases.uploadPhoto(personId, png()) }
            .isInstanceOf(RuntimeException::class.java)

        verify(exactly = 1) { store.delete("new.png") }
        verify(exactly = 1) { store.delete("new.webp") }
    }

    @Test
    fun `compensating delete throwing does not mask the original db failure`() {
        val personId = UUID.randomUUID()
        every { people.existsById(personId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new.png", 100, "a".repeat(64))
        every { transactionTemplate.execute(any<TransactionCallback<*>>()) } throws RuntimeException("db down")
        every { store.delete("new.png") } throws ArtworkStorageException()

        assertThatThrownBy { useCases.uploadPhoto(personId, png()) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("db down")

        assertThat(meterRegistry.counter("people.photo.compensation.failure").count()).isEqualTo(1.0)
    }

    private fun png(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }
}
