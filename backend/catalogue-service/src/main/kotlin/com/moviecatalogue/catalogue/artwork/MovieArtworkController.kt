package com.moviecatalogue.catalogue.artwork

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Movie artwork media endpoints (§8.1). Binary in/out lives here, not in GraphQL.
 * The 5 MiB cap is enforced both by the multipart config (fast reject) and the
 * content validator.
 */
@RestController
@RequestMapping("/api/movies/{movieId}/artwork")
class MovieArtworkController(
    private val artworkUseCases: ArtworkUseCases,
) {

    @PutMapping(consumes = ["multipart/form-data"])
    fun upload(
        @PathVariable movieId: UUID,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ArtworkResponse> {
        if (file.isEmpty) {
            throw com.moviecatalogue.media.UnsupportedMediaTypeException("empty upload")
        }
        val asset = artworkUseCases.uploadMovieArtwork(movieId, file.bytes, file.originalFilename)
        return ResponseEntity.status(HttpStatus.CREATED).body(asset.toResponse())
    }

    @DeleteMapping
    fun delete(@PathVariable movieId: UUID): ResponseEntity<Map<String, String>> {
        val id = artworkUseCases.deleteMovieArtwork(movieId)
        return ResponseEntity.ok(mapOf("deletedId" to id.toString()))
    }
}

data class ArtworkResponse(
    val id: String,
    val url: String,
    val mediaType: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
)

fun ArtworkAsset.toResponse() = ArtworkResponse(
    id = id.toString(),
    url = "/api/artwork/$id",
    mediaType = mediaType,
    byteSize = byteSize,
    width = width,
    height = height,
)
