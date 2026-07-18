package com.m4trust.coreapi.document;

import java.net.URI;
import java.util.List;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.api.FieldValidationError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class DocumentExceptionHandler {

    @ExceptionHandler(DocumentExceptions.MalformedRequest.class)
    ResponseEntity<ProblemDetail> malformed(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "malformed-request",
                "Malformed request", "The request could not be parsed.",
                "MALFORMED_REQUEST");
    }

    @ExceptionHandler(DocumentExceptions.NotFound.class)
    ResponseEntity<ProblemDetail> notFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "deal-document-not-found",
                "Document not found", "The document was not found.",
                "DEAL_DOCUMENT_NOT_FOUND");
    }

    @ExceptionHandler(DocumentExceptions.DealNotFound.class)
    ResponseEntity<ProblemDetail> dealNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "deal-not-found",
                "Deal not found", "The Deal was not found.", "DEAL_NOT_FOUND");
    }

    @ExceptionHandler(DocumentExceptions.MutationForbidden.class)
    ResponseEntity<ProblemDetail> forbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "deal-document-mutation-forbidden",
                "Deal document mutation forbidden",
                "The active legal entity cannot mutate this Deal document.",
                "DEAL_DOCUMENT_MUTATION_FORBIDDEN");
    }

    @ExceptionHandler(DocumentExceptions.UploadNotAllowed.class)
    ResponseEntity<ProblemDetail> uploadNotAllowed(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "deal-document-upload-not-allowed",
                "Document upload is not allowed",
                "This Deal cannot accept a document upload.",
                "DEAL_DOCUMENT_UPLOAD_NOT_ALLOWED");
    }

    @ExceptionHandler(DocumentExceptions.UploadExpired.class)
    ResponseEntity<ProblemDetail> expired(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "document-upload-expired",
                "Document upload expired", "The document upload has expired.",
                "DOCUMENT_UPLOAD_EXPIRED");
    }

    @ExceptionHandler(DocumentExceptions.UploadStateConflict.class)
    ResponseEntity<ProblemDetail> stateConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "document-upload-state-conflict",
                "Document upload state conflict",
                "The document is not pending upload.",
                "DOCUMENT_UPLOAD_STATE_CONFLICT");
    }

    @ExceptionHandler(DocumentExceptions.VerificationFailed.class)
    ResponseEntity<ProblemDetail> verificationFailed(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "document-verification-failed",
                "Document verification failed",
                "Verified object metadata does not match the request.",
                "DOCUMENT_VERIFICATION_FAILED");
    }

    @ExceptionHandler(DocumentExceptions.Validation.class)
    ResponseEntity<ProblemDetail> validation(DocumentExceptions.Validation exception,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "One or more fields are invalid.");
        problem.setType(URI.create("https://problems.m4trust.internal/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("correlationId", correlationId == null ? "" : correlationId.toString());
        problem.setProperty("errors", List.of(new FieldValidationError(
                exception.field(), "OUT_OF_RANGE", "Value exceeds the configured limit.")));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private ResponseEntity<ProblemDetail> response(HttpServletRequest request,
            HttpStatus status, String typeSlug, String title, String detail,
            String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://problems.m4trust.internal/" + typeSlug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId == null ? "" : correlationId.toString());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
