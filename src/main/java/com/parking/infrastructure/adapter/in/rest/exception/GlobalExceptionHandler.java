package com.parking.infrastructure.adapter.in.rest.exception;

import com.parking.domain.exception.AccountDisabledException;
import com.parking.domain.exception.BusinessRuleException;
import com.parking.domain.exception.InvalidCredentialsException;
import com.parking.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates domain and framework exceptions into {@link ApiError} responses.
 * <p>
 * Messages are in Spanish because they are shown to end users by the frontend.
 * Security filter errors (missing token, forbidden role) are routed here too
 * by {@code SecurityErrorHandler}, so every error shares the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Domain ──────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ApiError.response(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return ApiError.response(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return ApiError.response(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiError> handleAccountDisabled(AccountDisabledException ex, HttpServletRequest request) {
        return ApiError.response(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // ── Security (filter chain and @PreAuthorize) ───────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(HttpServletRequest request) {
        return ApiError.response(HttpStatus.UNAUTHORIZED, "Se requiere autenticación para acceder a este recurso", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return ApiError.response(HttpStatus.FORBIDDEN, "No tiene permisos para esta operación", request);
    }

    // ── HTTP and persistence ────────────────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleUnknownEndpoint(HttpServletRequest request) {
        return ApiError.response(HttpStatus.NOT_FOUND, "El recurso solicitado no existe", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpServletRequest request) {
        return ApiError.response(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no soportado para este recurso", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConcurrentModification(HttpServletRequest request) {
        return ApiError.response(HttpStatus.CONFLICT,
                "El recurso fue modificado por otro usuario al mismo tiempo. Intente de nuevo", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpServletRequest request) {
        return ApiError.response(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(MethodArgumentTypeMismatchException ex,
                                                           HttpServletRequest request) {
        return ApiError.response(HttpStatus.BAD_REQUEST,
                "Valor inválido para el parámetro '%s'".formatted(ex.getName()), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST, "Error de validación en los datos enviados",
                request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }
}
