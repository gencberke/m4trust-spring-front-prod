package com.m4trust.coreapi.api;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

/**
 * Minimal RFC 9457 Problem Details error handling, per ADR-006 sections 13,
 * 16, 17, 18 and 38. Covers field validation, malformed JSON, and a safe
 * fallback for unexpected failures while leaving framework HTTP errors to
 * Spring's normal resolvers.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request,
            HttpServletRequest httpRequest) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldValidationError)
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "One or more fields are invalid.");
        problem.setType(URI.create("https://problems.m4trust.internal/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create(requestPath(request)));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("correlationId", correlationId(httpRequest));
        problem.setProperty("errors", errors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedRequest(
            HttpMessageNotReadableException ex, WebRequest request,
            HttpServletRequest httpRequest) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The request body could not be parsed.");
        problem.setType(URI.create("https://problems.m4trust.internal/malformed-request"));
        problem.setTitle("Malformed request");
        problem.setInstance(URI.create(requestPath(request)));
        problem.setProperty("code", "MALFORMED_REQUEST");
        problem.setProperty("correlationId", correlationId(httpRequest));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex, WebRequest request,
            HttpServletRequest httpRequest) throws Exception {
        if (ex instanceof ErrorResponse
                || AnnotatedElementUtils.findMergedAnnotation(
                        ex.getClass(), ResponseStatus.class) != null) {
            throw ex;
        }

        String path = requestPath(request);
        String correlationId = correlationId(httpRequest);
        LOGGER.error("Unexpected request failure [correlationId={}, path={}]",
                correlationId, path, ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed.");
        problem.setType(URI.create("https://problems.m4trust.internal/internal-error"));
        problem.setTitle("Unexpected error");
        problem.setInstance(URI.create(path));
        problem.setProperty("code", "INTERNAL_ERROR");
        problem.setProperty("correlationId", correlationId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private FieldValidationError toFieldValidationError(FieldError fieldError) {
        String code = fieldError.getCode() != null ? fieldError.getCode() : "INVALID";
        String message = fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "Field is invalid.";
        return new FieldValidationError(fieldError.getField(), code, message);
    }

    private String requestPath(WebRequest request) {
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring(4) : description;
    }

    private String correlationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return attr != null ? attr.toString() : "";
    }
}
