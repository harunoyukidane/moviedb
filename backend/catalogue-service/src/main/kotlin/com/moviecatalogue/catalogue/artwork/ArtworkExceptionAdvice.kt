package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.domain.ValidationException
import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.PayloadTooLargeException
import com.moviecatalogue.media.UnsupportedMediaTypeException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * Maps media/REST errors to stable HTTP statuses + `code` bodies (§8.2 analog for
 * the media endpoints). No stack traces or PII are exposed.
 */
@RestControllerAdvice(basePackageClasses = [MovieArtworkController::class])
class ArtworkExceptionAdvice {

    private fun body(code: String, message: String) = mapOf("code" to code, "message" to message)

    @ExceptionHandler(PayloadTooLargeException::class, MaxUploadSizeExceededException::class)
    fun tooLarge(e: Exception) =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(body("PAYLOAD_TOO_LARGE", "artwork exceeds the 5 MiB limit"))

    @ExceptionHandler(UnsupportedMediaTypeException::class)
    fun unsupported(e: UnsupportedMediaTypeException) =
        ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(body("UNSUPPORTED_MEDIA_TYPE", e.message ?: "unsupported media type"))

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(body("NOT_FOUND", e.message ?: "not found"))

    @ExceptionHandler(ValidationException::class)
    fun badInput(e: ValidationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(body("BAD_USER_INPUT", e.message ?: "invalid input"))

    @ExceptionHandler(ArtworkStorageException::class)
    fun storageUnavailable(e: ArtworkStorageException) =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(body("STORAGE_UNAVAILABLE", e.message ?: "artwork storage backend is unavailable"))
}
