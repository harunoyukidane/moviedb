package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import com.moviecatalogue.media.UnsupportedMediaTypeException
import java.time.Duration
import java.util.UUID

/**
 * Person profile-photo media endpoints. Same validated path as movie artwork.
 * Photos are served from the People service; the GraphQL layer (Catalogue)
 * resolves the person's photo URL via the BFF relay.
 */
@RestController
class PersonPhotoController(
    private val photoUseCases: PersonPhotoUseCases,
    private val store: ArtworkStore,
) {

    @PutMapping("/api/people/{personId}/photo", consumes = ["multipart/form-data"])
    fun upload(
        @PathVariable personId: UUID,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Map<String, Any>> {
        if (file.isEmpty) throw UnsupportedMediaTypeException("empty upload")
        val result = photoUseCases.uploadPhoto(personId, file.bytes)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "url" to "/api/people/$personId/photo",
                "mediaType" to result.mediaType,
                "byteSize" to result.byteSize,
            ),
        )
    }

    @DeleteMapping("/api/people/{personId}/photo")
    fun delete(@PathVariable personId: UUID): ResponseEntity<Map<String, String>> {
        photoUseCases.deletePhoto(personId)
        return ResponseEntity.ok(mapOf("deletedId" to personId.toString()))
    }

    @GetMapping("/api/people/{personId}/photo")
    fun serve(
        @PathVariable personId: UUID,
        @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
        @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.ACCEPT, required = false) accept: String?,
    ): ResponseEntity<StreamingResponseBody> {
        val keys = photoUseCases.photoKeys(personId)
        val primaryKey = keys.storageKey ?: return ResponseEntity.notFound().build()

        // Prefer the WebP variant when the client advertises support for it and it was
        // successfully generated at upload time. `Vary: Accept` is set on every response
        // below so a shared cache never serves one client's negotiated format to a client
        // that asked for a different `Accept`.
        val serveWebp = keys.webpStorageKey != null && accept?.contains("image/webp") == true
        val key = if (serveWebp) keys.webpStorageKey!! else primaryKey

        if (!store.exists(key)) return ResponseEntity.notFound().build()
        val etag = "\"$key\""
        val cacheControl = CacheControl.maxAge(Duration.ofDays(30)).cachePublic()
        if (ifNoneMatch != null && ifNoneMatch.split(",").map { it.trim() }.any { it == etag || it == "*" }) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cacheControl)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .header("X-Content-Type-Options", "nosniff").build()
        }
        val mediaType = when {
            key.endsWith(".png") -> "image/png"
            key.endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
        val input = store.open(key) ?: return ResponseEntity.notFound().build()
        val body = StreamingResponseBody { out -> input.use { it.copyTo(out) } }
        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(cacheControl)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            .contentType(MediaType.parseMediaType(mediaType))
            .header("X-Content-Type-Options", "nosniff")
            .body(body)
    }
}
