package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.media.ArtworkStore
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.time.Duration
import java.util.UUID

/**
 * Serves artwork bytes (§8.1, §10). Cacheable binary response with a strong ETag
 * (the content SHA-256), long-lived immutable caching (the key/content never
 * changes for a given artwork id), and `X-Content-Type-Options: nosniff`. Honors
 * conditional requests with a 304 when the ETag matches.
 */
@RestController
class ArtworkServingController(
    private val artworkRepository: ArtworkRepository,
    private val store: ArtworkStore,
) {

    @GetMapping("/api/artwork/{artworkId}")
    fun serve(
        @PathVariable artworkId: UUID,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
    ): ResponseEntity<StreamingResponseBody> {
        val asset = artworkRepository.findById(artworkId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (!store.exists(asset.storageKey)) return ResponseEntity.notFound().build()

        val etag = "\"${asset.sha256}\""
        val cacheControl = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()

        // Conditional request: 304 when the client already has this exact content.
        if (ifNoneMatch != null && ifNoneMatch.split(",").map { it.trim() }.any { it == etag || it == "*" }) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .cacheControl(cacheControl)
                .header("X-Content-Type-Options", "nosniff")
                .build()
        }

        val input = store.open(asset.storageKey) ?: return ResponseEntity.notFound().build()
        val body = StreamingResponseBody { out -> input.use { it.copyTo(out) } }
        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(cacheControl)
            .contentType(MediaType.parseMediaType(asset.mediaType))
            .contentLength(asset.byteSize)
            .header("X-Content-Type-Options", "nosniff")
            .body(body)
    }
}
