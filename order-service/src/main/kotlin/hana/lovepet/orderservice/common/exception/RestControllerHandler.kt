package hana.lovepet.orderservice.common.exception

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RestControllerHandler {
    private  val log = LoggerFactory.getLogger(RestControllerHandler::class.java)

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(e: Exception): ResponseEntity<ErrorResponse> {
        log.error(e.toString())
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.message ?: "Entity not found"))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(e: Exception): ResponseEntity<ErrorResponse> {
        log.error(e.toString())
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(e.message ?: "Entity not found"))
    }

}
