package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

/** Proves a genuine MinIO outage during serving maps to a sanitized 503, not a default 500. */
@WebMvcTest(controllers = [PersonPhotoController::class])
class PhotoExceptionAdviceTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var photoUseCases: PersonPhotoUseCases

    @MockBean lateinit var store: ArtworkStore

    @Test
    fun `a storage backend outage while serving returns 503, not a default 500`() {
        val personId = UUID.randomUUID()
        given(photoUseCases.photoKey(personId)).willReturn("key.png")
        given(store.exists("key.png")).willThrow(ArtworkStorageException())

        mockMvc.get("/api/people/$personId/photo")
            .andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("STORAGE_UNAVAILABLE") }
            }
    }
}
