package com.m4trust.coreapi.payment;

import java.net.URI;

import com.m4trust.coreapi.api.ApiErrorCode;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 19)
class SettlementExceptionHandler {

    @ExceptionHandler(SettlementMalformedRequestException.class)
    ResponseEntity<ProblemDetail> malformedRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                "The request could not be parsed.", ApiErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(SettlementExceptions.DealNotFound.class)
    ResponseEntity<ProblemDetail> dealNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "deal-not-found", "Deal not found",
                "The Deal was not found.", ApiErrorCode.DEAL_NOT_FOUND);
    }

    @ExceptionHandler(SettlementExceptions.SettlementNotFound.class)
    ResponseEntity<ProblemDetail> settlementNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "settlement-not-found", "Settlement not found",
                "The settlement projection was not found.", ApiErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @ExceptionHandler(SettlementExceptions.ReleaseOperationNotFound.class)
    ResponseEntity<ProblemDetail> releaseOperationNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "release-operation-not-found", "Release operation not found",
                "The release operation was not found.", ApiErrorCode.RELEASE_OPERATION_NOT_FOUND);
    }

    @ExceptionHandler(SettlementExceptions.MutationForbidden.class)
    ResponseEntity<ProblemDetail> mutationForbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "settlement-mutation-forbidden", "Settlement mutation forbidden",
                "Only an ADMIN of the Deal's buyer legal entity may perform this settlement action.",
                ApiErrorCode.SETTLEMENT_MUTATION_FORBIDDEN);
    }

    @ExceptionHandler(SettlementExceptions.DealStateConflict.class)
    ResponseEntity<ProblemDetail> dealStateConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "deal-state-conflict", "Request could not be completed",
                "The Deal is not ACTIVE.", ApiErrorCode.DEAL_STATE_CONFLICT);
    }

    @ExceptionHandler(SettlementExceptions.DealStaleVersion.class)
    ResponseEntity<ProblemDetail> dealStaleVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "deal-stale-version", "Resource has changed",
                "The Deal was modified by another operation.", ApiErrorCode.DEAL_STALE_VERSION);
    }

    @ExceptionHandler(SettlementExceptions.SettlementStaleVersion.class)
    ResponseEntity<ProblemDetail> settlementStaleVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "settlement-stale-version", "Resource has changed",
                "The settlement was modified by another operation.", ApiErrorCode.SETTLEMENT_STALE_VERSION);
    }

    @ExceptionHandler(SettlementExceptions.FulfillmentStaleVersion.class)
    ResponseEntity<ProblemDetail> fulfillmentStaleVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "fulfillment-stale-version", "Resource has changed",
                "The fulfillment was modified by another operation.", ApiErrorCode.FULFILLMENT_STALE_VERSION);
    }

    @ExceptionHandler(SettlementExceptions.FundingUnitStaleVersion.class)
    ResponseEntity<ProblemDetail> fundingUnitStaleVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "funding-unit-stale-version", "Resource has changed",
                "The funding unit was modified by another operation.", ApiErrorCode.FUNDING_UNIT_STALE_VERSION);
    }

    @ExceptionHandler(SettlementExceptions.ReleaseOperationStaleVersion.class)
    ResponseEntity<ProblemDetail> releaseOperationStaleVersion(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "release-operation-stale-version", "Resource has changed",
                "The release operation was modified by another operation.",
                ApiErrorCode.RELEASE_OPERATION_STALE_VERSION);
    }

    @ExceptionHandler(SettlementExceptions.ContractualWindowMissing.class)
    ResponseEntity<ProblemDetail> contractualWindowMissing(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "settlement-contractual-window-missing",
                "Contractual dispute window missing",
                "The current ratification package is not release-eligible.",
                ApiErrorCode.SETTLEMENT_CONTRACTUAL_WINDOW_MISSING);
    }

    @ExceptionHandler(SettlementExceptions.DisputeWindowNotElapsed.class)
    ResponseEntity<ProblemDetail> disputeWindowNotElapsed(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "settlement-dispute-window-not-elapsed",
                "Dispute window not elapsed",
                "The contractual dispute window has not elapsed yet.",
                ApiErrorCode.SETTLEMENT_DISPUTE_WINDOW_NOT_ELAPSED);
    }

    @ExceptionHandler(SettlementExceptions.ActiveDispute.class)
    ResponseEntity<ProblemDetail> activeDispute(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "settlement-active-dispute", "Active dispute",
                "An active dispute blocks settlement release.",
                ApiErrorCode.SETTLEMENT_ACTIVE_DISPUTE);
    }

    @ExceptionHandler(SettlementExceptions.AlreadyTerminal.class)
    ResponseEntity<ProblemDetail> alreadyTerminal(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "settlement-already-terminal", "Settlement already terminal",
                "The settlement has already reached a terminal state.",
                ApiErrorCode.SETTLEMENT_ALREADY_TERMINAL);
    }

    @ExceptionHandler(SettlementExceptions.OperationAlreadyExists.class)
    ResponseEntity<ProblemDetail> operationAlreadyExists(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "release-operation-already-exists",
                "Release operation already exists",
                "A lifetime release operation already exists for this settlement.",
                ApiErrorCode.RELEASE_OPERATION_ALREADY_EXISTS);
    }

    @ExceptionHandler(SettlementExceptions.OutcomeUnknown.class)
    ResponseEntity<ProblemDetail> outcomeUnknown(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "release-outcome-unknown", "Release outcome unknown",
                "The simulated release outcome remains unknown after query.",
                ApiErrorCode.RELEASE_OUTCOME_UNKNOWN);
    }

    @ExceptionHandler(SettlementExceptions.ReconciliationUnavailable.class)
    ResponseEntity<ProblemDetail> reconciliationUnavailable(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "release-reconciliation-unavailable",
                "Release reconciliation unavailable",
                "The release operation cannot be reconciled in its current state.",
                ApiErrorCode.RELEASE_RECONCILIATION_UNAVAILABLE);
    }

    private ResponseEntity<ProblemDetail> response(HttpServletRequest request,
            HttpStatus status, String typeSlug, String title, String detail, ApiErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://problems.m4trust.internal/" + typeSlug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        problem.setProperty("correlationId", correlationId == null ? "" : correlationId.toString());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
