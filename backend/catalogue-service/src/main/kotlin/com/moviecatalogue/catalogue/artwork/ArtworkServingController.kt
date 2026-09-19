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
 * (the content SHA-256) and long-lived immutable caching (the key/content never
 * changes for a given artwork id). Honors conditional requests with a 304 when
 * the ETag matches. `X-Content-Type-Options: nosniff` is not set here - every
 * response on this service already gets it from SecurityHeadersFilter; adding
 * it again on the ResponseEntity would duplicate the header (Spring MVC writes
 * ResponseEntity headers via addHeader, which appends rather than replaces).
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
        @RequestHeader(value = HttpHeaders.ACCEPT, required = false) accept: String?,
    ): ResponseEntity<StreamingResponseBody> {
        val asset = artworkRepository.findById(artworkId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        // Prefer the WebP variant when the client advertises support for it and it was
        // successfully generated at upload time; otherwise fall back to the primary asset.
        // `Vary: Accept` is set on every response below so a shared cache never serves one
        // client's negotiated format to a client that asked for a different `Accept`.
        val serveWebp = asset.webpStorageKey != null && (accept?.contains("image/webp") == true)
        val storageKey = if (serveWebp) asset.webpStorageKey!! else asset.storageKey
        val mediaType = if (serveWebp) "image/webp" else asset.mediaType
        val byteSize = if (serveWebp) asset.webpByteSize!! else asset.byteSize
        val sha256 = if (serveWebp) asset.webpSha256!! else asset.sha256

        if (!store.exists(storageKey)) return ResponseEntity.notFound().build()

        val etag = "\"$sha256\""
        val cacheControl = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()

        // Conditional request: 304 when the client already has this exact content.
        if (ifNoneMatch != null && ifNoneMatch.split(",").map { it.trim() }.any { it == etag || it == "*" }) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .cacheControl(cacheControl)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .build()
        }

        val input = store.open(storageKey) ?: return ResponseEntity.notFound().build()
        val body = StreamingResponseBody { out -> input.use { it.copyTo(out) } }
        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(cacheControl)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            .contentType(MediaType.parseMediaType(mediaType))
            .contentLength(byteSize)
            .body(body)
    }
}
