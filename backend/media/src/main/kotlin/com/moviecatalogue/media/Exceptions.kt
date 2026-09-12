package com.moviecatalogue.media

/** Raised when an upload exceeds the configured size limit. -> PAYLOAD_TOO_LARGE */
class PayloadTooLargeException(message: String = "artwork exceeds the size limit") :
    RuntimeException(message)

/** Raised when content is not a valid, allowed image. -> UNSUPPORTED_MEDIA_TYPE */
class UnsupportedMediaTypeException(message: String = "artwork type is not allowed") :
    RuntimeException(message)
