package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.StoredObject
import com.moviecatalogue.people.person.PersonRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val useCases = PersonPhotoUseCases(
        people,
        store,
        ImageContentValidator(),
        transactionTemplate,
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

    private fun png(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }
}
