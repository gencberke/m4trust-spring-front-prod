package com.m4trust.coreapi.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Minimal RFC 9457 Problem Details error handling, per ADR-006 sections 13,
 * 16, 17 and 18. Intentionally covers only the two error cases exercised by
 * this skeleton (field validation and malformed JSON); it is not a general
 * error framework.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldValidationError)
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "One or more fields are invalid.");
        problem.setType(URI.create("https://problems.m4trust.internal/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create(requestPath(request)));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("errors", errors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedRequest(
            HttpMessageNotReadableException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The request body could not be parsed.");
        problem.setType(URI.create("https://problems.m4trust.internal/malformed-request"));
        problem.setTitle("Malformed request");
        problem.setInstance(URI.create(requestPath(request)));
        problem.setProperty("code", "MALFORMED_REQUEST");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
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
}
