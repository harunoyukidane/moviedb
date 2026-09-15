package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.StoredObject
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
    private val useCases = PersonPhotoUseCases(
        people,
        store,
        ImageContentValidator(),
        transactionTemplate,
        meterRegistry,
    )

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
        every { transactionTemplate.execute(any<TransactionCallback<String?>>()) } returns "old.png"
        every { store.delete("old.png") } returns true

        useCases.uploadPhoto(personId, png())

        verifyOrder {
            store.put(any(), any())
            transactionTemplate.execute(any<TransactionCallback<String?>>())
            store.delete("old.png")
        }
        verify(exactly = 0) { store.delete("new.png") }
    }

    @Test
    fun `old photo delete throwing after commit does not fail the request`() {
        val personId = UUID.randomUUID()
        every { people.existsById(personId) } returns true
        every { store.put(any(), any()) } returns StoredObject("new.png", 100, "a".repeat(64))
        every { transactionTemplate.execute(any<TransactionCallback<String?>>()) } returns "old.png"
        every { store.delete("old.png") } throws ArtworkStorageException()

        val result = useCases.uploadPhoto(personId, png())

        assertThat(result.storageKey).isEqualTo("new.png")
        assertThat(meterRegistry.counter("people.photo.delete.failure").count()).isEqualTo(1.0)
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
