package com.m4trust.coreapi.fulfillment;

import java.net.URI;
import java.util.UUID;

import com.m4trust.coreapi.api.CorrelationIdFilter;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import com.m4trust.coreapi.organization.ResolvedOperationContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class FulfillmentController {

    private final FulfillmentService service;
    private final VideoAnalysisService videoAnalysisService;

    FulfillmentController(FulfillmentService service, VideoAnalysisService videoAnalysisService) {
        this.service = service;
        this.videoAnalysisService = videoAnalysisService;
    }

    @PostMapping("/deals/{dealId}/fulfillment")
    ResponseEntity<FulfillmentDetail> startFulfillment(
            @ResolvedOperationContext(RequestedOperation.FULFILLMENT_START)
            OperationContext context,
            @PathVariable String dealId,
            @Valid @RequestBody StartFulfillmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        FulfillmentDetail result = service.startFulfillment(context, uuid(dealId), request,
                uuid(idempotencyKey), uuid(correlationId));
        return ResponseEntity.created(URI.create("/api/v1/deals/" + dealId + "/fulfillment")).body(result);
    }

    @GetMapping("/deals/{dealId}/fulfillment")
    FulfillmentDetail getFulfillment(
            @ResolvedOperationContext(RequestedOperation.FULFILLMENT_READ)
            OperationContext context,
            @PathVariable String dealId) {
        return service.getFulfillment(context, uuid(dealId));
    }

    @PostMapping("/deals/{dealId}/fulfillment/evidence/upload-intents")
    ResponseEntity<EvidenceUploadIntent> createEvidenceUploadIntent(
            @ResolvedOperationContext(RequestedOperation.EVIDENCE_UPLOAD_INTENT_CREATE)
            OperationContext context,
            @PathVariable String dealId,
            @Valid @RequestBody CreateEvidenceUploadIntentRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        EvidenceUploadIntent result = service.createEvidenceUploadIntent(context, uuid(dealId),
                request, uuid(correlationId));
        return ResponseEntity.created(URI.create("/api/v1/deals/" + dealId
                + "/fulfillment/evidence/" + result.evidence().id())).body(result);
    }

    @PostMapping("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize")
    EvidenceSubmissionProjection finalizeEvidenceUpload(
            @ResolvedOperationContext(RequestedOperation.EVIDENCE_UPLOAD_FINALIZE)
            OperationContext context,
            @PathVariable String dealId,
            @PathVariable String evidenceSubmissionId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody FinalizeEvidenceUploadRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        return service.finalizeEvidenceUpload(context, uuid(dealId), uuid(evidenceSubmissionId),
                request, uuid(idempotencyKey), uuid(correlationId));
    }

    @PostMapping("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload")
    EvidenceSubmissionProjection cancelEvidenceUpload(
            @ResolvedOperationContext(RequestedOperation.EVIDENCE_UPLOAD_CANCEL)
            OperationContext context,
            @PathVariable String dealId,
            @PathVariable String evidenceSubmissionId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody CancelEvidenceUploadRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        return service.cancelEvidenceUpload(context, uuid(dealId), uuid(evidenceSubmissionId),
                request, uuid(idempotencyKey), uuid(correlationId));
    }

    @PostMapping("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/download-link")
    EvidenceDownloadLink createEvidenceDownloadLink(
            @ResolvedOperationContext(RequestedOperation.EVIDENCE_DOWNLOAD_LINK_CREATE)
            OperationContext context,
            @PathVariable String dealId,
            @PathVariable String evidenceSubmissionId) {
        return service.createDownloadLink(context, uuid(dealId), uuid(evidenceSubmissionId));
    }

    @PostMapping("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept")
    EvidenceSubmissionProjection acceptEvidence(
            @ResolvedOperationContext(RequestedOperation.EVIDENCE_ACCEPT)
            OperationContext context,
            @PathVariable String dealId,
            @PathVariable String evidenceSubmissionId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody AcceptEvidenceRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        return service.acceptEvidence(context, uuid(dealId), uuid(evidenceSubmissionId),
                request, uuid(idempotencyKey), uuid(correlationId));
    }

    @PostMapping("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject")
    EvidenceSubmissionProjection rejectEvidence(
            @ResolvedOperationContext(RequestedOperation.EVIDENCE_REJECT)
            OperationContext context,
            @PathVariable String dealId,
            @PathVariable String evidenceSubmissionId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody RejectEvidenceRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        return service.rejectEvidence(context, uuid(dealId), uuid(evidenceSubmissionId),
                request, uuid(idempotencyKey), uuid(correlationId));
    }

    @PostMapping("/deals/{dealId}/fulfillment/accept-without-evidence")
    FulfillmentDetail acceptWithoutEvidence(
            @ResolvedOperationContext(RequestedOperation.FULFILLMENT_ACCEPT_WITHOUT_EVIDENCE)
            OperationContext context,
            @PathVariable String dealId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody AcceptWithoutEvidenceRequest request,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        return service.acceptWithoutEvidence(context, uuid(dealId), request,
                uuid(idempotencyKey), uuid(correlationId));
    }

    @GetMapping("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis")
    VideoAnalysisDetail getVideoAnalysis(
            @ResolvedOperationContext(RequestedOperation.VIDEO_ANALYSIS_READ)
            OperationContext context,
            @PathVariable String dealId,
            @PathVariable String evidenceSubmissionId) {
        return videoAnalysisService.get(context, uuid(dealId), uuid(evidenceSubmissionId));
    }

    @PostMapping("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis")
    ResponseEntity<VideoAnalysisDetail> requestVideoAnalysis(
            @ResolvedOperationContext(RequestedOperation.VIDEO_ANALYSIS_REQUEST)
            OperationContext context,
            @PathVariable String dealId,
            @PathVariable String evidenceSubmissionId,
            @Valid @RequestBody RequestVideoAnalysisRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId) {
        UUID parsedDealId = uuid(dealId);
        UUID parsedEvidenceId = uuid(evidenceSubmissionId);
        VideoAnalysisDetail accepted = videoAnalysisService.request(context, parsedDealId, parsedEvidenceId,
                request, uuid(idempotencyKey), uuid(correlationId));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/deals/" + parsedDealId
                        + "/fulfillment/evidence/" + parsedEvidenceId + "/video-analysis"))
                .body(accepted);
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            throw new FulfillmentExceptions.MalformedRequest();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new FulfillmentExceptions.MalformedRequest();
        }
    }
}
