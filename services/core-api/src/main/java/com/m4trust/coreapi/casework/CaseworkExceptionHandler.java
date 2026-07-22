package com.m4trust.coreapi.casework;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import com.m4trust.coreapi.api.ApiErrorCode;
import com.m4trust.coreapi.api.CorrelationIdFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps casework domain exceptions to stable RFC 9457 Problem Details. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CaseworkExceptionHandler {

    @ExceptionHandler(CaseworkExceptions.MalformedRequest.class)
    ResponseEntity<ProblemDetail> handleMalformedRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST,
                "https://problems.m4trust.internal/malformed-request",
                "Malformed request", ApiErrorCode.MALFORMED_REQUEST,
                "The request body could not be parsed.");
    }

    @ExceptionHandler(CaseworkExceptions.NotFound.class)
    ResponseEntity<ProblemDetail> handleCaseworkNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND,
                "https://problems.m4trust.internal/casework-not-found-or-hidden",
                "Casework not found or hidden",
                ApiErrorCode.CASEWORK_NOT_FOUND_OR_HIDDEN,
                "The requested Deal or casework collection is not available.");
    }

    @ExceptionHandler(CaseworkExceptions.DisputeNotFound.class)
    ResponseEntity<ProblemDetail> handleDisputeNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND,
                "https://problems.m4trust.internal/dispute-not-found-or-hidden",
                "Dispute not found or hidden",
                ApiErrorCode.DISPUTE_NOT_FOUND_OR_HIDDEN,
                "The requested dispute is not available.");
    }

    @ExceptionHandler(CaseworkExceptions.OpenForbidden.class)
    ResponseEntity<ProblemDetail> handleOpenForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "https://problems.m4trust.internal/dispute-open-forbidden",
                "Dispute open forbidden",
                ApiErrorCode.DISPUTE_OPEN_FORBIDDEN,
                "The active legal entity is not authorized to open a dispute.");
    }

    @ExceptionHandler(CaseworkExceptions.CommentForbidden.class)
    ResponseEntity<ProblemDetail> handleCommentForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "https://problems.m4trust.internal/dispute-comment-forbidden",
                "Dispute comment forbidden",
                ApiErrorCode.DISPUTE_COMMENT_FORBIDDEN,
                "The active legal entity is not authorized to comment on this dispute.");
    }

    @ExceptionHandler(CaseworkExceptions.AcknowledgeForbidden.class)
    ResponseEntity<ProblemDetail> handleAcknowledgeForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "https://problems.m4trust.internal/dispute-acknowledge-forbidden",
                "Dispute acknowledge forbidden",
                ApiErrorCode.DISPUTE_ACKNOWLEDGE_FORBIDDEN,
                "The active legal entity is not authorized to acknowledge this dispute.");
    }

    @ExceptionHandler(CaseworkExceptions.WithdrawForbidden.class)
    ResponseEntity<ProblemDetail> handleWithdrawForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "https://problems.m4trust.internal/dispute-withdraw-forbidden",
                "Dispute withdraw forbidden",
                ApiErrorCode.DISPUTE_WITHDRAW_FORBIDDEN,
                "The active legal entity is not authorized to withdraw this dispute.");
    }

    @ExceptionHandler(CaseworkExceptions.Conflict.class)
    ResponseEntity<ProblemDetail> handleConflict(CaseworkExceptions.Conflict exception,
            HttpServletRequest request) {
        ApiErrorCode code = exception.code();
        String slug = code.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return response(request, HttpStatus.CONFLICT,
                "https://problems.m4trust.internal/" + slug,
                "Dispute operation conflict",
                code,
                "The dispute operation conflicts with the current resource state.");
    }

    @ExceptionHandler(CaseworkExceptions.Validation.class)
    ResponseEntity<ProblemDetail> handleValidation(CaseworkExceptions.Validation exception,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "One or more fields are invalid.");
        problem.setType(URI.create("https://problems.m4trust.internal/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ApiErrorCode.VALIDATION_FAILED.name());
        problem.setProperty("correlationId", correlationId(request));
        problem.setProperty("errors", exception.errors());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<ProblemDetail> response(HttpServletRequest request, HttpStatus status,
            String type, String title, ApiErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", correlationId(request));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String correlationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return attr != null ? attr.toString() : "";
    }
}
