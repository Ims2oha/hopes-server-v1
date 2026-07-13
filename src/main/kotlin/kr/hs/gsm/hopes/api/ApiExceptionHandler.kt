package kr.hs.gsm.hopes.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class ApiException(val status: HttpStatus, override val message: String) : RuntimeException(message)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handleApi(exception: ApiException) = ResponseEntity.status(exception.status)
        .body(mapOf("message" to exception.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = exception.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "잘못된 값입니다") }
        return ResponseEntity.badRequest().body(mapOf("message" to (errors.values.firstOrNull() ?: "입력값을 확인해주세요"), "errors" to errors))
    }
}
