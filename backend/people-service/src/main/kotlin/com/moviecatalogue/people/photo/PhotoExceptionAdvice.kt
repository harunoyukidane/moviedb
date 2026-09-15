package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStorageException
import com.moviecatalogue.media.PayloadTooLargeException
import com.moviecatalogue.media.UnsupportedMediaTypeException
import com.moviecatalogue.people.domain.PersonNotFoundException
import com.moviecatalogue.people.domain.ValidationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/** Maps person-photo media errors to stable HTTP statuses + `code` bodies. */
@RestControllerAdvice(basePackageClasses = [PersonPhotoController::class])
class PhotoExceptionAdvice {

    private fun body(code: String, message: String) = mapOf("code" to code, "message" to message)

    @ExceptionHandler(PayloadTooLargeException::class, MaxUploadSizeExceededException::class)
    fun tooLarge(e: Exception) =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(body("PAYLOAD_TOO_LARGE", "photo exceeds the 5 MiB limit"))

    @ExceptionHandler(UnsupportedMediaTypeException::class)
    fun unsupported(e: UnsupportedMediaTypeException) =
        ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(body("UNSUPPORTED_MEDIA_TYPE", e.message ?: "unsupported media type"))

    @ExceptionHandler(PersonNotFoundException::class)
    fun notFound(e: PersonNotFoundException) =
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
