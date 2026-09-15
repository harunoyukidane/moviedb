package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.ArtworkStore
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Optional
import java.util.UUID

/** Proves a genuine MinIO outage during serving maps to a sanitized 503, not a default 500. */
@WebMvcTest(controllers = [ArtworkServingController::class])
class ArtworkExceptionAdviceTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var artworkRepository: ArtworkRepository

    @MockBean lateinit var store: ArtworkStore

    @Test
    fun `a storage backend outage while serving returns 503, not a default 500`() {
        val asset = ArtworkAsset(
            id = UUID.randomUUID(), movieId = UUID.randomUUID(), storageKey = "key.png",
            originalFilename = "poster.png", mediaType = "image/png", byteSize = 10, sha256 = "a".repeat(64),
        )
        given(artworkRepository.findById(asset.id)).willReturn(Optional.of(asset))
        given(store.exists("key.png")).willThrow(ArtworkStorageException())

        mockMvc.get("/api/artwork/${asset.id}")
            .andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("STORAGE_UNAVAILABLE") }
            }
    }
}
