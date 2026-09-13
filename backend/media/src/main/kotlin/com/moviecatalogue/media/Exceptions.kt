package com.moviecatalogue.media

/** Raised when an upload exceeds the configured size limit. -> PAYLOAD_TOO_LARGE */
class PayloadTooLargeException(message: String = "artwork exceeds the size limit") :
    RuntimeException(message)

/** Raised when content is not a valid, allowed image. -> UNSUPPORTED_MEDIA_TYPE */
class UnsupportedMediaTypeException(message: String = "artwork type is not allowed") :
    RuntimeException(message)

/**
 * A storage backend (connectivity, authentication, or unexpected server-side)
 * failure - distinct from "not found", which the ArtworkStore contract expresses
 * as null/false, never a throw. Message is a fixed, generic string; never include
 * endpoint URLs, bucket names, access keys, or raw SDK exception text. The
 * original exception is kept only as `cause`, for server-side logging.
 */
class ArtworkStorageException(
    message: String = "artwork storage backend is unavailable",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
