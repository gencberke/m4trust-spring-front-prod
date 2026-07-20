package com.m4trust.coreapi.fulfillment;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps fulfillment domain exceptions to stable RFC 9457 Problem Details. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class FulfillmentExceptionHandler {

    @ExceptionHandler(FulfillmentExceptions.MalformedRequest.class)
    ResponseEntity<ProblemDetail> handleMalformedRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST,
                "https://problems.m4trust.internal/malformed-request",
                "Malformed request", "MALFORMED_REQUEST",
                "The request body could not be parsed.");
    }

    @ExceptionHandler(FulfillmentExceptions.DealNotFound.class)
    ResponseEntity<ProblemDetail> handleDealNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND,
                "https://problems.m4trust.internal/deal-or-legal-entity-not-found-or-hidden",
                "Deal or legal entity not found or hidden",
                "DEAL_OR_LEGAL_ENTITY_NOT_FOUND_OR_HIDDEN",
                "The requested Deal or legal entity is not available.");
    }

    @ExceptionHandler(FulfillmentExceptions.NotFound.class)
    ResponseEntity<ProblemDetail> handleNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND,
                "https://problems.m4trust.internal/fulfillment-or-evidence-not-found-or-hidden",
                "Fulfillment or evidence not found or hidden",
                "FULFILLMENT_OR_EVIDENCE_NOT_FOUND_OR_HIDDEN",
                "The requested fulfillment or evidence is not available.");
    }

    @ExceptionHandler(FulfillmentExceptions.StartForbidden.class)
    ResponseEntity<ProblemDetail> handleStartForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "https://problems.m4trust.internal/fulfillment-start-forbidden",
                "Fulfillment start forbidden",
                "FULFILLMENT_START_FORBIDDEN",
                "The active legal entity is not authorized to start fulfillment.");
    }

    @ExceptionHandler(FulfillmentExceptions.UploadForbidden.class)
    ResponseEntity<ProblemDetail> handleUploadForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "https://problems.m4trust.internal/evidence-upload-forbidden",
                "Evidence upload forbidden",
                "EVIDENCE_UPLOAD_FORBIDDEN",
                "The active legal entity is not authorized to upload evidence.");
    }

    @ExceptionHandler(FulfillmentExceptions.ReviewForbidden.class)
    ResponseEntity<ProblemDetail> handleReviewForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "https://problems.m4trust.internal/evidence-review-forbidden",
                "Evidence review forbidden",
                "EVIDENCE_REVIEW_FORBIDDEN",
                "The active legal entity is not authorized to review evidence.");
    }

    @ExceptionHandler(FulfillmentExceptions.StartConflict.class)
    ResponseEntity<ProblemDetail> handleStartConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "https://problems.m4trust.internal/fulfillment-start-conflict",
                "Fulfillment start conflict",
                "FULFILLMENT_START_CONFLICT",
                "The Deal is not in a state that allows fulfillment to be started.");
    }

    @ExceptionHandler(FulfillmentExceptions.UploadConflict.class)
    ResponseEntity<ProblemDetail> handleUploadConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "https://problems.m4trust.internal/evidence-upload-conflict",
                "Evidence upload conflict",
                "EVIDENCE_UPLOAD_CONFLICT",
                "The milestone is not accepting new evidence.");
    }

    @ExceptionHandler(FulfillmentExceptions.FinalizeConflict.class)
    ResponseEntity<ProblemDetail> handleFinalizeConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "https://problems.m4trust.internal/evidence-finalize-conflict",
                "Evidence finalize conflict",
                "EVIDENCE_FINALIZE_CONFLICT",
                "The evidence submission cannot be finalized in its current state.");
    }

    @ExceptionHandler(FulfillmentExceptions.ReviewConflict.class)
    ResponseEntity<ProblemDetail> handleReviewConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "https://problems.m4trust.internal/evidence-review-conflict",
                "Evidence review conflict",
                "EVIDENCE_REVIEW_CONFLICT",
                "The evidence submission cannot be reviewed in its current state.");
    }

    @ExceptionHandler(FulfillmentExceptions.DownloadNotAvailable.class)
    ResponseEntity<ProblemDetail> handleDownloadNotAvailable(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "https://problems.m4trust.internal/evidence-download-not-available",
                "Evidence download not available",
                "EVIDENCE_DOWNLOAD_NOT_AVAILABLE",
                "The evidence is not available for download.");
    }

    @ExceptionHandler(FulfillmentExceptions.Validation.class)
    ResponseEntity<ProblemDetail> handleValidation(FulfillmentExceptions.Validation exception,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "One or more fields are invalid.");
        problem.setType(URI.create("https://problems.m4trust.internal/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("correlationId", correlationId(request));
        problem.setProperty("errors", List.of(new FieldValidationError(exception.field(), "INVALID", "Field is invalid.")));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<ProblemDetail> response(HttpServletRequest request, HttpStatus status,
            String type, String title, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String correlationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return attr != null ? attr.toString() : "";
    }

    private record FieldValidationError(String field, String code, String message) {
    }
}
