package com.m4trust.coreapi.deal;

import java.net.URI;

import com.m4trust.coreapi.api.CorrelationIdFilter;
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
class DealExceptionHandler {

    @ExceptionHandler(MalformedDealRequestException.class)
    ResponseEntity<ProblemDetail> handleMalformed(
            HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST,
                "malformed-request", "Malformed request",
                "The request could not be parsed.", "MALFORMED_REQUEST");
    }

    @ExceptionHandler(DealValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            DealValidationException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(request,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation-failed", "Validation failed",
                "One or more fields are invalid.", "VALIDATION_FAILED");
        problem.setProperty("errors", exception.errors());
        return entity(HttpStatus.UNPROCESSABLE_ENTITY, problem);
    }

    @ExceptionHandler(DealNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND,
                "deal-not-found", "Deal not found",
                "The Deal was not found.", "DEAL_NOT_FOUND");
    }

    @ExceptionHandler(DealMutationForbiddenException.class)
    ResponseEntity<ProblemDetail> handleMutationForbidden(
            HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "deal-mutation-forbidden", "Deal mutation forbidden",
                "The active legal entity cannot mutate this Deal.",
                "DEAL_MUTATION_FORBIDDEN");
    }

    @ExceptionHandler(DealStaleVersionException.class)
    ResponseEntity<ProblemDetail> handleStale(
            HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "stale-resource-version", "Resource has changed",
                "The resource was modified by another operation.",
                "DEAL_STALE_VERSION");
    }

    @ExceptionHandler(DealStateConflictException.class)
    ResponseEntity<ProblemDetail> handleStateConflict(
            HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "deal-state-conflict", "Request could not be completed",
                "The requested operation conflicts with the current resource state.",
                "DEAL_STATE_CONFLICT");
    }

    @ExceptionHandler(DealInvitationNotFoundException.class)
    ResponseEntity<ProblemDetail> handleInvitationNotFound(
            HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND,
                "deal-invitation-not-found", "Deal invitation not found",
                "The Deal invitation was not found.",
                "DEAL_INVITATION_NOT_FOUND");
    }

    @ExceptionHandler(DealInvitationForbiddenException.class)
    ResponseEntity<ProblemDetail> handleInvitationForbidden(
            HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN,
                "deal-invitation-forbidden", "Deal invitation forbidden",
                "The active legal entity cannot perform this invitation operation.",
                "DEAL_INVITATION_FORBIDDEN");
    }

    @ExceptionHandler(DealInvitationPendingExistsException.class)
    ResponseEntity<ProblemDetail> handleInvitationPendingExists(
            HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "deal-invitation-pending-exists",
                "Pending Deal invitation exists",
                "A pending invitation already exists for this recipient.",
                "DEAL_INVITATION_PENDING_EXISTS");
    }

    @ExceptionHandler(DealInvitationStaleVersionException.class)
    ResponseEntity<ProblemDetail> handleInvitationStale(
            HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "deal-invitation-stale-version", "Resource has changed",
                "The Deal invitation was modified by another operation.",
                "DEAL_INVITATION_STALE_VERSION");
    }

    @ExceptionHandler(DealInvitationStateConflictException.class)
    ResponseEntity<ProblemDetail> handleInvitationStateConflict(
            HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "deal-invitation-state-conflict",
                "Deal invitation state conflict",
                "The invitation state does not permit this operation.",
                "DEAL_INVITATION_STATE_CONFLICT");
    }

    @ExceptionHandler(DealInvitationAcceptedByOtherEntityException.class)
    ResponseEntity<ProblemDetail> handleInvitationAcceptedByOtherEntity(
            HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT,
                "deal-invitation-accepted-by-other-entity",
                "Deal invitation already accepted",
                "The invitation was accepted with a different legal entity.",
                "DEAL_INVITATION_ACCEPTED_BY_OTHER_ENTITY");
    }

    private ResponseEntity<ProblemDetail> response(
            HttpServletRequest request, HttpStatus status, String typeSlug,
            String title, String detail, String code) {
        return entity(status, problem(
                request, status, typeSlug, title, detail, code));
    }

    private ProblemDetail problem(
            HttpServletRequest request, HttpStatus status, String typeSlug,
            String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, detail);
        problem.setType(URI.create(
                "https://problems.m4trust.internal/" + typeSlug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        Object correlationId = request.getAttribute(
                CorrelationIdFilter.ATTRIBUTE);
        problem.setProperty("correlationId",
                correlationId == null ? "" : correlationId.toString());
        return problem;
    }

    private ResponseEntity<ProblemDetail> entity(
            HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
