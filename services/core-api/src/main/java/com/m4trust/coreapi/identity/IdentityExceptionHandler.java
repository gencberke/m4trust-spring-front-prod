package com.m4trust.coreapi.identity;

import java.net.URI;
import java.util.List;

import com.m4trust.coreapi.api.ApiErrorCode;
import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.api.FieldErrorCode;
import com.m4trust.coreapi.api.FieldValidationError;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityExceptionHandler {

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ProblemDetail> handleWeakPassword(
            WeakPasswordException ex, WebRequest request,
            HttpServletRequest httpRequest) {
        ProblemDetail problem = baseProblem(HttpStatus.UNPROCESSABLE_ENTITY,
                "validation-failed", "Validation failed",
                "One or more fields are invalid.", ApiErrorCode.VALIDATION_FAILED,
                request, httpRequest);
        problem.setProperty("errors", List.of(new FieldValidationError(
                "password", FieldErrorCode.PASSWORD_TOO_COMMON,
                "Password is too common or easily guessed.")));
        return response(HttpStatus.UNPROCESSABLE_ENTITY, problem);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEmail(
            DuplicateEmailException ex, WebRequest request,
            HttpServletRequest httpRequest) {
        return response(HttpStatus.CONFLICT, baseProblem(HttpStatus.CONFLICT,
                "auth-email-already-exists", "Email already registered",
                "An account with this email already exists.",
                ApiErrorCode.AUTH_EMAIL_ALREADY_EXISTS, request, httpRequest));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException ex, WebRequest request,
            HttpServletRequest httpRequest) {
        return response(HttpStatus.UNAUTHORIZED, baseProblem(HttpStatus.UNAUTHORIZED,
                "auth-invalid-credentials", "Authentication failed",
                "Email or password is invalid.", ApiErrorCode.AUTH_INVALID_CREDENTIALS,
                request, httpRequest));
    }

    private ProblemDetail baseProblem(HttpStatus status, String typeSlug,
            String title, String detail, ApiErrorCode code, WebRequest request,
            HttpServletRequest httpRequest) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://problems.m4trust.internal/" + typeSlug));
        problem.setTitle(title);
        problem.setInstance(URI.create(requestPath(request)));
        problem.setProperty("code", code.name());
        Object correlationId = httpRequest.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        problem.setProperty("correlationId",
                correlationId == null ? "" : correlationId.toString());
        return problem;
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String requestPath(WebRequest request) {
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring(4) : description;
    }
}
