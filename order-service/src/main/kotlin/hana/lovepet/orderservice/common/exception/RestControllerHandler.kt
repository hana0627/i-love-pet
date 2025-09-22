package hana.lovepet.orderservice.common.exception

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RestControllerHandler {
    private val log = LoggerFactory.getLogger(RestControllerHandler::class.java)

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(e: ApplicationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(e.errorCode.status).body(ErrorResponse(e.getMessage))
    }
}


