#!/usr/bin/env python3
"""Lightweight validation for the M4Trust contract foundation."""

from __future__ import annotations

import copy
import hashlib
import json
import os
import re
import sys
import warnings
from pathlib import Path
from typing import Any

warnings.filterwarnings("ignore", message="jsonschema.RefResolver is deprecated", category=DeprecationWarning)

try:
    import yaml
    from jsonschema import Draft202012Validator, FormatChecker, RefResolver
except ImportError as exc:  # pragma: no cover - exercised by the CLI error path
    print("Missing dependency: install contracts/requirements.txt (jsonschema, PyYAML).", file=sys.stderr)
    raise SystemExit(2) from exc


ROOT = Path(__file__).resolve().parents[1]
SCHEMA_ROOT = ROOT / "schemas"
SCHEMA_ID_NAMESPACE = "https://schemas.m4trust.internal/"

FIXTURE_SCHEMAS = {
    "examples/document-extraction/minimum-request.json": "schemas/document-extraction/requested-event-1.0.0.schema.json",
    "examples/document-extraction/full-request.json": "schemas/document-extraction/requested-event-1.0.0.schema.json",
    "examples/document-extraction/success-result.json": "schemas/document-extraction/completed-event-1.0.0.schema.json",
    "examples/document-extraction/warning-result.json": "schemas/document-extraction/completed-event-1.0.0.schema.json",
    "examples/document-extraction/retryable-failure.json": "schemas/document-extraction/failed-event-1.0.0.schema.json",
    "examples/document-extraction/non-retryable-failure.json": "schemas/document-extraction/failed-event-1.0.0.schema.json",
    "examples/video-analysis/minimum-request.json": "schemas/video-analysis/requested-event-1.0.0.schema.json",
    "examples/video-analysis/full-request.json": "schemas/video-analysis/requested-event-1.0.0.schema.json",
    "examples/video-analysis/success-result.json": "schemas/video-analysis/completed-event-1.0.0.schema.json",
    "examples/video-analysis/warning-result.json": "schemas/video-analysis/completed-event-1.0.0.schema.json",
    "examples/video-analysis/retryable-failure.json": "schemas/video-analysis/failed-event-1.0.0.schema.json",
    "examples/video-analysis/non-retryable-failure.json": "schemas/video-analysis/failed-event-1.0.0.schema.json",
    "examples/job/cancel-request.json": "schemas/job/cancel-requested-event-1.0.0.schema.json",
}

CONCRETE_EVENT_SCHEMAS = [
    "schemas/document-extraction/requested-event-1.0.0.schema.json",
    "schemas/document-extraction/completed-event-1.0.0.schema.json",
    "schemas/document-extraction/failed-event-1.0.0.schema.json",
    "schemas/video-analysis/requested-event-1.0.0.schema.json",
    "schemas/video-analysis/completed-event-1.0.0.schema.json",
    "schemas/video-analysis/failed-event-1.0.0.schema.json",
    "schemas/job/cancel-requested-event-1.0.0.schema.json",
]

EXPECTED_CHANNELS = {
    "ai.document-extraction.requested.v1",
    "ai.video-analysis.requested.v1",
    "ai.job.cancel.requested.v1",
    "ai.document-extraction.completed.v1",
    "ai.document-extraction.failed.v1",
    "ai.video-analysis.completed.v1",
    "ai.video-analysis.failed.v1",
}
EXPECTED_AI_INTERNAL_OPENAPI_PATHS = {
    "/health/live",
    "/health/ready",
    "/internal/v1/capabilities",
    "/internal/v1/contracts",
}
EXPECTED_CORE_INTERNAL_OPENAPI_PATHS = {
    "/internal/v1/contracts",
}

# ADR-016 §2.5 inclusion set relative to contracts/
_BUNDLE_INCLUDE_SPECS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("asyncapi", ("*.json", "*.yaml", "*.yml")),
    ("openapi", ("*.json", "*.yaml", "*.yml")),
    ("schemas", ("*.json",)),
    ("examples", ("*.json",)),
)
EXPECTED_CORE_API_OPERATIONS = {
    ("/auth/register", "post"): {
        "operationId": "register",
        "responses": {"201", "400", "403", "409", "422"},
        "security": [{"CsrfToken": []}],
    },
    ("/auth/login", "post"): {
        "operationId": "login",
        "responses": {"200", "400", "401", "403", "422"},
        "security": [{"CsrfToken": []}],
    },
    ("/auth/logout", "post"): {
        "operationId": "logout",
        "responses": {"204", "401", "403"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/auth/me", "get"): {
        "operationId": "getCurrentUser",
        "responses": {"200", "401"},
        "security": [{"SessionCookie": []}],
    },
    ("/security/csrf", "get"): {
        "operationId": "getCsrfToken",
        "responses": {"200"},
        "security": [],
    },
    ("/legal-entities", "post"): {
        "operationId": "createLegalEntity",
        "responses": {"201", "400", "401", "403", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/legal-entities", "get"): {
        "operationId": "listLegalEntityMemberships",
        "responses": {"200", "401"},
        "security": [{"SessionCookie": []}],
    },
    ("/legal-entities/{legalEntityId}", "get"): {
        "operationId": "getLegalEntity",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/legal-entities/{legalEntityId}/members", "get"): {
        "operationId": "listLegalEntityMembers",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals", "post"): {
        "operationId": "createDeal",
        "responses": {"201", "400", "401", "403", "404", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals", "get"): {
        "operationId": "listDeals",
        "responses": {"200", "400", "401", "403", "404", "422"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}", "get"): {
        "operationId": "getDeal",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}", "patch"): {
        "operationId": "updateDeal",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/cancel", "post"): {
        "operationId": "cancelDeal",
        "responses": {"200", "400", "401", "403", "404", "409"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/parties", "patch"): {
        "operationId": "updateDealParties",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/documents/upload-intents", "post"): {
        "operationId": "createDealDocumentUploadIntent",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/documents", "get"): {
        "operationId": "listDealDocuments",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/document-analysis", "get"): {
        "operationId": "getDealDocumentAnalysis",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/document-analysis", "post"): {
        "operationId": "requestDealDocumentAnalysis",
        "responses": {"202", "400", "401", "403", "404", "409"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/extraction-review", "get"): {
        "operationId": "getDealExtractionReview",
        "responses": {"200", "400", "401", "403", "404", "409"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/extraction-review/accept", "post"): {
        "operationId": "acceptDealExtractionReview",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/rule-set-versions", "get"): {
        "operationId": "listDealRuleSetVersions",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/rule-set-versions/{ruleSetVersionId}", "get"): {
        "operationId": "getDealRuleSetVersion",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/ratification-packages", "post"): {
        "operationId": "createRatificationPackage",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/ratification-packages", "get"): {
        "operationId": "listRatificationPackages",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}", "get"): {
        "operationId": "getRatificationPackage",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post"): {
        "operationId": "approveRatificationPackage",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post"): {
        "operationId": "rejectRatificationPackage",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/funding-plan", "post"): {
        "operationId": "createFundingPlan",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/funding-plan", "get"): {
        "operationId": "getFundingPlan",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/funding-units/{fundingUnitId}/payment-operations", "post"): {
        "operationId": "initiatePaymentOperation",
        "responses": {"202", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/payment-operations/{paymentOperationId}", "get"): {
        "operationId": "getPaymentOperation",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/payment-operations/{paymentOperationId}/reconcile", "post"): {
        "operationId": "reconcilePaymentOperation",
        "responses": {"202", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/documents/{documentId}/finalize", "post"): {
        "operationId": "finalizeDocumentUpload",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/documents/{documentId}/download-link", "post"): {
        "operationId": "createDocumentDownloadLink",
        "responses": {"200", "400", "401", "403", "404", "409"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/invitations", "post"): {
        "operationId": "createDealInvitation",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/invitations", "get"): {
        "operationId": "listDealInvitations",
        "responses": {"200", "400", "401", "403", "404", "422"},
        "security": [{"SessionCookie": []}],
    },
    ("/deal-invitations/incoming", "get"): {
        "operationId": "listIncomingDealInvitations",
        "responses": {"200", "400", "401", "422"},
        "security": [{"SessionCookie": []}],
    },
    ("/deal-invitations/{invitationId}/accept", "post"): {
        "operationId": "acceptDealInvitation",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deal-invitations/{invitationId}/reject", "post"): {
        "operationId": "rejectDealInvitation",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deal-invitations/{invitationId}/revoke", "post"): {
        "operationId": "revokeDealInvitation",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment", "post"): {
        "operationId": "startFulfillment",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment", "get"): {
        "operationId": "getFulfillment",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/fulfillment/evidence/upload-intents", "post"): {
        "operationId": "createEvidenceUploadIntent",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize", "post"): {
        "operationId": "finalizeEvidenceUpload",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload", "post"): {
        "operationId": "cancelEvidenceUpload",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/download-link", "post"): {
        "operationId": "createEvidenceDownloadLink",
        "responses": {"200", "400", "401", "403", "404", "409"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept", "post"): {
        "operationId": "acceptEvidence",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject", "post"): {
        "operationId": "rejectEvidence",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment/accept-without-evidence", "post"): {
        "operationId": "acceptFulfillmentWithoutEvidence",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "get"): {
        "operationId": "getVideoAnalysis",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post"): {
        "operationId": "requestVideoAnalysis",
        "responses": {"202", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/disputes", "post"): {
        "operationId": "openDispute",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/disputes", "get"): {
        "operationId": "listDisputes",
        "responses": {"200", "400", "401", "403", "404", "422"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/disputes/{disputeId}", "get"): {
        "operationId": "getDispute",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/disputes/{disputeId}/comments", "get"): {
        "operationId": "listDisputeComments",
        "responses": {"200", "400", "401", "403", "404", "422"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post"): {
        "operationId": "createDisputeComment",
        "responses": {"201", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post"): {
        "operationId": "acknowledgeDispute",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post"): {
        "operationId": "withdrawDispute",
        "responses": {"200", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/deals/{dealId}/settlement", "get"): {
        "operationId": "getSettlement",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/deals/{dealId}/settlement/release", "post"): {
        "operationId": "requestSettlementRelease",
        "responses": {"202", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
    ("/release-operations/{operationId}", "get"): {
        "operationId": "getReleaseOperation",
        "responses": {"200", "400", "401", "403", "404"},
        "security": [{"SessionCookie": []}],
    },
    ("/release-operations/{operationId}/reconcile", "post"): {
        "operationId": "reconcileReleaseOperation",
        "responses": {"202", "400", "401", "403", "404", "409", "422"},
        "security": [{"SessionCookie": [], "CsrfToken": []}],
    },
}
EXPECTED_CORE_REQUEST_SCHEMAS = {
    ("/auth/register", "post"): "#/components/schemas/RegisterRequest",
    ("/auth/login", "post"): "#/components/schemas/LoginRequest",
    ("/legal-entities", "post"): "#/components/schemas/CreateLegalEntityRequest",
    ("/deals", "post"): "#/components/schemas/CreateDealRequest",
    ("/deals/{dealId}", "patch"): "#/components/schemas/UpdateDealRequest",
    ("/deals/{dealId}/parties", "patch"): "#/components/schemas/UpdateDealPartiesRequest",
    ("/deals/{dealId}/documents/upload-intents", "post"): "#/components/schemas/CreateDocumentUploadIntentRequest",
    ("/documents/{documentId}/finalize", "post"): "#/components/schemas/FinalizeDocumentUploadRequest",
    ("/deals/{dealId}/extraction-review/accept", "post"): "#/components/schemas/AcceptExtractionReviewRequest",
    ("/deals/{dealId}/invitations", "post"): "#/components/schemas/CreateDealInvitationRequest",
    ("/deal-invitations/{invitationId}/accept", "post"): "#/components/schemas/AcceptDealInvitationRequest",
    ("/deal-invitations/{invitationId}/reject", "post"): "#/components/schemas/DealInvitationTerminalActionRequest",
    ("/deal-invitations/{invitationId}/revoke", "post"): "#/components/schemas/DealInvitationTerminalActionRequest",
    ("/deals/{dealId}/ratification-packages", "post"): "#/components/schemas/CreateRatificationPackageRequest",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post"): "#/components/schemas/RatificationPackageActionRequest",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post"): "#/components/schemas/RatificationPackageActionRequest",
    ("/deals/{dealId}/funding-plan", "post"): "#/components/schemas/CreateFundingPlanRequest",
    ("/funding-units/{fundingUnitId}/payment-operations", "post"): "#/components/schemas/InitiatePaymentOperationRequest",
    ("/payment-operations/{paymentOperationId}/reconcile", "post"): "#/components/schemas/ReconcilePaymentOperationRequest",
    ("/deals/{dealId}/fulfillment", "post"): "#/components/schemas/StartFulfillmentRequest",
    ("/deals/{dealId}/fulfillment/evidence/upload-intents", "post"): "#/components/schemas/CreateEvidenceUploadIntentRequest",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize", "post"): "#/components/schemas/FinalizeEvidenceUploadRequest",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload", "post"): "#/components/schemas/CancelEvidenceUploadRequest",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept", "post"): "#/components/schemas/AcceptEvidenceRequest",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject", "post"): "#/components/schemas/RejectEvidenceRequest",
    ("/deals/{dealId}/fulfillment/accept-without-evidence", "post"): "#/components/schemas/AcceptWithoutEvidenceRequest",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post"): "#/components/schemas/RequestVideoAnalysisRequest",
    ("/deals/{dealId}/disputes", "post"): "#/components/schemas/OpenDisputeRequest",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post"): "#/components/schemas/CreateDisputeCommentRequest",
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post"): "#/components/schemas/AcknowledgeDisputeRequest",
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post"): "#/components/schemas/WithdrawDisputeRequest",
    ("/deals/{dealId}/settlement/release", "post"): "#/components/schemas/RequestSettlementReleaseRequest",
    ("/release-operations/{operationId}/reconcile", "post"): "#/components/schemas/ReconcileReleaseOperationRequest",
}
EXPECTED_CORE_SUCCESS_SCHEMAS = {
    ("/auth/register", "post", "201"): "#/components/schemas/PublicUser",
    ("/auth/login", "post", "200"): "#/components/schemas/PublicUser",
    ("/auth/me", "get", "200"): "#/components/schemas/CurrentUser",
    ("/security/csrf", "get", "200"): "#/components/schemas/CsrfToken",
    ("/legal-entities", "post", "201"): "#/components/schemas/LegalEntity",
    ("/legal-entities", "get", "200"): "#/components/schemas/LegalEntityMembershipList",
    ("/legal-entities/{legalEntityId}", "get", "200"): "#/components/schemas/LegalEntity",
    ("/legal-entities/{legalEntityId}/members", "get", "200"): "#/components/schemas/LegalEntityMemberList",
    ("/deals", "post", "201"): "#/components/schemas/DealDetail",
    ("/deals", "get", "200"): "#/components/schemas/DealPage",
    ("/deals/{dealId}", "get", "200"): "#/components/schemas/DealDetail",
    ("/deals/{dealId}", "patch", "200"): "#/components/schemas/DealDetail",
    ("/deals/{dealId}/cancel", "post", "200"): "#/components/schemas/DealDetail",
    ("/deals/{dealId}/parties", "patch", "200"): "#/components/schemas/DealDetail",
    ("/deals/{dealId}/documents/upload-intents", "post", "201"): "#/components/schemas/DocumentUploadIntent",
    ("/deals/{dealId}/documents", "get", "200"): "#/components/schemas/DealDocumentHistory",
    ("/deals/{dealId}/document-analysis", "get", "200"): "#/components/schemas/DealDocumentAnalysis",
    ("/deals/{dealId}/document-analysis", "post", "202"): "#/components/schemas/DealDocumentAnalysis",
    ("/deals/{dealId}/extraction-review", "get", "200"): "#/components/schemas/DealExtractionReview",
    ("/deals/{dealId}/extraction-review/accept", "post", "201"): "#/components/schemas/RuleSetVersion",
    ("/deals/{dealId}/rule-set-versions", "get", "200"): "#/components/schemas/RuleSetVersionHistory",
    ("/deals/{dealId}/rule-set-versions/{ruleSetVersionId}", "get", "200"): "#/components/schemas/RuleSetVersion",
    ("/documents/{documentId}/finalize", "post", "200"): "#/components/schemas/AvailableDealDocument",
    ("/documents/{documentId}/download-link", "post", "200"): "#/components/schemas/DocumentDownloadLink",
    ("/deals/{dealId}/invitations", "post", "201"): "#/components/schemas/DealInvitation",
    ("/deals/{dealId}/invitations", "get", "200"): "#/components/schemas/DealInvitationPage",
    ("/deal-invitations/incoming", "get", "200"): "#/components/schemas/IncomingDealInvitationPage",
    ("/deal-invitations/{invitationId}/accept", "post", "200"): "#/components/schemas/IncomingDealInvitation",
    ("/deal-invitations/{invitationId}/reject", "post", "200"): "#/components/schemas/IncomingDealInvitation",
    ("/deal-invitations/{invitationId}/revoke", "post", "200"): "#/components/schemas/DealInvitation",
    ("/deals/{dealId}/ratification-packages", "post", "201"): "#/components/schemas/RatificationPackageDetail",
    ("/deals/{dealId}/ratification-packages", "get", "200"): "#/components/schemas/RatificationPackageHistory",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}", "get", "200"): "#/components/schemas/RatificationPackageDetail",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post", "200"): "#/components/schemas/RatificationPackageDetail",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post", "200"): "#/components/schemas/RatificationPackageDetail",
    ("/deals/{dealId}/funding-plan", "post", "201"): "#/components/schemas/FundingPlanDetail",
    ("/deals/{dealId}/funding-plan", "get", "200"): "#/components/schemas/FundingPlanDetail",
    ("/funding-units/{fundingUnitId}/payment-operations", "post", "202"): "#/components/schemas/PaymentOperation",
    ("/payment-operations/{paymentOperationId}", "get", "200"): "#/components/schemas/PaymentOperation",
    ("/payment-operations/{paymentOperationId}/reconcile", "post", "202"): "#/components/schemas/PaymentOperation",
    ("/deals/{dealId}/fulfillment", "post", "201"): "#/components/schemas/FulfillmentDetail",
    ("/deals/{dealId}/fulfillment", "get", "200"): "#/components/schemas/FulfillmentDetail",
    ("/deals/{dealId}/fulfillment/evidence/upload-intents", "post", "201"): "#/components/schemas/EvidenceUploadIntent",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize", "post", "200"): "#/components/schemas/SubmittedEvidenceSubmission",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload", "post", "200"): "#/components/schemas/PendingEvidenceSubmission",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/download-link", "post", "200"): "#/components/schemas/EvidenceDownloadLink",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept", "post", "200"): "#/components/schemas/AcceptedEvidenceSubmission",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject", "post", "200"): "#/components/schemas/RejectedEvidenceSubmission",
    ("/deals/{dealId}/fulfillment/accept-without-evidence", "post", "200"): "#/components/schemas/FulfillmentDetail",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "get", "200"): "#/components/schemas/VideoAnalysisDetail",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post", "202"): "#/components/schemas/VideoAnalysisDetail",
    ("/deals/{dealId}/disputes", "post", "201"): "#/components/schemas/DisputeDetail",
    ("/deals/{dealId}/disputes", "get", "200"): "#/components/schemas/DisputePage",
    ("/deals/{dealId}/disputes/{disputeId}", "get", "200"): "#/components/schemas/DisputeDetail",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "get", "200"): "#/components/schemas/DisputeCommentPage",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post", "201"): "#/components/schemas/DisputeComment",
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post", "200"): "#/components/schemas/DisputeDetail",
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post", "200"): "#/components/schemas/DisputeDetail",
    ("/deals/{dealId}/settlement", "get", "200"): "#/components/schemas/SettlementDetail",
    ("/deals/{dealId}/settlement/release", "post", "202"): "#/components/schemas/ReleaseOperation",
    ("/release-operations/{operationId}", "get", "200"): "#/components/schemas/ReleaseOperation",
    ("/release-operations/{operationId}/reconcile", "post", "202"): "#/components/schemas/ReleaseOperation",
}
EXPECTED_CORE_ERROR_RESPONSES = {
    ("/auth/register", "post", "400"): "MalformedRequest",
    ("/auth/register", "post", "403"): "CsrfRejected",
    ("/auth/register", "post", "409"): "EmailAlreadyExists",
    ("/auth/register", "post", "422"): "ValidationFailed",
    ("/auth/login", "post", "400"): "MalformedRequest",
    ("/auth/login", "post", "401"): "InvalidCredentials",
    ("/auth/login", "post", "403"): "CsrfRejected",
    ("/auth/login", "post", "422"): "ValidationFailed",
    ("/auth/logout", "post", "401"): "SessionRequired",
    ("/auth/logout", "post", "403"): "CsrfRejected",
    ("/auth/me", "get", "401"): "SessionRequired",
    ("/legal-entities", "post", "400"): "MalformedRequest",
    ("/legal-entities", "post", "401"): "SessionRequired",
    ("/legal-entities", "post", "403"): "CsrfRejected",
    ("/legal-entities", "post", "422"): "ValidationFailed",
    ("/legal-entities", "get", "401"): "SessionRequired",
    ("/legal-entities/{legalEntityId}", "get", "400"): "MalformedRequest",
    ("/legal-entities/{legalEntityId}", "get", "401"): "SessionRequired",
    ("/legal-entities/{legalEntityId}", "get", "403"): "LegalEntityAccessDenied",
    ("/legal-entities/{legalEntityId}", "get", "404"): "LegalEntityNotFoundOrHidden",
    ("/legal-entities/{legalEntityId}/members", "get", "400"): "MalformedRequest",
    ("/legal-entities/{legalEntityId}/members", "get", "401"): "SessionRequired",
    ("/legal-entities/{legalEntityId}/members", "get", "403"): "LegalEntityAccessDenied",
    ("/legal-entities/{legalEntityId}/members", "get", "404"): "LegalEntityNotFoundOrHidden",
    ("/deals", "post", "400"): "MalformedRequest",
    ("/deals", "post", "401"): "SessionRequired",
    ("/deals", "post", "403"): "DealScopedMutationForbidden",
    ("/deals", "post", "404"): "LegalEntityNotFoundOrHidden",
    ("/deals", "post", "422"): "ValidationFailed",
    ("/deals", "get", "400"): "MalformedRequest",
    ("/deals", "get", "401"): "SessionRequired",
    ("/deals", "get", "403"): "LegalEntityAccessDenied",
    ("/deals", "get", "404"): "LegalEntityNotFoundOrHidden",
    ("/deals", "get", "422"): "ValidationFailed",
    ("/deals/{dealId}", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}", "get", "401"): "SessionRequired",
    ("/deals/{dealId}", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}", "get", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}", "patch", "400"): "MalformedRequest",
    ("/deals/{dealId}", "patch", "401"): "SessionRequired",
    ("/deals/{dealId}", "patch", "403"): "DealScopedMutationForbidden",
    ("/deals/{dealId}", "patch", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}", "patch", "409"): "DealUpdateConflict",
    ("/deals/{dealId}", "patch", "422"): "ValidationFailed",
    ("/deals/{dealId}/cancel", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/cancel", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/cancel", "post", "403"): "DealScopedMutationForbidden",
    ("/deals/{dealId}/cancel", "post", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/cancel", "post", "409"): "DealStateConflict",
    ("/deals/{dealId}/parties", "patch", "400"): "MalformedRequest",
    ("/deals/{dealId}/parties", "patch", "401"): "SessionRequired",
    ("/deals/{dealId}/parties", "patch", "403"): "DealScopedMutationForbidden",
    ("/deals/{dealId}/parties", "patch", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/parties", "patch", "409"): "DealPartiesConflict",
    ("/deals/{dealId}/parties", "patch", "422"): "ValidationFailed",
    ("/deals/{dealId}/documents/upload-intents", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/documents/upload-intents", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/documents/upload-intents", "post", "403"): "DealDocumentMutationForbidden",
    ("/deals/{dealId}/documents/upload-intents", "post", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/documents/upload-intents", "post", "409"): "DealDocumentIntentConflict",
    ("/deals/{dealId}/documents/upload-intents", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/documents", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/documents", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/documents", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/documents", "get", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/document-analysis", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/document-analysis", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/document-analysis", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/document-analysis", "get", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/document-analysis", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/document-analysis", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/document-analysis", "post", "403"): "DealAnalysisRequestForbidden",
    ("/deals/{dealId}/document-analysis", "post", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/document-analysis", "post", "409"): "DealDocumentAnalysisRequestConflict",
    ("/deals/{dealId}/extraction-review", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/extraction-review", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/extraction-review", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/extraction-review", "get", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/extraction-review", "get", "409"): "DealReviewConflict",
    ("/deals/{dealId}/extraction-review/accept", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/extraction-review/accept", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/extraction-review/accept", "post", "403"): "DealReviewAcceptanceForbidden",
    ("/deals/{dealId}/extraction-review/accept", "post", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/extraction-review/accept", "post", "409"): "DealReviewAcceptanceConflict",
    ("/deals/{dealId}/extraction-review/accept", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/rule-set-versions", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/rule-set-versions", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/rule-set-versions", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/rule-set-versions", "get", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/rule-set-versions/{ruleSetVersionId}", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/rule-set-versions/{ruleSetVersionId}", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/rule-set-versions/{ruleSetVersionId}", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/rule-set-versions/{ruleSetVersionId}", "get", "404"): "RuleSetVersionNotFoundOrHidden",
    ("/documents/{documentId}/finalize", "post", "400"): "MalformedRequest",
    ("/documents/{documentId}/finalize", "post", "401"): "SessionRequired",
    ("/documents/{documentId}/finalize", "post", "403"): "DealDocumentMutationForbidden",
    ("/documents/{documentId}/finalize", "post", "404"): "DealDocumentNotFoundOrHidden",
    ("/documents/{documentId}/finalize", "post", "409"): "DocumentFinalizeConflict",
    ("/documents/{documentId}/finalize", "post", "422"): "ValidationFailed",
    ("/documents/{documentId}/download-link", "post", "400"): "MalformedRequest",
    ("/documents/{documentId}/download-link", "post", "401"): "SessionRequired",
    ("/documents/{documentId}/download-link", "post", "403"): "LegalEntityAccessDenied",
    ("/documents/{documentId}/download-link", "post", "404"): "DealDocumentNotFoundOrHidden",
    ("/documents/{documentId}/download-link", "post", "409"): "DocumentDownloadConflict",
    ("/deals/{dealId}/invitations", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/invitations", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/invitations", "post", "403"): "DealInvitationMutationForbidden",
    ("/deals/{dealId}/invitations", "post", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/invitations", "post", "409"): "DealInvitationCreateConflict",
    ("/deals/{dealId}/invitations", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/invitations", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/invitations", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/invitations", "get", "403"): "DealInvitationListForbidden",
    ("/deals/{dealId}/invitations", "get", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/invitations", "get", "422"): "ValidationFailed",
    ("/deal-invitations/incoming", "get", "400"): "MalformedRequest",
    ("/deal-invitations/incoming", "get", "401"): "SessionRequired",
    ("/deal-invitations/incoming", "get", "422"): "ValidationFailed",
    ("/deal-invitations/{invitationId}/accept", "post", "400"): "MalformedRequest",
    ("/deal-invitations/{invitationId}/accept", "post", "401"): "SessionRequired",
    ("/deal-invitations/{invitationId}/accept", "post", "403"): "CsrfRejected",
    ("/deal-invitations/{invitationId}/accept", "post", "404"): "DealInvitationAcceptNotFoundOrHidden",
    ("/deal-invitations/{invitationId}/accept", "post", "409"): "DealInvitationAcceptConflict",
    ("/deal-invitations/{invitationId}/accept", "post", "422"): "ValidationFailed",
    ("/deal-invitations/{invitationId}/reject", "post", "400"): "MalformedRequest",
    ("/deal-invitations/{invitationId}/reject", "post", "401"): "SessionRequired",
    ("/deal-invitations/{invitationId}/reject", "post", "403"): "CsrfRejected",
    ("/deal-invitations/{invitationId}/reject", "post", "404"): "DealInvitationNotFoundOrHidden",
    ("/deal-invitations/{invitationId}/reject", "post", "409"): "DealInvitationTerminalConflict",
    ("/deal-invitations/{invitationId}/reject", "post", "422"): "ValidationFailed",
    ("/deal-invitations/{invitationId}/revoke", "post", "400"): "MalformedRequest",
    ("/deal-invitations/{invitationId}/revoke", "post", "401"): "SessionRequired",
    ("/deal-invitations/{invitationId}/revoke", "post", "403"): "DealInvitationMutationForbidden",
    ("/deal-invitations/{invitationId}/revoke", "post", "404"): "DealInvitationOrLegalEntityNotFoundOrHidden",
    ("/deal-invitations/{invitationId}/revoke", "post", "409"): "DealInvitationTerminalConflict",
    ("/deal-invitations/{invitationId}/revoke", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/ratification-packages", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/ratification-packages", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/ratification-packages", "post", "403"): "RatificationPackageCreateForbidden",
    ("/deals/{dealId}/ratification-packages", "post", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/ratification-packages", "post", "409"): "RatificationPackageCreateConflict",
    ("/deals/{dealId}/ratification-packages", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/ratification-packages", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/ratification-packages", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/ratification-packages", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/ratification-packages", "get", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}", "get", "404"): "RatificationPackageNotFoundOrHidden",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post", "403"): "RatificationPackageActionForbidden",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post", "404"): "RatificationPackageNotFoundOrHidden",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post", "409"): "RatificationPackageActionConflict",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post", "403"): "RatificationPackageActionForbidden",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post", "404"): "RatificationPackageNotFoundOrHidden",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post", "409"): "RatificationPackageActionConflict",
    ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/funding-plan", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/funding-plan", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/funding-plan", "post", "403"): "FundingMutationForbidden",
    ("/deals/{dealId}/funding-plan", "post", "404"): "DealOrLegalEntityNotFoundOrHidden",
    ("/deals/{dealId}/funding-plan", "post", "409"): "FundingPlanCreateConflict",
    ("/deals/{dealId}/funding-plan", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/funding-plan", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/funding-plan", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/funding-plan", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/funding-plan", "get", "404"): "FundingPlanNotFoundOrHidden",
    ("/funding-units/{fundingUnitId}/payment-operations", "post", "400"): "MalformedRequest",
    ("/funding-units/{fundingUnitId}/payment-operations", "post", "401"): "SessionRequired",
    ("/funding-units/{fundingUnitId}/payment-operations", "post", "403"): "FundingMutationForbidden",
    ("/funding-units/{fundingUnitId}/payment-operations", "post", "404"): "FundingUnitNotFoundOrHidden",
    ("/funding-units/{fundingUnitId}/payment-operations", "post", "409"): "PaymentOperationInitiateConflict",
    ("/funding-units/{fundingUnitId}/payment-operations", "post", "422"): "ValidationFailed",
    ("/payment-operations/{paymentOperationId}", "get", "400"): "MalformedRequest",
    ("/payment-operations/{paymentOperationId}", "get", "401"): "SessionRequired",
    ("/payment-operations/{paymentOperationId}", "get", "403"): "LegalEntityAccessDenied",
    ("/payment-operations/{paymentOperationId}", "get", "404"): "PaymentOperationNotFoundOrHidden",
    ("/payment-operations/{paymentOperationId}/reconcile", "post", "400"): "MalformedRequest",
    ("/payment-operations/{paymentOperationId}/reconcile", "post", "401"): "SessionRequired",
    ("/payment-operations/{paymentOperationId}/reconcile", "post", "403"): "FundingMutationForbidden",
    ("/payment-operations/{paymentOperationId}/reconcile", "post", "404"): "PaymentOperationNotFoundOrHidden",
    ("/payment-operations/{paymentOperationId}/reconcile", "post", "409"): "PaymentOperationReconcileConflict",
    ("/payment-operations/{paymentOperationId}/reconcile", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/fulfillment", "post", "403"): "FulfillmentStartForbidden",
    ("/deals/{dealId}/fulfillment", "post", "409"): "FulfillmentStartConflict",
    ("/deals/{dealId}/fulfillment", "get", "404"): "FulfillmentNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/evidence/upload-intents", "post", "403"): "EvidenceUploadForbidden",
    ("/deals/{dealId}/fulfillment/evidence/upload-intents", "post", "409"): "EvidenceUploadConflict",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize", "post", "403"): "EvidenceUploadForbidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize", "post", "404"): "EvidenceNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize", "post", "409"): "EvidenceFinalizeConflict",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload", "post", "403"): "EvidenceUploadForbidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload", "post", "404"): "EvidenceNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload", "post", "409"): "EvidenceCancelUploadConflict",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/download-link", "post", "404"): "EvidenceNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/download-link", "post", "409"): "EvidenceDownloadConflict",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept", "post", "403"): "EvidenceReviewForbidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept", "post", "404"): "EvidenceNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept", "post", "409"): "EvidenceReviewConflict",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject", "post", "403"): "EvidenceReviewForbidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject", "post", "404"): "EvidenceNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject", "post", "409"): "EvidenceReviewConflict",
    ("/deals/{dealId}/fulfillment/accept-without-evidence", "post", "403"): "EvidenceReviewForbidden",
    ("/deals/{dealId}/fulfillment/accept-without-evidence", "post", "404"): "FulfillmentNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/accept-without-evidence", "post", "409"): "AcceptWithoutEvidenceConflict",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "get", "404"): "EvidenceNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post", "403"): "VideoAnalysisRequestForbidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post", "404"): "EvidenceNotFoundOrHidden",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post", "409"): "VideoAnalysisRequestConflict",
    ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/disputes", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/disputes", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/disputes", "post", "403"): "DisputeOpenForbidden",
    ("/deals/{dealId}/disputes", "post", "404"): "CaseworkNotFoundOrHidden",
    ("/deals/{dealId}/disputes", "post", "409"): "DisputeOpenConflict",
    ("/deals/{dealId}/disputes", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/disputes", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/disputes", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/disputes", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/disputes", "get", "404"): "CaseworkNotFoundOrHidden",
    ("/deals/{dealId}/disputes", "get", "422"): "ValidationFailed",
    ("/deals/{dealId}/disputes/{disputeId}", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/disputes/{disputeId}", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/disputes/{disputeId}", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/disputes/{disputeId}", "get", "404"): "DisputeNotFoundOrHidden",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "get", "404"): "DisputeNotFoundOrHidden",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "get", "422"): "ValidationFailed",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post", "403"): "DisputeCommentForbidden",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post", "404"): "DisputeNotFoundOrHidden",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post", "409"): "DisputeMutationConflict",
    ("/deals/{dealId}/disputes/{disputeId}/comments", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post", "403"): "DisputeAcknowledgeForbidden",
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post", "404"): "DisputeNotFoundOrHidden",
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post", "409"): "DisputeMutationConflict",
    ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post", "403"): "DisputeWithdrawForbidden",
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post", "404"): "DisputeNotFoundOrHidden",
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post", "409"): "DisputeMutationConflict",
    ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post", "422"): "ValidationFailed",
    ("/deals/{dealId}/settlement", "get", "400"): "MalformedRequest",
    ("/deals/{dealId}/settlement", "get", "401"): "SessionRequired",
    ("/deals/{dealId}/settlement", "get", "403"): "LegalEntityAccessDenied",
    ("/deals/{dealId}/settlement", "get", "404"): "SettlementNotFoundOrHidden",
    ("/deals/{dealId}/settlement/release", "post", "400"): "MalformedRequest",
    ("/deals/{dealId}/settlement/release", "post", "401"): "SessionRequired",
    ("/deals/{dealId}/settlement/release", "post", "403"): "SettlementMutationForbidden",
    ("/deals/{dealId}/settlement/release", "post", "404"): "SettlementNotFoundOrHidden",
    ("/deals/{dealId}/settlement/release", "post", "409"): "SettlementReleaseConflict",
    ("/deals/{dealId}/settlement/release", "post", "422"): "ValidationFailed",
    ("/release-operations/{operationId}", "get", "400"): "MalformedRequest",
    ("/release-operations/{operationId}", "get", "401"): "SessionRequired",
    ("/release-operations/{operationId}", "get", "403"): "LegalEntityAccessDenied",
    ("/release-operations/{operationId}", "get", "404"): "ReleaseOperationNotFoundOrHidden",
    ("/release-operations/{operationId}/reconcile", "post", "400"): "MalformedRequest",
    ("/release-operations/{operationId}/reconcile", "post", "401"): "SessionRequired",
    ("/release-operations/{operationId}/reconcile", "post", "403"): "SettlementMutationForbidden",
    ("/release-operations/{operationId}/reconcile", "post", "404"): "ReleaseOperationNotFoundOrHidden",
    ("/release-operations/{operationId}/reconcile", "post", "409"): "ReleaseOperationReconcileConflict",
    ("/release-operations/{operationId}/reconcile", "post", "422"): "ValidationFailed",
}
REMOVED_COMBINED_API_ERROR_CODES = {
    "DEAL_OR_LEGAL_ENTITY_NOT_FOUND_OR_HIDDEN",
    "FULFILLMENT_OR_EVIDENCE_NOT_FOUND_OR_HIDDEN",
}
REQUIRED_CORE_API_SCHEMAS = {
    "RegisterRequest", "LoginRequest", "PublicUser", "CurrentUser", "CsrfToken",
    "CreateLegalEntityRequest", "LegalEntity", "LegalEntityRole",
    "LegalEntityMembership", "LegalEntityMembershipList",
    "LegalEntityMember", "LegalEntityMemberList",
    "CreateDealRequest", "UpdateDealRequest", "UpdateDealPartiesRequest", "DealStatus",
    "DealLifecycleProjection", "DealAvailableActions", "DealSummary",
    "DealParticipant", "DealPartyRole", "DealParty", "DealDetail", "DealPage", "UtcTimestamp",
    "ApiErrorCode", "FieldErrorCode", "ProblemDetail", "FieldError",
    "CreateDealInvitationRequest", "AcceptDealInvitationRequest", "DealInvitationTerminalActionRequest",
    "DealInvitationStatus", "DealInvitationAvailableActions", "DealInvitationDeal",
    "DealInvitation", "IncomingDealInvitation", "DealInvitationPage", "IncomingDealInvitationPage",
    "DocumentMediaType", "DocumentStatus", "Sha256", "CreateDocumentUploadIntentRequest",
    "FinalizeDocumentUploadRequest", "DocumentAvailableActions", "PendingDealDocument",
    "AvailableDealDocument", "HistoricalDealDocument", "DealDocumentHistory", "DocumentUploadIntent", "DocumentDownloadLink",
    "DocumentAnalysisStatus", "DocumentAnalysisFailureSummary", "DealDocumentAnalysisSummary",
    "ExtractionSourceReference", "ExtractedParty", "ExtractedRuleValue", "RuleSetStructuredValue", "AdvisoryLegalBasis",
    "ExtractedRule", "ExtractedDeliveryRequirement", "DocumentAnalysisResultSummary",
    "DocumentAnalysisResult", "DealDocumentAnalysis",
    "DealExtractionReview", "ReviewRuleDecision", "KeptRuleDecision", "ModifiedRuleDecision", "ExcludedRuleDecision", "AddedRuleDecision", "AcceptExtractionReviewRequest",
    "RuleCategory", "RuleLegalBasisProvenance", "RuleSetRule", "RuleSetVersionSummary", "RuleSetVersion", "RuleSetVersionHistory",
    "RatificationReadiness", "RatificationPackageStatus", "RatificationCommercialTerms", "RatificationSnapshotParty", "RatificationSnapshotRule", "RatificationSnapshotRuleSet", "RatificationSnapshotDocument", "RatificationPackageSnapshot", "RatificationApproval", "RatificationPackageAvailableActions", "RatificationPackageDetail", "RatificationPackageHistory", "RatificationProjection", "CreateRatificationPackageRequest", "RatificationPackageActionRequest",
    "FundingStatus", "FundingUnitStatus", "PaymentOperationStatus", "FundingUnitAvailableActions",
    "PaymentOperationAvailableActions", "PaymentOperation", "FundingUnit", "FundingPlanDetail",
    "DealFundingSummary", "CreateFundingPlanRequest", "InitiatePaymentOperationRequest",
    "ReconcilePaymentOperationRequest",
    "FulfillmentStatus", "EvidencePolicy", "EvidenceType", "EvidenceMediaType", "EvidenceSubmissionStatus",
    "StartFulfillmentRequest", "CreateEvidenceUploadIntentRequest", "FinalizeEvidenceUploadRequest",
    "CancelEvidenceUploadRequest",
    "AcceptEvidenceRequest", "AcceptWithoutEvidenceRequest", "RejectEvidenceRequest",
    "EvidenceAvailableActions", "MilestoneAvailableActions", "FulfillmentAvailableActions",
    "MilestoneRuleReference", "FulfillmentMilestone",
    "PendingEvidenceSubmission", "SubmittedEvidenceSubmission", "AcceptedEvidenceSubmission",
    "RejectedEvidenceSubmission", "EvidenceSubmission", "EvidenceUploadIntent", "EvidenceDownloadLink",
    "VideoAnalysisStatus", "VideoAnalysisFailureSummary", "VideoAnalysisTimeRange",
    "VideoAnalysisObservationType", "VideoAnalysisObservation", "VideoAnalysisAnomalySeverity",
    "VideoAnalysisAnomaly", "VideoAnalysisAdvisoryOutcome", "VideoAnalysisSummary",
    "VideoAnalysisWarningSeverity", "VideoAnalysisWarningDetails", "VideoAnalysisWarning",
    "VideoAnalysisResult", "RequestVideoAnalysisRequest", "VideoAnalysisAvailableActions",
    "VideoAnalysisDetail",
    "DealFulfillmentSummary", "FulfillmentDetail",
    "DisputeReasonCode", "DisputeStatus", "DisputeOpeningLegalEntity", "DisputeAvailableActions",
    "DisputeSummary", "DisputeEvidenceSnapshotEntry", "DisputeVideoAnalysisSnapshotEntry",
    "DisputeOpeningSnapshot", "DisputeDetail", "DisputePage", "DisputeCommentAuthorAttribution",
    "DisputeComment", "DisputeCommentPage", "OpenDisputeRequest", "CreateDisputeCommentRequest",
    "AcknowledgeDisputeRequest", "WithdrawDisputeRequest", "DealCaseworkSummary",
    "SettlementStatus", "ReleaseOperationStatus", "SettlementAvailableActions",
    "ReleaseOperationAvailableActions", "ReleaseOperationSummary", "ReleaseOperation",
    "SettlementDetail", "DealSettlementSummary", "RequestSettlementReleaseRequest",
    "ReconcileReleaseOperationRequest",
    "RatificationPackageSnapshotV1", "RatificationPackageSnapshotV2", "RatificationPackageSnapshotV3",
}



def bundle_relative_paths(contracts_root: Path) -> list[str]:
    """Return ADR-016 inclusion paths relative to contracts/, POSIX, ordinal-sorted."""
    found: set[str] = set()
    for subdir, patterns in _BUNDLE_INCLUDE_SPECS:
        root = contracts_root / subdir
        if not root.is_dir():
            continue
        for pattern in patterns:
            for path in root.rglob(pattern):
                if path.is_file():
                    found.add(path.relative_to(contracts_root).as_posix())
    return sorted(found)


def file_sha256_hex(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_bundle_manifest(contracts_root: Path) -> bytes:
    """UTF-8 LF manifest: `<lowercase-file-sha256><two spaces><relative-path>\n`."""
    lines: list[str] = []
    for relative in bundle_relative_paths(contracts_root):
        file_digest = file_sha256_hex(contracts_root / relative)
        lines.append(f"{file_digest}  {relative}\n")
    return "".join(lines).encode("utf-8")


def contract_bundle_digest(contracts_root: Path) -> str:
    manifest = build_bundle_manifest(contracts_root)
    return "sha256:" + hashlib.sha256(manifest).hexdigest()


def ownership_duplicate_failures(ownership: dict[str, Any]) -> list[str]:
    """Reject duplicate entries in P1 ownership arrays (global and each byResponse list)."""
    failures: list[str] = []
    global_codes = ownership.get("global")
    if isinstance(global_codes, list) and len(global_codes) != len(set(global_codes)):
        failures.append(
            "FAIL Core API x-m4trust-api-error-ownership: duplicate entries in global"
        )
    by_response = ownership.get("byResponse")
    if isinstance(by_response, dict):
        for name, codes in by_response.items():
            if isinstance(codes, list) and len(codes) != len(set(codes)):
                failures.append(
                    "FAIL Core API x-m4trust-api-error-ownership: "
                    f"duplicate entries in byResponse.{name}"
                )
    return failures


def validate_bundle_digest(failures: list[str]) -> None:
    paths = bundle_relative_paths(ROOT)
    if not paths:
        failures.append("FAIL contract bundle: inclusion set is empty")
        return
    digest = contract_bundle_digest(ROOT)
    print(f"PASS contract bundle digest {digest} ({len(paths)} files)")

    ai_root = os.environ.get("M4TRUST_AI_CONTRACTS_ROOT")
    if not ai_root:
        print("UNVERIFIED_EXTERNAL_GATE: AI contract baseline not supplied")
        return
    ai_path = Path(ai_root)
    if not ai_path.is_dir():
        failures.append(
            f"FAIL AI contract baseline: M4TRUST_AI_CONTRACTS_ROOT is not a directory: {ai_root}"
        )
        return
    main_files = {
        relative: file_sha256_hex(ROOT / relative)
        for relative in bundle_relative_paths(ROOT)
    }
    ai_paths = bundle_relative_paths(ai_path)
    ai_files = {
        relative: file_sha256_hex(ai_path / relative)
        for relative in ai_paths
    }
    all_paths = sorted(set(main_files) | set(ai_files))
    file_mismatches: list[str] = []
    for relative in all_paths:
        expected = main_files.get(relative)
        actual = ai_files.get(relative)
        if expected == actual:
            continue
        file_mismatches.append(
            f"  {relative}: expected={expected or 'MISSING'} actual={actual or 'MISSING'}"
        )
    ai_digest = contract_bundle_digest(ai_path)
    if ai_digest != digest or file_mismatches:
        failures.append(
            "FAIL AI contract baseline digest mismatch: "
            f"main={digest} ai={ai_digest}"
        )
        if file_mismatches:
            failures.append(
                "FAIL AI contract baseline file-level hash delta:\n"
                + "\n".join(file_mismatches)
            )
    else:
        print(f"PASS AI contract baseline digest matches {digest}")


def validate_core_internal_openapi(failures: list[str]) -> None:
    path = ROOT / "openapi/core-internal-v1.yaml"
    if not path.is_file():
        failures.append("FAIL Core internal OpenAPI: openapi/core-internal-v1.yaml is missing")
        return
    try:
        document = yaml.safe_load(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        failures.append(f"FAIL Core internal OpenAPI: cannot parse YAML: {exc}")
        return

    local_failures: list[str] = []
    if not str(document.get("openapi", "")).startswith("3.1."):
        local_failures.append("FAIL Core internal OpenAPI version: expected OpenAPI 3.1")
    if set(document.get("paths", {})) != EXPECTED_CORE_INTERNAL_OPENAPI_PATHS:
        local_failures.append("FAIL Core internal OpenAPI paths: expected exact /internal/v1/contracts")

    operation = document.get("paths", {}).get("/internal/v1/contracts", {}).get("get")
    if not isinstance(operation, dict):
        local_failures.append("FAIL Core internal OpenAPI: missing GET /internal/v1/contracts")
    else:
        if operation.get("operationId") != "getCoreContractBundle":
            local_failures.append("FAIL Core internal OpenAPI operationId: getCoreContractBundle")
        if operation.get("security") != [{"ProbeBearer": []}]:
            local_failures.append("FAIL Core internal OpenAPI security: ProbeBearer required")
        responses = operation.get("responses", {})
        if "200" not in responses or "401" not in responses:
            local_failures.append("FAIL Core internal OpenAPI responses: 200 and 401 required")
        schema_ref = (
            responses.get("200", {})
            .get("content", {})
            .get("application/json", {})
            .get("schema", {})
            .get("$ref")
        )
        if schema_ref != "#/components/schemas/CoreContractBundle":
            local_failures.append(
                "FAIL Core internal OpenAPI 200 schema: must $ref CoreContractBundle"
            )

    security = document.get("components", {}).get("securitySchemes", {}).get("ProbeBearer", {})
    if security.get("type") != "http" or security.get("scheme") != "bearer":
        local_failures.append("FAIL Core internal OpenAPI: ProbeBearer must be HTTP bearer")

    bundle = document.get("components", {}).get("schemas", {}).get("CoreContractBundle", {})
    if bundle.get("additionalProperties") is not False:
        local_failures.append("FAIL CoreContractBundle: additionalProperties must be false")
    if set(bundle.get("required", [])) != {
        "service",
        "releaseRevision",
        "contractBundleDigest",
        "files",
    }:
        local_failures.append("FAIL CoreContractBundle: required field set mismatch")
    props = bundle.get("properties", {})
    if props.get("service") != {"type": "string", "const": "core"}:
        local_failures.append('FAIL CoreContractBundle.service: must be const "core"')
    release = props.get("releaseRevision", {})
    if release.get("type") != "string" or release.get("pattern") != "^[a-f0-9]{40}$":
        local_failures.append("FAIL CoreContractBundle.releaseRevision: pattern ^[a-f0-9]{40}$")
    digest_prop = props.get("contractBundleDigest", {})
    if digest_prop.get("type") != "string" or digest_prop.get("pattern") != "^sha256:[a-f0-9]{64}$":
        local_failures.append(
            "FAIL CoreContractBundle.contractBundleDigest: pattern ^sha256:[a-f0-9]{64}$"
        )
    files = props.get("files", {})
    if files.get("type") != "array":
        local_failures.append("FAIL CoreContractBundle.files: must be array")
    file_ref = files.get("items", {}).get("$ref")
    if file_ref != "#/components/schemas/ContractBundleFile":
        local_failures.append("FAIL CoreContractBundle.files.items: must $ref ContractBundleFile")

    file_schema = document.get("components", {}).get("schemas", {}).get("ContractBundleFile", {})
    if file_schema.get("additionalProperties") is not False:
        local_failures.append("FAIL ContractBundleFile: additionalProperties must be false")
    if set(file_schema.get("required", [])) != {"path", "sha256"}:
        local_failures.append("FAIL ContractBundleFile: required path and sha256")
    file_props = file_schema.get("properties", {})
    if file_props.get("path", {}).get("type") != "string":
        local_failures.append("FAIL ContractBundleFile.path: string required")
    sha = file_props.get("sha256", {})
    if sha.get("type") != "string" or sha.get("pattern") != "^[a-f0-9]{64}$":
        local_failures.append("FAIL ContractBundleFile.sha256: pattern ^[a-f0-9]{64}$")

    failures.extend(local_failures)
    if not local_failures:
        print("PASS Core internal OpenAPI ADR-016 projection")


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError("top-level JSON value must be an object")
    return value


def file_uri(path: Path) -> str:
    return path.resolve().as_uri()


def json_path(path: Any) -> str:
    parts = [str(part) for part in path]
    return ".".join(parts) if parts else "<root>"


def find_duplicate_ids(schema_paths: list[Path], schemas: dict[Path, dict[str, Any]]) -> dict[str, list[str]]:
    locations: dict[str, list[str]] = {}
    for path in schema_paths:
        schema_id = schemas[path].get("$id")
        if isinstance(schema_id, str):
            try:
                display_path = str(path.relative_to(ROOT))
            except ValueError:
                display_path = str(path)
            locations.setdefault(schema_id, []).append(display_path)
    return {schema_id: paths for schema_id, paths in locations.items() if len(paths) > 1}


def load_schemas(schema_paths: list[Path]) -> tuple[dict[Path, dict[str, Any]], dict[str, dict[str, Any]], list[str]]:
    schemas: dict[Path, dict[str, Any]] = {}
    store: dict[str, dict[str, Any]] = {}
    failures: list[str] = []
    for path in schema_paths:
        try:
            schema = read_json(path)
            schemas[path] = schema
            store[file_uri(path)] = schema
            schema_id = schema.get("$id")
            if isinstance(schema_id, str):
                store[schema_id] = schema
            else:
                failures.append(f"FAIL schema {path.relative_to(ROOT)}: missing string $id")
        except Exception as exc:  # noqa: BLE001 - this is a CLI validator
            failures.append(f"FAIL schema {path.relative_to(ROOT)}: {exc}")
    return schemas, store, failures


def validator_for(schema_path: Path, schema: dict[str, Any], store: dict[str, dict[str, Any]]) -> Draft202012Validator:
    resolver = RefResolver(base_uri=file_uri(schema_path), referrer=schema, store=store)
    return Draft202012Validator(schema, resolver=resolver, format_checker=FormatChecker())


def errors_for(schema_path: Path, schema: dict[str, Any], instance: dict[str, Any], store: dict[str, dict[str, Any]]) -> list[Any]:
    validator = validator_for(schema_path, schema, store)
    return sorted(validator.iter_errors(instance), key=lambda error: list(error.path))


def format_errors(errors: list[Any]) -> str:
    return "; ".join(f"{json_path(error.path)}: {error.message}" for error in errors)


def expect_invalid(label: str, schema_path: Path, instance: dict[str, Any], store: dict[str, dict[str, Any]], failures: list[str]) -> None:
    errors = errors_for(schema_path, read_json(schema_path), instance, store)
    if not errors:
        failures.append(f"FAIL expected-invalid {label}: instance unexpectedly passed")
    else:
        print(f"PASS expected-invalid {label}: {format_errors(errors)}")


def expect_valid(label: str, schema_path: Path, instance: dict[str, Any], store: dict[str, dict[str, Any]], failures: list[str]) -> None:
    errors = errors_for(schema_path, read_json(schema_path), instance, store)
    if errors:
        failures.append(f"FAIL expected-valid {label}: {format_errors(errors)}")
    else:
        print(f"PASS expected-valid {label}")


def closed_string_enum(schema: dict[str, Any] | None) -> list[str] | None:
    if not isinstance(schema, dict) or schema.get("type") != "string":
        return None
    values = schema.get("enum")
    if not isinstance(values, list) or not values:
        return None
    if any(not isinstance(value, str) or not value for value in values):
        return None
    return list(values)


def read_java_enum_names(java_path: Path, enum_name: str) -> set[str] | None:
    if not java_path.is_file():
        return None
    text = java_path.read_text(encoding="utf-8")
    match = re.search(
        rf"public\s+enum\s+{re.escape(enum_name)}\s*\{{([^}}]*?)(?:;|\}})",
        text,
        re.S,
    )
    if match is None:
        return None
    body = match.group(1)
    names = re.findall(r"\b([A-Z][A-Z0-9_]*)\b", body)
    return set(names)


def owned_api_error_codes(core_openapi: dict[str, Any]) -> set[str] | None:
    """Catalog-independent ownership from components.x-m4trust-api-error-ownership."""
    ownership = core_openapi.get("components", {}).get("x-m4trust-api-error-ownership")
    if not isinstance(ownership, dict):
        return None
    owned: set[str] = set()
    global_codes = ownership.get("global")
    if not isinstance(global_codes, list) or not global_codes:
        return None
    for code in global_codes:
        if not isinstance(code, str) or not code:
            return None
        owned.add(code)
    by_response = ownership.get("byResponse")
    if not isinstance(by_response, dict) or not by_response:
        return None
    responses = core_openapi.get("components", {}).get("responses", {})
    if set(by_response) != set(responses):
        return None
    for name, codes in by_response.items():
        if not isinstance(codes, list) or not codes:
            return None
        for code in codes:
            if not isinstance(code, str) or not code:
                return None
            owned.add(code)
    return owned


def validate_closed_error_catalogs(core_openapi: dict[str, Any], failures: list[str]) -> None:
    schemas = core_openapi.get("components", {}).get("schemas", {})
    api_schema = schemas.get("ApiErrorCode")
    field_schema = schemas.get("FieldErrorCode")
    api_values = closed_string_enum(api_schema)
    field_values = closed_string_enum(field_schema)
    local_failures: list[str] = []

    if api_values is None:
        local_failures.append("FAIL Core API ApiErrorCode: closed string enum catalog is required")
    if field_values is None:
        local_failures.append("FAIL Core API FieldErrorCode: closed string enum catalog is required")
    if local_failures:
        failures.extend(local_failures)
        return

    assert api_values is not None and field_values is not None

    if len(api_values) != len(set(api_values)):
        local_failures.append("FAIL Core API ApiErrorCode: duplicate enum values")
    if len(field_values) != len(set(field_values)):
        local_failures.append("FAIL Core API FieldErrorCode: duplicate enum values")

    removed = sorted(REMOVED_COMBINED_API_ERROR_CODES.intersection(api_values))
    if removed:
        local_failures.append(
            "FAIL Core API ApiErrorCode: removed combined fulfillment codes present: "
            + ", ".join(removed)
        )

    problem_code = schemas.get("ProblemDetail", {}).get("properties", {}).get("code")
    field_code = schemas.get("FieldError", {}).get("properties", {}).get("code")
    if problem_code != {"$ref": "#/components/schemas/ApiErrorCode"}:
        local_failures.append("FAIL Core API ProblemDetail.code: must $ref ApiErrorCode (open string rejected)")
    if field_code != {"$ref": "#/components/schemas/FieldErrorCode"}:
        local_failures.append("FAIL Core API FieldError.code: must $ref FieldErrorCode (open string rejected)")

    ownership = core_openapi.get("components", {}).get("x-m4trust-api-error-ownership")
    if isinstance(ownership, dict):
        local_failures.extend(ownership_duplicate_failures(ownership))
        # Negative proof: duplicate ownership entries must be detectable.
        if not ownership_duplicate_failures(ownership):
            probe_ownership = copy.deepcopy(ownership)
            global_codes = probe_ownership.get("global")
            if isinstance(global_codes, list) and global_codes:
                probe_ownership["global"] = list(global_codes) + [global_codes[0]]
                if not ownership_duplicate_failures(probe_ownership):
                    local_failures.append(
                        "FAIL Core API ApiErrorCode negative duplicate ownership detector: "
                        "duplicate global entry was not detected"
                    )
                else:
                    print("PASS expected-invalid ApiErrorCode duplicate ownership in global")
            by_response = probe_ownership.get("byResponse")
            if isinstance(by_response, dict) and by_response:
                first_name = next(iter(sorted(by_response)))
                codes = by_response[first_name]
                if isinstance(codes, list) and codes:
                    by_response[first_name] = list(codes) + [codes[0]]
                    if not ownership_duplicate_failures(probe_ownership):
                        local_failures.append(
                            "FAIL Core API ApiErrorCode negative duplicate ownership detector: "
                            f"duplicate byResponse.{first_name} entry was not detected"
                        )
                    else:
                        print(
                            "PASS expected-invalid ApiErrorCode duplicate ownership "
                            f"in byResponse.{first_name}"
                        )

    owned = owned_api_error_codes(core_openapi)
    if owned is None:
        local_failures.append(
            "FAIL Core API x-m4trust-api-error-ownership: machine-readable ownership "
            "must list global codes and exact byResponse coverage for every reusable response"
        )
    else:
        catalog_set = set(api_values)
        missing_owned = sorted(owned - catalog_set)
        extra_catalog = sorted(catalog_set - owned)
        if missing_owned or extra_catalog:
            local_failures.append(
                "FAIL Core API ApiErrorCode ownership exact-set mismatch"
                + (f"; owned-missing-from-catalog={','.join(missing_owned)}" if missing_owned else "")
                + (f"; catalog-not-owned={','.join(extra_catalog)}" if extra_catalog else "")
            )
        # Negative proof: removing one owned catalog member must be detectable.
        if catalog_set and not missing_owned and not extra_catalog:
            probe = copy.deepcopy(core_openapi)
            removed = next(iter(sorted(catalog_set)))
            probe["components"]["schemas"]["ApiErrorCode"]["enum"] = [
                code for code in api_values if code != removed
            ]
            probe_owned = owned_api_error_codes(probe)
            probe_catalog = set(closed_string_enum(probe["components"]["schemas"]["ApiErrorCode"]) or [])
            if probe_owned is None or probe_owned == probe_catalog or removed not in (probe_owned - probe_catalog):
                local_failures.append(
                    "FAIL Core API ApiErrorCode negative ownership drift detector: "
                    "removing a documented/global catalog member was not detected"
                )
            else:
                print(f"PASS expected-invalid ApiErrorCode ownership drift when removing {removed}")

    repo_root = ROOT.parent
    java_api = read_java_enum_names(
        repo_root / "services/core-api/src/main/java/com/m4trust/coreapi/api/ApiErrorCode.java",
        "ApiErrorCode",
    )
    java_field = read_java_enum_names(
        repo_root / "services/core-api/src/main/java/com/m4trust/coreapi/api/FieldErrorCode.java",
        "FieldErrorCode",
    )
    if java_api is None:
        local_failures.append("FAIL Core API ApiErrorCode: Java enum source not found for exact-set check")
    elif java_api != set(api_values):
        only_java = sorted(java_api - set(api_values))
        only_openapi = sorted(set(api_values) - java_api)
        local_failures.append(
            "FAIL Core API ApiErrorCode exact-set mismatch"
            + (f"; java-only={','.join(only_java)}" if only_java else "")
            + (f"; openapi-only={','.join(only_openapi)}" if only_openapi else "")
        )
    if java_field is None:
        local_failures.append("FAIL Core API FieldErrorCode: Java enum source not found for exact-set check")
    elif java_field != set(field_values):
        only_java = sorted(java_field - set(field_values))
        only_openapi = sorted(set(field_values) - java_field)
        local_failures.append(
            "FAIL Core API FieldErrorCode exact-set mismatch"
            + (f"; java-only={','.join(only_java)}" if only_java else "")
            + (f"; openapi-only={','.join(only_openapi)}" if only_openapi else "")
        )

    failures.extend(local_failures)
    if not local_failures:
        print(
            f"PASS Core API closed error catalogs "
            f"(ApiErrorCode={len(set(api_values))}, FieldErrorCode={len(set(field_values))})"
        )


def sample_problem_detail(code: str, field_code: str | None = None) -> dict[str, Any]:
    problem: dict[str, Any] = {
        "type": "https://problems.m4trust.internal/validation-failed",
        "title": "Validation failed",
        "status": 422,
        "detail": "One or more fields are invalid.",
        "code": code,
        "correlationId": "00000000-0000-4000-8000-000000000001",
    }
    if field_code is not None:
        problem["errors"] = [{"field": "email", "code": field_code, "message": "Field is invalid."}]
    return problem


def validate_error_catalog_fixtures(core_openapi: dict[str, Any], failures: list[str]) -> None:
    """Validate ProblemDetail/FieldError against OpenAPI component schemas via jsonschema."""
    schemas = core_openapi.get("components", {}).get("schemas", {})
    api_values = closed_string_enum(schemas.get("ApiErrorCode"))
    field_values = closed_string_enum(schemas.get("FieldErrorCode"))
    if not api_values or not field_values:
        failures.append("FAIL error-catalog fixtures: closed catalogs unavailable")
        return

    store: dict[str, dict[str, Any]] = {}
    for name, schema in schemas.items():
        if isinstance(schema, dict):
            cloned = copy.deepcopy(schema)
            cloned["$id"] = f"https://schemas.m4trust.internal/openapi/{name}"
            store[cloned["$id"]] = cloned

    def resolve_ref(ref: str) -> dict[str, Any]:
        name = ref.rsplit("/", 1)[-1]
        return store[f"https://schemas.m4trust.internal/openapi/{name}"]

    def rewrite(node: Any) -> Any:
        if isinstance(node, dict):
            if set(node.keys()) == {"$ref"} and isinstance(node["$ref"], str) and node["$ref"].startswith("#/components/schemas/"):
                return rewrite(copy.deepcopy(resolve_ref(node["$ref"])))
            return {key: rewrite(value) for key, value in node.items()}
        if isinstance(node, list):
            return [rewrite(item) for item in node]
        return node

    problem_schema = rewrite(copy.deepcopy(schemas["ProblemDetail"]))
    problem_schema["$id"] = "https://schemas.m4trust.internal/openapi/ProblemDetail-resolved"
    store[problem_schema["$id"]] = problem_schema
    schema_path = Path("openapi/ProblemDetail-resolved")

    def local_errors(instance: dict[str, Any]) -> list[Any]:
        validator = Draft202012Validator(problem_schema, resolver=RefResolver(
            base_uri=problem_schema["$id"], referrer=problem_schema, store=store
        ), format_checker=FormatChecker())
        return sorted(validator.iter_errors(instance), key=lambda error: list(error.path))

    valid = sample_problem_detail(api_values[0], field_values[0])
    if local_errors(valid):
        failures.append(f"FAIL expected-valid ProblemDetail catalog member: {format_errors(local_errors(valid))}")
    else:
        print("PASS expected-valid ProblemDetail with catalog ApiErrorCode/FieldErrorCode")

    open_code = sample_problem_detail("NOT_IN_CATALOG")
    if not local_errors(open_code):
        failures.append("FAIL expected-invalid ProblemDetail open ApiErrorCode: instance unexpectedly passed")
    else:
        print(f"PASS expected-invalid ProblemDetail open ApiErrorCode: {format_errors(local_errors(open_code))}")

    bad_field = sample_problem_detail(api_values[0], "TOO_SHORT")
    if not local_errors(bad_field):
        failures.append("FAIL expected-invalid FieldError open FieldErrorCode: instance unexpectedly passed")
    else:
        print(f"PASS expected-invalid FieldError open FieldErrorCode: {format_errors(local_errors(bad_field))}")

    for removed in sorted(REMOVED_COMBINED_API_ERROR_CODES):
        removed_instance = sample_problem_detail(removed)
        if not local_errors(removed_instance):
            failures.append(f"FAIL expected-invalid removed combined code {removed}: instance unexpectedly passed")
        else:
            print(f"PASS expected-invalid removed combined code {removed}")


def event_properties(schema: dict[str, Any]) -> dict[str, Any]:
    properties: dict[str, Any] = {}
    for branch in schema.get("allOf", []):
        if isinstance(branch, dict):
            properties.update(branch.get("properties", {}))
    return properties


def validate_contract_documents(failures: list[str]) -> None:
    asyncapi_path = ROOT / "asyncapi/m4trust-ai-v1.yaml"
    ai_openapi_path = ROOT / "openapi/ai-internal-v1.yaml"
    core_openapi_path = ROOT / "openapi/core-api-v1.yaml"
    try:
        asyncapi = yaml.safe_load(asyncapi_path.read_text(encoding="utf-8"))
        ai_openapi = yaml.safe_load(ai_openapi_path.read_text(encoding="utf-8"))
        core_openapi = yaml.safe_load(core_openapi_path.read_text(encoding="utf-8"))
        if set(asyncapi.get("channels", {})) != EXPECTED_CHANNELS:
            failures.append("FAIL AsyncAPI channels: accepted routing-key set changed")
        if set(ai_openapi.get("paths", {})) != EXPECTED_AI_INTERNAL_OPENAPI_PATHS:
            failures.append("FAIL AI internal OpenAPI paths: operational endpoint set changed")
        if not str(core_openapi.get("openapi", "")).startswith("3.1."):
            failures.append("FAIL Core API OpenAPI version: expected OpenAPI 3.1")
        core_paths = core_openapi.get("paths", {})
        expected_core_paths = {path for path, _ in EXPECTED_CORE_API_OPERATIONS}
        if set(core_paths) != expected_core_paths:
            failures.append("FAIL Core API OpenAPI paths: accepted public endpoint set changed")
        for (path, method), expected in EXPECTED_CORE_API_OPERATIONS.items():
            operation = core_paths.get(path, {}).get(method)
            if not isinstance(operation, dict):
                failures.append(f"FAIL Core API operation: missing {method.upper()} {path}")
                continue
            if operation.get("operationId") != expected["operationId"]:
                failures.append(f"FAIL Core API operationId: {method.upper()} {path}")
            if set(operation.get("responses", {})) != expected["responses"]:
                failures.append(f"FAIL Core API responses: {method.upper()} {path}")
            if operation.get("security") != expected["security"]:
                failures.append(f"FAIL Core API security: {method.upper()} {path}")

        core_components = core_openapi.get("components", {})
        core_schemas = set(core_components.get("schemas", {}))
        missing_core_schemas = REQUIRED_CORE_API_SCHEMAS - core_schemas
        if missing_core_schemas:
            failures.append(
                "FAIL Core API OpenAPI schemas: missing " + ", ".join(sorted(missing_core_schemas))
            )
        public_user = core_components.get("schemas", {}).get("PublicUser", {})
        if (set(public_user.get("properties", {})) != {"id", "email", "displayName"}
                or set(public_user.get("required", [])) != {"id", "email", "displayName"}
                or public_user.get("additionalProperties") is not False):
            failures.append("FAIL Core API PublicUser: field set must be exactly id, email, displayName")
        current_user = core_components.get("schemas", {}).get("CurrentUser", {})
        current_user_memberships = current_user.get("properties", {}).get("memberships", {})
        if (set(current_user.get("properties", {})) != {"id", "email", "displayName", "memberships"}
                or set(current_user.get("required", [])) != {"id", "email", "displayName", "memberships"}
                or current_user.get("additionalProperties") is not False
                or current_user_memberships.get("type") != "array"
                or current_user_memberships.get("items", {}).get("$ref")
                != "#/components/schemas/LegalEntityMembership"):
            failures.append("FAIL Core API CurrentUser: required non-null memberships bootstrap changed")
        register_request = core_components.get("schemas", {}).get("RegisterRequest", {})
        if (set(register_request.get("required", [])) != {"email", "password", "displayName"}
                or register_request.get("properties", {}).get("password", {}).get("minLength") != 15
                or register_request.get("properties", {}).get("password", {}).get("maxLength") != 128
                or register_request.get("properties", {}).get("password", {}).get("writeOnly") is not True):
            failures.append("FAIL Core API RegisterRequest: required fields or password policy changed")
        create_legal_entity = core_components.get("schemas", {}).get("CreateLegalEntityRequest", {})
        create_properties = create_legal_entity.get("properties", {})
        if (set(create_legal_entity.get("required", [])) != {"legalName", "registrationNumber"}
                or set(create_properties) != {"legalName", "registrationNumber"}
                or create_legal_entity.get("additionalProperties") is not False
                or (create_properties.get("legalName", {}).get("minLength"),
                    create_properties.get("legalName", {}).get("maxLength")) != (1, 200)
                or (create_properties.get("registrationNumber", {}).get("minLength"),
                    create_properties.get("registrationNumber", {}).get("maxLength")) != (1, 100)):
            failures.append("FAIL Core API CreateLegalEntityRequest: minimum bounded field set changed")
        legal_entity_role = core_components.get("schemas", {}).get("LegalEntityRole", {})
        if legal_entity_role.get("type") != "string" or legal_entity_role.get("enum") != ["ADMIN", "MEMBER"]:
            failures.append("FAIL Core API LegalEntityRole: role set must be exactly ADMIN and MEMBER")
        for list_schema_name, item_schema_name in (
                ("LegalEntityMembershipList", "LegalEntityMembership"),
                ("LegalEntityMemberList", "LegalEntityMember")):
            list_schema = core_components.get("schemas", {}).get(list_schema_name, {})
            items_property = list_schema.get("properties", {}).get("items", {})
            if (set(list_schema.get("required", [])) != {"items"}
                    or set(list_schema.get("properties", {})) != {"items"}
                    or list_schema.get("additionalProperties") is not False
                    or items_property.get("type") != "array"
                    or items_property.get("items", {}).get("$ref")
                    != f"#/components/schemas/{item_schema_name}"):
                failures.append(f"FAIL Core API {list_schema_name}: stable non-null items DTO changed")

        create_deal = core_components.get("schemas", {}).get("CreateDealRequest", {})
        create_deal_properties = create_deal.get("properties", {})
        create_description = create_deal_properties.get("description", {})
        if (set(create_deal.get("required", [])) != {"title"}
                or set(create_deal_properties) != {"title", "description"}
                or create_deal.get("additionalProperties") is not False
                or (create_deal_properties.get("title", {}).get("minLength"),
                    create_deal_properties.get("title", {}).get("maxLength")) != (1, 200)
                or create_description.get("type") != ["string", "null"]
                or create_description.get("maxLength") != 4000):
            failures.append("FAIL Core API CreateDealRequest: frozen title and optional nullable description changed")
        update_deal = core_components.get("schemas", {}).get("UpdateDealRequest", {})
        update_deal_properties = update_deal.get("properties", {})
        if (set(update_deal.get("required", [])) != {"title", "description", "expectedVersion"}
                or set(update_deal_properties) != {"title", "description", "expectedVersion"}
                or update_deal.get("additionalProperties") is not False
                or (update_deal_properties.get("title", {}).get("minLength"),
                    update_deal_properties.get("title", {}).get("maxLength")) != (1, 200)
                or update_deal_properties.get("description", {}).get("type") != ["string", "null"]
                or update_deal_properties.get("description", {}).get("maxLength") != 4000
                or update_deal_properties.get("expectedVersion", {}).get("minimum") != 0):
            failures.append("FAIL Core API UpdateDealRequest: replacement fields or expectedVersion changed")
        update_parties = core_components.get("schemas", {}).get("UpdateDealPartiesRequest", {})
        update_parties_properties = update_parties.get("properties", {})
        if (set(update_parties.get("required", [])) != {"buyerLegalEntityId", "sellerLegalEntityId", "expectedVersion"}
                or set(update_parties_properties) != {"buyerLegalEntityId", "sellerLegalEntityId", "expectedVersion"}
                or update_parties.get("additionalProperties") is not False
                or update_parties_properties.get("buyerLegalEntityId", {}).get("type") != ["string", "null"]
                or update_parties_properties.get("buyerLegalEntityId", {}).get("format") != "uuid"
                or update_parties_properties.get("sellerLegalEntityId", {}).get("type") != ["string", "null"]
                or update_parties_properties.get("sellerLegalEntityId", {}).get("format") != "uuid"
                or update_parties_properties.get("expectedVersion", {}).get("minimum") != 0):
            failures.append("FAIL Core API UpdateDealPartiesRequest: atomic nullable parties and expectedVersion changed")
        deal_status = core_components.get("schemas", {}).get("DealStatus", {})
        if deal_status.get("enum") != ["DRAFT", "ACTIVE", "CANCELLED", "COMPLETED", "ARCHIVED"]:
            failures.append("FAIL Core API DealStatus: ADR lifecycle state set changed")
        lifecycle = core_components.get("schemas", {}).get("DealLifecycleProjection", {})
        if lifecycle.get("enum") != [
                "DRAFT", "CONTRACT_ANALYSIS", "MANUAL_REVIEW", "RATIFICATION",
                "FUNDING", "FULFILLMENT", "SETTLEMENT", "DISPUTE",
                "COMPLETED", "CANCELLED", "ARCHIVED"]:
            failures.append("FAIL Core API DealLifecycleProjection: ADR projection set changed")
        actions = core_components.get("schemas", {}).get("DealAvailableActions", {})
        optional_deal_actions = (
            "canReviewExtraction", "canCreateRatificationPackage", "canApproveRatification", "canRejectRatification",
            "canCreateFundingPlan", "canInitiateFunding", "canReconcilePaymentOperation",
            "canStartFulfillment", "canUploadEvidence", "canAcceptEvidence", "canRejectEvidence",
            "canAcceptWithoutEvidence",
            "canOpenDispute", "canRequestRelease", "canReconcileRelease",
        )
        if (set(actions.get("required", [])) != {"canUpdate", "canCancel", "canCreateInvitation", "canManageParties", "canCreateDocumentUploadIntent", "canRequestAnalysis"}
                or set(actions.get("properties", {})) != {"canUpdate", "canCancel", "canCreateInvitation", "canManageParties", "canCreateDocumentUploadIntent", "canRequestAnalysis"} | set(optional_deal_actions)
                or actions.get("additionalProperties") is not False
                or any(
                    actions.get("properties", {}).get(name, {}).get("type") != "boolean"
                    for name in ("canUpdate", "canCancel", "canCreateInvitation", "canManageParties", "canCreateDocumentUploadIntent", "canRequestAnalysis") + optional_deal_actions
                )):
            failures.append("FAIL Core API DealAvailableActions: backend-derived action set changed")
        summary = core_components.get("schemas", {}).get("DealSummary", {})
        detail = core_components.get("schemas", {}).get("DealDetail", {})
        common_deal_fields = {
            "id", "reference", "title", "status", "lifecycle", "version",
            "createdAt", "updatedAt", "availableActions",
        }
        if (set(summary.get("required", [])) != common_deal_fields
                or set(summary.get("properties", {})) != common_deal_fields
                or summary.get("additionalProperties") is not False):
            failures.append("FAIL Core API DealSummary: frozen summary projection changed")
        participant = core_components.get("schemas", {}).get("DealParticipant", {})
        if (set(participant.get("required", [])) != {"legalEntityId", "legalName", "joinedAt", "partyRoles"}
                or set(participant.get("properties", {})) != {"legalEntityId", "legalName", "joinedAt", "partyRoles"}
                or participant.get("additionalProperties") is not False
                or participant.get("properties", {}).get("legalEntityId", {}).get("format") != "uuid"
                or participant.get("properties", {}).get("legalName", {}).get("maxLength") != 200
                or participant.get("properties", {}).get("joinedAt", {}).get("$ref")
                != "#/components/schemas/UtcTimestamp"
                or participant.get("properties", {}).get("partyRoles", {}).get("type") != "array"
                or participant.get("properties", {}).get("partyRoles", {}).get("minItems") != 0
                or participant.get("properties", {}).get("partyRoles", {}).get("maxItems") != 1
                or participant.get("properties", {}).get("partyRoles", {}).get("uniqueItems") is not True
                or participant.get("properties", {}).get("partyRoles", {}).get("items", {}).get("$ref")
                != "#/components/schemas/DealPartyRole"):
            failures.append("FAIL Core API DealParticipant: non-consent party-role projection changed")
        party_role = core_components.get("schemas", {}).get("DealPartyRole", {})
        party = core_components.get("schemas", {}).get("DealParty", {})
        if party_role.get("enum") != ["BUYER", "SELLER"]:
            failures.append("FAIL Core API DealPartyRole: closed buyer/seller role set changed")
        if (set(party.get("required", [])) != {"legalEntityId", "legalName"}
                or set(party.get("properties", {})) != {"legalEntityId", "legalName"}
                or party.get("additionalProperties") is not False
                or party.get("properties", {}).get("legalEntityId", {}).get("format") != "uuid"
                or party.get("properties", {}).get("legalName", {}).get("maxLength") != 200):
            failures.append("FAIL Core API DealParty: stable buyer/seller assignment projection changed")
        detail_fields = common_deal_fields | {"description", "buyer", "seller", "participants", "currentDocument", "analysis", "currentRuleSet", "ratification", "funding", "fulfillment", "casework", "settlement"}
        detail_description = detail.get("properties", {}).get("description", {})
        detail_participants = detail.get("properties", {}).get("participants", {})
        detail_buyer = detail.get("properties", {}).get("buyer", {})
        detail_seller = detail.get("properties", {}).get("seller", {})
        nullable_party = [{"$ref": "#/components/schemas/DealParty"}, {"type": "null"}]
        if (set(detail.get("required", [])) != common_deal_fields | {"description", "buyer", "seller", "participants", "currentDocument", "analysis"}
                or set(detail.get("properties", {})) != detail_fields
                or detail.get("additionalProperties") is not False
                or detail_description.get("type") != ["string", "null"]
                or detail_description.get("maxLength") != 4000
                or detail_buyer.get("anyOf") != nullable_party
                or detail_seller.get("anyOf") != nullable_party
                or detail_participants.get("type") != "array"
                or detail_participants.get("items", {}).get("$ref")
                != "#/components/schemas/DealParticipant"
                or detail.get("properties", {}).get("currentRuleSet", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/RuleSetVersionSummary"}, {"type": "null"}]
                or detail.get("properties", {}).get("ratification", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/RatificationProjection"}, {"type": "null"}]
                or detail.get("properties", {}).get("funding", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/DealFundingSummary"}, {"type": "null"}]
                or detail.get("properties", {}).get("fulfillment", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/DealFulfillmentSummary"}, {"type": "null"}]
                or detail.get("properties", {}).get("casework", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/DealCaseworkSummary"}, {"type": "null"}]
                or detail.get("properties", {}).get("settlement", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/DealSettlementSummary"}, {"type": "null"}]):
            failures.append("FAIL Core API DealDetail: party and participant-role projection changed")

        ratification_terms = core_components.get("schemas", {}).get("RatificationCommercialTerms", {})
        ratification_snapshot = core_components.get("schemas", {}).get("RatificationPackageSnapshot", {})
        ratification_snapshot_v1 = core_components.get("schemas", {}).get("RatificationPackageSnapshotV1", {})
        ratification_snapshot_v2 = core_components.get("schemas", {}).get("RatificationPackageSnapshotV2", {})
        ratification_snapshot_v3 = core_components.get("schemas", {}).get("RatificationPackageSnapshotV3", {})
        ratification_status = core_components.get("schemas", {}).get("RatificationPackageStatus", {})
        ratification_readiness = core_components.get("schemas", {}).get("RatificationReadiness", {})
        ratification_create = core_components.get("schemas", {}).get("CreateRatificationPackageRequest", {})
        ratification_action = core_components.get("schemas", {}).get("RatificationPackageActionRequest", {})
        ratification_detail = core_components.get("schemas", {}).get("RatificationPackageDetail", {})
        ratification_approval = core_components.get("schemas", {}).get("RatificationApproval", {})
        ratification_snapshot_rule_set = core_components.get("schemas", {}).get("RatificationSnapshotRuleSet", {})
        lowercase_uuid_pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        ratification_snapshot_party = core_components.get("schemas", {}).get("RatificationSnapshotParty", {})
        ratification_snapshot_document = core_components.get("schemas", {}).get("RatificationSnapshotDocument", {})
        ratification_conflict = core_components.get("responses", {}).get("RatificationPackageActionConflict", {})
        ratification_create_conflict = core_components.get("responses", {}).get("RatificationPackageCreateConflict", {})
        evidence_policy = core_components.get("schemas", {}).get("EvidencePolicy", {})
        snapshot_v1_rules = ratification_snapshot_v1.get("properties", {}).get("ruleSet", {}).get("$ref")
        snapshot_v2_rules = ratification_snapshot_v2.get("properties", {}).get("ruleSet", {}).get("$ref")
        snapshot_v3_rules = ratification_snapshot_v3.get("properties", {}).get("ruleSet", {}).get("$ref")
        ratification_closed_fields = {
            "RatificationCommercialTerms": {"amountMinor", "currency"},
            "RatificationSnapshotParty": {"legalEntityId", "legalName"},
            "RatificationSnapshotRule": {"ruleReference", "decision", "category", "title", "description", "structuredValue", "legalBasis", "legalBasisProvenance"},
            "RatificationSnapshotRuleSet": {"ruleSetVersionId", "version", "rules"},
            "RatificationSnapshotDocument": {"documentId", "objectVersion", "sha256"},
            "RatificationPackageSnapshotV1": {"schemaVersion", "dealId", "dealReference", "dealTitle", "buyer", "seller", "ruleSet", "commercialTerms", "document"},
            "RatificationPackageSnapshotV2": {"schemaVersion", "dealId", "dealReference", "dealTitle", "buyer", "seller", "ruleSet", "commercialTerms", "document", "disputeWindowDays"},
            "RatificationPackageSnapshotV3": {"schemaVersion", "dealId", "dealReference", "dealTitle", "buyer", "seller", "ruleSet", "commercialTerms", "document", "disputeWindowDays", "evidencePolicy"},
            "RatificationApproval": {"legalEntityId", "legalName", "status", "approvedAt", "approverUserId"},
            "RatificationPackageAvailableActions": {"canApprove", "canReject"},
            "RatificationPackageDetail": {"id", "version", "status", "contentHash", "snapshot", "approvals", "availableActions", "createdAt"},
            "RatificationPackageHistory": {"items"},
            "RatificationProjection": {"readiness", "currentPackage"},
            "CreateRatificationPackageRequest": {"expectedVersion", "commercialTerms", "disputeWindowDays", "evidencePolicy"},
            "RatificationPackageActionRequest": {"expectedPackageVersion"},
        }
        ratification_required_fields = {
            "RatificationCommercialTerms": {"amountMinor", "currency"},
            "RatificationSnapshotParty": {"legalEntityId", "legalName"},
            "RatificationSnapshotRule": {"ruleReference", "decision", "category", "title", "description", "structuredValue", "legalBasis", "legalBasisProvenance"},
            "RatificationSnapshotRuleSet": {"ruleSetVersionId", "version", "rules"},
            "RatificationSnapshotDocument": {"documentId", "objectVersion", "sha256"},
            "RatificationPackageSnapshotV1": {"schemaVersion", "dealId", "dealReference", "dealTitle", "buyer", "seller", "ruleSet", "commercialTerms", "document"},
            "RatificationPackageSnapshotV2": {"schemaVersion", "dealId", "dealReference", "dealTitle", "buyer", "seller", "ruleSet", "commercialTerms", "document", "disputeWindowDays"},
            "RatificationPackageSnapshotV3": {"schemaVersion", "dealId", "dealReference", "dealTitle", "buyer", "seller", "ruleSet", "commercialTerms", "document", "disputeWindowDays", "evidencePolicy"},
            "RatificationApproval": {"legalEntityId", "legalName", "status", "approvedAt", "approverUserId"},
            "RatificationPackageAvailableActions": {"canApprove", "canReject"},
            "RatificationPackageDetail": {"id", "version", "status", "contentHash", "snapshot", "approvals", "availableActions", "createdAt"},
            "RatificationPackageHistory": {"items"},
            "RatificationProjection": {"readiness", "currentPackage"},
            "CreateRatificationPackageRequest": {"expectedVersion", "commercialTerms"},
            "RatificationPackageActionRequest": {"expectedPackageVersion"},
        }
        snapshot_one_of = ratification_snapshot.get("oneOf", [])
        snapshot_discriminator = ratification_snapshot.get("discriminator", {})
        if (ratification_readiness.get("enum") != ["NOT_READY", "READY"]
                or ratification_status.get("enum") != ["PENDING", "RATIFIED", "REJECTED", "SUPERSEDED"]
                or evidence_policy.get("enum") != ["REQUIRED", "NOT_REQUIRED"]
                or set(ratification_terms.get("required", [])) != {"amountMinor", "currency"}
                or set(ratification_terms.get("properties", {})) != {"amountMinor", "currency"}
                or ratification_terms.get("additionalProperties") is not False
                or ratification_terms.get("properties", {}).get("amountMinor", {}).get("minimum") != 1
                or ratification_terms.get("properties", {}).get("amountMinor", {}).get("maximum") != 9007199254740991
                or ratification_terms.get("properties", {}).get("currency", {}).get("pattern") != "^[A-Z]{3}$"
                or set(ratification_create.get("required", [])) != {"expectedVersion", "commercialTerms"}
                or set(ratification_create.get("properties", {})) != {"expectedVersion", "commercialTerms", "disputeWindowDays", "evidencePolicy"}
                or ratification_create.get("additionalProperties") is not False
                or ratification_create.get("properties", {}).get("expectedVersion", {}).get("maximum") != 9007199254740991
                or ratification_create.get("properties", {}).get("disputeWindowDays", {}).get("minimum") != 0
                or ratification_create.get("properties", {}).get("disputeWindowDays", {}).get("maximum") != 365
                or ratification_create.get("properties", {}).get("evidencePolicy", {}).get("$ref")
                != "#/components/schemas/EvidencePolicy"
                or set(ratification_action.get("required", [])) != {"expectedPackageVersion"}
                or set(ratification_action.get("properties", {})) != {"expectedPackageVersion"}
                or ratification_action.get("additionalProperties") is not False
                or ratification_action.get("properties", {}).get("expectedPackageVersion", {}).get("maximum") != 9007199254740991
                or set(ratification_snapshot_v1.get("required", [])) != ratification_required_fields["RatificationPackageSnapshotV1"]
                or set(ratification_snapshot_v1.get("properties", {})) != ratification_closed_fields["RatificationPackageSnapshotV1"]
                or ratification_snapshot_v1.get("additionalProperties") is not False
                or ratification_snapshot_v1.get("properties", {}).get("schemaVersion", {}).get("const") != 1
                or set(ratification_snapshot_v2.get("required", [])) != ratification_required_fields["RatificationPackageSnapshotV2"]
                or set(ratification_snapshot_v2.get("properties", {})) != ratification_closed_fields["RatificationPackageSnapshotV2"]
                or ratification_snapshot_v2.get("additionalProperties") is not False
                or ratification_snapshot_v2.get("properties", {}).get("schemaVersion", {}).get("const") != 2
                or ratification_snapshot_v2.get("properties", {}).get("disputeWindowDays", {}).get("minimum") != 0
                or ratification_snapshot_v2.get("properties", {}).get("disputeWindowDays", {}).get("maximum") != 365
                or set(ratification_snapshot_v3.get("required", [])) != ratification_required_fields["RatificationPackageSnapshotV3"]
                or set(ratification_snapshot_v3.get("properties", {})) != ratification_closed_fields["RatificationPackageSnapshotV3"]
                or ratification_snapshot_v3.get("additionalProperties") is not False
                or ratification_snapshot_v3.get("properties", {}).get("schemaVersion", {}).get("const") != 3
                or ratification_snapshot_v3.get("properties", {}).get("disputeWindowDays", {}).get("minimum") != 0
                or ratification_snapshot_v3.get("properties", {}).get("disputeWindowDays", {}).get("maximum") != 365
                or ratification_snapshot_v3.get("properties", {}).get("evidencePolicy", {}).get("$ref")
                != "#/components/schemas/EvidencePolicy"
                or snapshot_v1_rules != "#/components/schemas/RatificationSnapshotRuleSet"
                or snapshot_v2_rules != "#/components/schemas/RatificationSnapshotRuleSet"
                or snapshot_v3_rules != "#/components/schemas/RatificationSnapshotRuleSet"
                or ratification_snapshot_v1.get("properties", {}).get("dealId", {}).get("pattern") != lowercase_uuid_pattern
                or ratification_snapshot_v2.get("properties", {}).get("dealId", {}).get("pattern") != lowercase_uuid_pattern
                or ratification_snapshot_v3.get("properties", {}).get("dealId", {}).get("pattern") != lowercase_uuid_pattern
                or ratification_snapshot_party.get("properties", {}).get("legalEntityId", {}).get("pattern") != lowercase_uuid_pattern
                or ratification_snapshot_rule_set.get("properties", {}).get("ruleSetVersionId", {}).get("pattern") != lowercase_uuid_pattern
                or ratification_snapshot_document.get("properties", {}).get("documentId", {}).get("pattern") != lowercase_uuid_pattern
                or ratification_snapshot_rule_set.get("properties", {}).get("rules", {}).get("uniqueItems") is not True
                or "UTF-8 bytewise" not in ratification_snapshot_rule_set.get("properties", {}).get("rules", {}).get("description", "")
                or "sole contentHash input" not in ratification_snapshot_v1.get("description", "")
                or "RFC 8785" not in ratification_snapshot_v1.get("description", "")
                or "excludes" not in ratification_snapshot_v1.get("description", "")
                or "RFC 8785" not in ratification_snapshot_v2.get("description", "")
                or "RFC 8785" not in ratification_snapshot_v3.get("description", "")
                or snapshot_discriminator.get("propertyName") != "schemaVersion"
                or snapshot_discriminator.get("mapping", {}).get("1") != "#/components/schemas/RatificationPackageSnapshotV1"
                or snapshot_discriminator.get("mapping", {}).get("2") != "#/components/schemas/RatificationPackageSnapshotV2"
                or snapshot_discriminator.get("mapping", {}).get("3") != "#/components/schemas/RatificationPackageSnapshotV3"
                or {ref.get("$ref") for ref in snapshot_one_of}
                != {
                    "#/components/schemas/RatificationPackageSnapshotV1",
                    "#/components/schemas/RatificationPackageSnapshotV2",
                    "#/components/schemas/RatificationPackageSnapshotV3",
                }
                or set(ratification_detail.get("required", [])) != {"id", "version", "status", "contentHash", "snapshot", "approvals", "availableActions", "createdAt"}
                or ratification_detail.get("additionalProperties") is not False
                or ratification_detail.get("properties", {}).get("contentHash", {}).get("pattern") != "^[a-f0-9]{64}$"
                or ratification_detail.get("properties", {}).get("snapshot", {}).get("$ref") != "#/components/schemas/RatificationPackageSnapshot"
                or set(ratification_approval.get("required", [])) != {"legalEntityId", "legalName", "status", "approvedAt", "approverUserId"}
                or ratification_approval.get("additionalProperties") is not False
                or "RATIFICATION_STALE_PACKAGE" not in ratification_conflict.get("description", "")
                or "RATIFICATION_PACKAGE_STATE_CONFLICT" not in ratification_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in ratification_conflict.get("description", "")
                or "DEAL_STALE_VERSION" not in ratification_create_conflict.get("description", "")
                or "RATIFICATION_NOT_READY" not in ratification_create_conflict.get("description", "")):
            failures.append("FAIL Core API Slice 10 ratification: closed snapshot, exact terms, actions, or stable conflicts changed")
        for schema_name, expected_fields in ratification_closed_fields.items():
            schema = core_components.get("schemas", {}).get(schema_name, {})
            if (schema.get("additionalProperties") is not False
                    or set(schema.get("properties", {})) != expected_fields
                    or set(schema.get("required", [])) != ratification_required_fields[schema_name]):
                failures.append(f"FAIL Core API Slice 10 {schema_name}: closed field set changed")

        funding_status = core_components.get("schemas", {}).get("FundingStatus", {})
        funding_unit_status = core_components.get("schemas", {}).get("FundingUnitStatus", {})
        payment_operation_status = core_components.get("schemas", {}).get("PaymentOperationStatus", {})
        funding_plan_detail = core_components.get("schemas", {}).get("FundingPlanDetail", {})
        funding_unit = core_components.get("schemas", {}).get("FundingUnit", {})
        payment_operation = core_components.get("schemas", {}).get("PaymentOperation", {})
        deal_funding_summary = core_components.get("schemas", {}).get("DealFundingSummary", {})
        create_funding_plan = core_components.get("schemas", {}).get("CreateFundingPlanRequest", {})
        initiate_payment_operation = core_components.get("schemas", {}).get("InitiatePaymentOperationRequest", {})
        reconcile_payment_operation = core_components.get("schemas", {}).get("ReconcilePaymentOperationRequest", {})
        funding_mutation_forbidden = core_components.get("responses", {}).get("FundingMutationForbidden", {})
        funding_plan_create_conflict = core_components.get("responses", {}).get("FundingPlanCreateConflict", {})
        payment_operation_initiate_conflict = core_components.get("responses", {}).get("PaymentOperationInitiateConflict", {})
        payment_operation_reconcile_conflict = core_components.get("responses", {}).get("PaymentOperationReconcileConflict", {})
        funding_closed_fields = {
            "FundingUnitAvailableActions": {"canInitiatePayment"},
            "PaymentOperationAvailableActions": {"canReconcile"},
            "PaymentOperation": {"id", "fundingUnitId", "status", "reconciliationRequired", "providerReference", "version", "availableActions", "createdAt", "updatedAt"},
            "FundingUnit": {"id", "sequenceNo", "amountMinor", "currency", "status", "version", "currentOperation", "availableActions", "createdAt", "updatedAt"},
            "FundingPlanDetail": {"id", "dealId", "amountMinor", "currency", "fundingStatus", "version", "fundingUnit", "createdAt", "updatedAt"},
            "DealFundingSummary": {"fundingStatus", "fundingPlanId", "amountMinor", "currency"},
            "CreateFundingPlanRequest": {"expectedVersion"},
            "InitiatePaymentOperationRequest": {"expectedVersion"},
            "ReconcilePaymentOperationRequest": {"expectedVersion"},
        }
        # Additive, optional-only fields layered onto the Slice 11 closed sets by the
        # 2026-07-22 simulation-only decision §2 / ADR-014 §2.1 visible-mode labeling
        # requirement. Never required, so older consumers reading the closed Slice 11
        # field set remain unaffected.
        funding_optional_fields = {
            "PaymentOperation": {"mode"},
            "FundingPlanDetail": {"mode"},
        }
        for schema_name, expected_fields in funding_closed_fields.items():
            schema = core_components.get("schemas", {}).get(schema_name, {})
            optional_fields = funding_optional_fields.get(schema_name, set())
            if (schema.get("additionalProperties") is not False
                    or set(schema.get("properties", {})) != expected_fields | optional_fields
                    or set(schema.get("required", [])) != expected_fields):
                failures.append(f"FAIL Core API Slice 11 {schema_name}: closed field set changed")
        if (funding_status.get("enum") != ["NOT_CONFIGURED", "PLANNED", "PENDING", "PARTIALLY_FUNDED", "FUNDED"]
                or funding_unit_status.get("enum") != ["PLANNED", "PENDING", "FUNDED", "FAILED"]
                or payment_operation_status.get("enum") != ["CREATED", "SUCCEEDED", "DECLINED", "UNCONFIRMED"]
                or "unreachable in V1" not in funding_status.get("description", "")
                or funding_unit.get("properties", {}).get("amountMinor", {}).get("minimum") != 1
                or funding_unit.get("properties", {}).get("amountMinor", {}).get("maximum") != 9007199254740991
                or funding_unit.get("properties", {}).get("currency", {}).get("pattern") != "^[A-Z]{3}$"
                or funding_unit.get("properties", {}).get("currentOperation", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/PaymentOperation"}, {"type": "null"}]
                or funding_plan_detail.get("properties", {}).get("fundingUnit", {}).get("$ref")
                != "#/components/schemas/FundingUnit"
                or funding_plan_detail.get("properties", {}).get("fundingStatus", {}).get("$ref")
                != "#/components/schemas/FundingStatus"
                or payment_operation.get("properties", {}).get("reconciliationRequired", {}).get("type") != "boolean"
                or payment_operation.get("properties", {}).get("providerReference", {}).get("anyOf", [{}])[-1]
                != {"type": "null"}
                or create_funding_plan.get("properties", {}).get("expectedVersion", {}).get("maximum") != 9007199254740991
                or initiate_payment_operation.get("properties", {}).get("expectedVersion", {}).get("maximum") != 9007199254740991
                or reconcile_payment_operation.get("properties", {}).get("expectedVersion", {}).get("maximum") != 9007199254740991
                or "FUNDING_MUTATION_FORBIDDEN" not in funding_mutation_forbidden.get("description", "")
                or "DEAL_STALE_VERSION" not in funding_plan_create_conflict.get("description", "")
                or "DEAL_STATE_CONFLICT" not in funding_plan_create_conflict.get("description", "")
                or "FUNDING_PLAN_ALREADY_EXISTS" not in funding_plan_create_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in funding_plan_create_conflict.get("description", "")
                or "FUNDING_UNIT_STALE_VERSION" not in payment_operation_initiate_conflict.get("description", "")
                or "FUNDING_UNIT_ALREADY_FUNDED" not in payment_operation_initiate_conflict.get("description", "")
                or "PAYMENT_OPERATION_IN_FLIGHT" not in payment_operation_initiate_conflict.get("description", "")
                or "PAYMENT_OPERATION_STALE_VERSION" not in payment_operation_reconcile_conflict.get("description", "")
                or "PAYMENT_OPERATION_STATE_CONFLICT" not in payment_operation_reconcile_conflict.get("description", "")
                or deal_funding_summary.get("properties", {}).get("fundingStatus", {}).get("$ref")
                != "#/components/schemas/FundingStatus"
                or deal_funding_summary.get("properties", {}).get("fundingPlanId", {}).get("anyOf", [{}])[-1]
                != {"type": "null"}
                or deal_funding_summary.get("properties", {}).get("amountMinor", {}).get("anyOf", [{}])[-1]
                != {"type": "null"}
                or deal_funding_summary.get("properties", {}).get("currency", {}).get("anyOf", [{}])[-1]
                != {"type": "null"}):
            failures.append("FAIL Core API Slice 11 funding: closed enums, projections, requests, or stable conflicts changed")

        analysis_status = core_components.get("schemas", {}).get("DocumentAnalysisStatus", {})
        analysis_summary = core_components.get("schemas", {}).get("DealDocumentAnalysisSummary", {})
        analysis = core_components.get("schemas", {}).get("DealDocumentAnalysis", {})
        analysis_conflict = core_components.get("responses", {}).get("DealDocumentAnalysisRequestConflict", {})
        if (analysis_status.get("enum") != ["NOT_REQUESTED", "QUEUED", "PROCESSING", "REVIEW_REQUIRED", "ACCEPTED", "FAILED"]
                or set(analysis_summary.get("required", [])) != {"currentDocumentId", "status", "requestedAt", "processingStartedAt", "completedAt", "failedAt", "failure"}
                or analysis.get("properties", {}).get("result", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/DocumentAnalysisResult"}, {"type": "null"}]
                or "DEAL_STATE_CONFLICT" not in analysis_conflict.get("description", "")
                or "DEAL_DOCUMENT_ANALYSIS_DOCUMENT_NOT_AVAILABLE" not in analysis_conflict.get("description", "")
                or "DEAL_DOCUMENT_ANALYSIS_ACTIVE_JOB_EXISTS" not in analysis_conflict.get("description", "")):
            failures.append("FAIL Core API document analysis: state, result, or stable conflict codes changed")

        review_request = core_components.get("schemas", {}).get("AcceptExtractionReviewRequest", {})
        review_decision = core_components.get("schemas", {}).get("ReviewRuleDecision", {})
        modified_decision = core_components.get("schemas", {}).get("ModifiedRuleDecision", {})
        added_decision = core_components.get("schemas", {}).get("AddedRuleDecision", {})
        rule_set_rule = core_components.get("schemas", {}).get("RuleSetRule", {})
        rule_set_value = core_components.get("schemas", {}).get("RuleSetStructuredValue", {})
        extracted_rule_value = core_components.get("schemas", {}).get("ExtractedRuleValue", {})
        rule_set_summary = core_components.get("schemas", {}).get("RuleSetVersionSummary", {})
        rule_set = core_components.get("schemas", {}).get("RuleSetVersion", {})
        review_conflict = core_components.get("responses", {}).get("DealReviewAcceptanceConflict", {})
        if (set(review_request.get("required", [])) != {"analysisId", "expectedVersion", "decisions"}
                or set(review_request.get("properties", {})) != {"analysisId", "expectedVersion", "decisions"}
                or review_request.get("additionalProperties") is not False
                or review_request.get("properties", {}).get("analysisId", {}).get("format") != "uuid"
                or review_request.get("properties", {}).get("expectedVersion", {}).get("minimum") != 0
                or review_request.get("properties", {}).get("decisions", {}).get("items", {}).get("$ref")
                != "#/components/schemas/ReviewRuleDecision"
                or "minItems" in review_request.get("properties", {}).get("decisions", {})
                or review_decision.get("discriminator", {}).get("propertyName") != "decision"
                or set(review_decision.get("discriminator", {}).get("mapping", {})) != {"KEPT", "MODIFIED", "EXCLUDED", "ADDED"}
                or set(modified_decision.get("properties", {})) != {"decision", "ruleReference", "category", "title", "description", "structuredValue"}
                or set(added_decision.get("properties", {})) != {"decision", "category", "title", "description", "structuredValue"}
                or modified_decision.get("properties", {}).get("structuredValue", {}).get("$ref") != "#/components/schemas/RuleSetStructuredValue"
                or added_decision.get("properties", {}).get("structuredValue", {}).get("$ref") != "#/components/schemas/RuleSetStructuredValue"
                or rule_set_rule.get("properties", {}).get("structuredValue", {}).get("$ref") != "#/components/schemas/RuleSetStructuredValue"
                or len(rule_set_value.get("oneOf", [])) != 7
                or rule_set_value.get("oneOf", [None, None])[1].get("properties", {}).get("amountMinor") != {"type": "integer", "minimum": 0}
                or rule_set_value.get("oneOf", [None, None, None])[2].get("properties", {}).get("basisPoints") != {"type": "integer", "minimum": 0, "maximum": 10000}
                or extracted_rule_value.get("oneOf", [None, None])[1].get("properties", {}).get("amountMinor") != {"type": "integer"}
                or set(rule_set_summary.get("required", [])) != {"id", "version", "sourceAnalysisId", "sourceExtractionResultVersionId", "createdAt", "createdByUserId", "previousRuleSetVersionId", "ruleCount"}
                or set(rule_set_summary.get("properties", {})) != {"id", "version", "sourceAnalysisId", "sourceExtractionResultVersionId", "createdAt", "createdByUserId", "previousRuleSetVersionId", "ruleCount"}
                or rule_set_summary.get("properties", {}).get("sourceExtractionResultVersionId", {}).get("format") != "uuid"
                or set(rule_set.get("required", [])) != {"id", "version", "sourceAnalysisId", "sourceExtractionResultVersionId", "createdAt", "createdByUserId", "previousRuleSetVersionId", "ruleCount", "rules", "excludedRuleReferences"}
                or set(rule_set.get("properties", {})) != {"id", "version", "sourceAnalysisId", "sourceExtractionResultVersionId", "createdAt", "createdByUserId", "previousRuleSetVersionId", "ruleCount", "rules", "excludedRuleReferences"}
                or rule_set.get("additionalProperties") is not False
                or "DEAL_STALE_VERSION" not in review_conflict.get("description", "")
                or "DEAL_STATE_CONFLICT" not in review_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in review_conflict.get("description", "")):
            failures.append("FAIL Core API Slice 9 review acceptance: decisions, immutable version, or stable conflicts changed")

        create_document_intent = core_components.get("schemas", {}).get("CreateDocumentUploadIntentRequest", {})
        finalize_document = core_components.get("schemas", {}).get("FinalizeDocumentUploadRequest", {})
        if (set(create_document_intent.get("required", [])) != {"fileName", "mediaType", "sizeBytes", "sha256"}
                or set(create_document_intent.get("properties", {})) != {"fileName", "mediaType", "sizeBytes", "sha256"}
                or create_document_intent.get("additionalProperties") is not False
                or create_document_intent.get("properties", {}).get("sizeBytes", {}).get("minimum") != 1
                or "maximum" in create_document_intent.get("properties", {}).get("sizeBytes", {})
                or set(finalize_document.get("required", [])) != {"sizeBytes", "sha256"}
                or set(finalize_document.get("properties", {})) != {"sizeBytes", "sha256"}
                or finalize_document.get("additionalProperties") is not False
                or finalize_document.get("properties", {}).get("sizeBytes", {}).get("minimum") != 1
                or "maximum" in finalize_document.get("properties", {}).get("sizeBytes", {})):
            failures.append("FAIL Core API document upload requests: bounded client metadata or finalize canonical request changed")
        if (core_components.get("schemas", {}).get("DocumentMediaType", {}).get("enum")
                != ["application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"]
                or core_components.get("schemas", {}).get("DocumentStatus", {}).get("enum")
                != ["PENDING_UPLOAD", "AVAILABLE", "SUPERSEDED"]):
            failures.append("FAIL Core API document enums: initial media types or retained state set changed")
        document_actions = core_components.get("schemas", {}).get("DocumentAvailableActions", {})
        pending_document = core_components.get("schemas", {}).get("PendingDealDocument", {})
        available_document = core_components.get("schemas", {}).get("AvailableDealDocument", {})
        historical_document = core_components.get("schemas", {}).get("HistoricalDealDocument", {})
        document_history = core_components.get("schemas", {}).get("DealDocumentHistory", {})
        current_document = detail.get("properties", {}).get("currentDocument", {})
        if (set(document_actions.get("required", [])) != {"canFinalize", "canDownload"}
                or set(document_actions.get("properties", {})) != {"canFinalize", "canDownload"}
                or pending_document.get("properties", {}).get("status", {}).get("const") != "PENDING_UPLOAD"
                or available_document.get("properties", {}).get("status", {}).get("const") != "AVAILABLE"
                or historical_document.get("properties", {}).get("status", {}).get("enum") != ["AVAILABLE", "SUPERSEDED"]
                or document_history.get("properties", {}).get("items", {}).get("items", {}).get("oneOf")
                != [{"$ref": "#/components/schemas/PendingDealDocument"}, {"$ref": "#/components/schemas/HistoricalDealDocument"}]
                or current_document.get("anyOf")
                != [{"$ref": "#/components/schemas/AvailableDealDocument"}, {"type": "null"}]
                or "objectVersion" not in available_document.get("required", [])
                or available_document.get("properties", {}).get("objectVersion", {}).get("minLength") != 1):
            failures.append("FAIL Core API document projections: actor actions, AVAILABLE current/finalize, or immutable history changed")
        deal_page = core_components.get("schemas", {}).get("DealPage", {})
        deal_page_fields = {"items", "page", "size", "totalElements", "totalPages"}
        page_items = deal_page.get("properties", {}).get("items", {})
        if (set(deal_page.get("required", [])) != deal_page_fields
                or set(deal_page.get("properties", {})) != deal_page_fields
                or deal_page.get("additionalProperties") is not False
                or page_items.get("type") != "array"
                or page_items.get("items", {}).get("$ref") != "#/components/schemas/DealSummary"):
            failures.append("FAIL Core API DealPage: stable paginated list DTO changed")

        invitation_status = core_components.get("schemas", {}).get("DealInvitationStatus", {})
        if invitation_status.get("enum") != ["PENDING", "ACCEPTED", "REJECTED", "REVOKED"]:
            failures.append("FAIL Core API DealInvitationStatus: closed Slice 4 state set changed")
        create_invitation = core_components.get("schemas", {}).get("CreateDealInvitationRequest", {})
        create_invitation_email = create_invitation.get("properties", {}).get("recipientEmail", {})
        if (set(create_invitation.get("required", [])) != {"recipientEmail"}
                or set(create_invitation.get("properties", {})) != {"recipientEmail"}
                or create_invitation.get("additionalProperties") is not False
                or (create_invitation_email.get("format"), create_invitation_email.get("minLength"),
                    create_invitation_email.get("maxLength")) != ("email", 3, 320)):
            failures.append("FAIL Core API CreateDealInvitationRequest: normalized-email request contract changed")
        accept_invitation = core_components.get("schemas", {}).get("AcceptDealInvitationRequest", {})
        accept_properties = accept_invitation.get("properties", {})
        terminal_invitation = core_components.get("schemas", {}).get("DealInvitationTerminalActionRequest", {})
        if (set(accept_invitation.get("required", [])) != {"legalEntityId", "expectedVersion"}
                or set(accept_properties) != {"legalEntityId", "expectedVersion"}
                or accept_invitation.get("additionalProperties") is not False
                or accept_properties.get("legalEntityId", {}).get("format") != "uuid"
                or accept_properties.get("expectedVersion", {}).get("minimum") != 0
                or set(terminal_invitation.get("required", [])) != {"expectedVersion"}
                or set(terminal_invitation.get("properties", {})) != {"expectedVersion"}
                or terminal_invitation.get("additionalProperties") is not False
                or terminal_invitation.get("properties", {}).get("expectedVersion", {}).get("minimum") != 0):
            failures.append("FAIL Core API invitation terminal actions: expectedVersion contract changed")
        invitation_actions = core_components.get("schemas", {}).get("DealInvitationAvailableActions", {})
        if (set(invitation_actions.get("required", [])) != {"canAccept", "canReject", "canRevoke"}
                or set(invitation_actions.get("properties", {})) != {"canAccept", "canReject", "canRevoke"}
                or invitation_actions.get("additionalProperties") is not False
                or any(invitation_actions.get("properties", {}).get(name, {}).get("type") != "boolean"
                       for name in ("canAccept", "canReject", "canRevoke"))):
            failures.append("FAIL Core API DealInvitationAvailableActions: actor-aware action set changed")
        invitation = core_components.get("schemas", {}).get("DealInvitation", {})
        incoming_invitation = core_components.get("schemas", {}).get("IncomingDealInvitation", {})
        invitation_deal = core_components.get("schemas", {}).get("DealInvitationDeal", {})
        invitation_deal_fields = {"id", "reference", "title", "initiatorLegalName"}
        invitation_fields = {"id", "dealId", "recipientEmail", "status", "version", "createdAt", "updatedAt", "availableActions"}
        incoming_fields = {"id", "deal", "status", "version", "createdAt", "updatedAt", "availableActions"}
        if (set(invitation_deal.get("required", [])) != invitation_deal_fields
                or set(invitation_deal.get("properties", {})) != invitation_deal_fields
                or invitation_deal.get("additionalProperties") is not False
                or invitation_deal.get("properties", {}).get("initiatorLegalName", {}).get("type") != "string"
                or (invitation_deal.get("properties", {}).get("initiatorLegalName", {}).get("minLength"),
                    invitation_deal.get("properties", {}).get("initiatorLegalName", {}).get("maxLength")) != (1, 200)
                or set(incoming_invitation.get("properties", {}).get("deal", {})) != {"$ref"}
                or incoming_invitation.get("properties", {}).get("deal", {}).get("$ref")
                != "#/components/schemas/DealInvitationDeal"):
            failures.append("FAIL Core API DealInvitationDeal: bounded recipient preview changed")
        if (set(invitation.get("required", [])) != invitation_fields
                or set(invitation.get("properties", {})) != invitation_fields
                or invitation.get("additionalProperties") is not False
                or invitation.get("properties", {}).get("recipientEmail", {}).get("format") != "email"
                or set(incoming_invitation.get("required", [])) != incoming_fields
                or set(incoming_invitation.get("properties", {})) != incoming_fields
                or incoming_invitation.get("additionalProperties") is not False
                or "recipientEmail" in incoming_invitation.get("properties", {})):
            failures.append("FAIL Core API invitation projections: recipient-email disclosure boundary changed")
        for page_schema_name, item_schema_name in (
                ("DealInvitationPage", "DealInvitation"),
                ("IncomingDealInvitationPage", "IncomingDealInvitation")):
            page_schema = core_components.get("schemas", {}).get(page_schema_name, {})
            page_properties = page_schema.get("properties", {})
            if (set(page_schema.get("required", [])) != deal_page_fields
                    or set(page_properties) != deal_page_fields
                    or page_schema.get("additionalProperties") is not False
                    or page_properties.get("items", {}).get("type") != "array"
                    or page_properties.get("items", {}).get("items", {}).get("$ref")
                    != f"#/components/schemas/{item_schema_name}"):
                failures.append(f"FAIL Core API {page_schema_name}: stable paginated invitation DTO changed")
        utc_timestamp = core_components.get("schemas", {}).get("UtcTimestamp", {})
        if (utc_timestamp.get("type") != "string"
                or utc_timestamp.get("format") != "date-time"
                or not str(utc_timestamp.get("pattern", "")).endswith("Z$")):
            failures.append("FAIL Core API UtcTimestamp: RFC 3339 UTC Z contract changed")

        security_schemes = core_components.get("securitySchemes", {})
        session_cookie = security_schemes.get("SessionCookie", {})
        csrf_token = security_schemes.get("CsrfToken", {})
        if (set(security_schemes) != {"SessionCookie", "CsrfToken"}
                or (session_cookie.get("type"), session_cookie.get("in"), session_cookie.get("name"))
                != ("apiKey", "cookie", "__Host-M4TRUST_SESSION")
                or (csrf_token.get("type"), csrf_token.get("in"), csrf_token.get("name"))
                != ("apiKey", "header", "X-CSRF-TOKEN")):
            failures.append("FAIL Core API security schemes: session cookie or CSRF contract changed")
        core_parameters = core_components.get("parameters", {})
        legal_entity_context = core_parameters.get("LegalEntityContext", {})
        if (legal_entity_context.get("name") != "X-M4Trust-Legal-Entity-Id"
                or legal_entity_context.get("in") != "header"
                or legal_entity_context.get("required") is not True
                or legal_entity_context.get("schema", {}).get("format") != "uuid"):
            failures.append("FAIL Core API LegalEntityContext: required UUID header contract changed")
        for scoped_path in (
                "/legal-entities/{legalEntityId}",
                "/legal-entities/{legalEntityId}/members"):
            parameter_refs = {
                parameter.get("$ref")
                for parameter in core_paths.get(scoped_path, {}).get("get", {}).get("parameters", [])
                if isinstance(parameter, dict)
            }
            if parameter_refs != {
                    "#/components/parameters/LegalEntityId",
                    "#/components/parameters/LegalEntityContext"}:
                failures.append(f"FAIL Core API legal entity scope parameters: GET {scoped_path}")
        for unscoped_method in ("get", "post"):
            if core_paths.get("/legal-entities", {}).get(unscoped_method, {}).get("parameters"):
                failures.append(
                    f"FAIL Core API legal entity bootstrap: {unscoped_method.upper()} /legal-entities "
                    "must not require active legal entity context"
                )
        expected_deal_parameter_refs = {
            ("/deals", "post"): {"#/components/parameters/LegalEntityContext"},
            ("/deals", "get"): {
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/DealStatusFilter",
                "#/components/parameters/Page",
                "#/components/parameters/PageSize",
                "#/components/parameters/DealSort",
            },
            ("/deals/{dealId}", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}", "patch"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/cancel", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/documents/upload-intents", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/documents", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/document-analysis", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/document-analysis", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/extraction-review", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/extraction-review/accept", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/rule-set-versions", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/rule-set-versions/{ruleSetVersionId}", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/RuleSetVersionId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/ratification-packages", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/ratification-packages", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/ratification-packages/{ratificationPackageId}", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/RatificationPackageId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/RatificationPackageId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/RatificationPackageId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/funding-plan", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/funding-plan", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/funding-units/{fundingUnitId}/payment-operations", "post"): {
                "#/components/parameters/FundingUnitId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/payment-operations/{paymentOperationId}", "get"): {
                "#/components/parameters/PaymentOperationId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/payment-operations/{paymentOperationId}/reconcile", "post"): {
                "#/components/parameters/PaymentOperationId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/documents/{documentId}/finalize", "post"): {
                "#/components/parameters/DocumentId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/documents/{documentId}/download-link", "post"): {
                "#/components/parameters/DocumentId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/fulfillment", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/fulfillment", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/fulfillment/evidence/upload-intents", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/EvidenceSubmissionId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/EvidenceSubmissionId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/download-link", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/EvidenceSubmissionId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/EvidenceSubmissionId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/EvidenceSubmissionId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/fulfillment/accept-without-evidence", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/EvidenceSubmissionId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/EvidenceSubmissionId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/disputes", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/disputes", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/Page",
                "#/components/parameters/PageSize",
                "#/components/parameters/DisputeSort",
            },
            ("/deals/{dealId}/disputes/{disputeId}", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/DisputeId",
                "#/components/parameters/LegalEntityContext",
            },
            ("/deals/{dealId}/disputes/{disputeId}/comments", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/DisputeId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/Page",
                "#/components/parameters/PageSize",
                "#/components/parameters/DisputeCommentSort",
            },
            ("/deals/{dealId}/disputes/{disputeId}/comments", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/DisputeId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/disputes/{disputeId}/acknowledge", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/DisputeId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/disputes/{disputeId}/withdraw", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/DisputeId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/invitations", "post"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/deals/{dealId}/invitations", "get"): {
                "#/components/parameters/DealId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/Page",
                "#/components/parameters/PageSize",
            },
            ("/deal-invitations/incoming", "get"): {
                "#/components/parameters/Page",
                "#/components/parameters/PageSize",
            },
            ("/deal-invitations/{invitationId}/accept", "post"): {
                "#/components/parameters/InvitationId",
            },
            ("/deal-invitations/{invitationId}/reject", "post"): {
                "#/components/parameters/InvitationId",
            },
            ("/deal-invitations/{invitationId}/revoke", "post"): {
                "#/components/parameters/InvitationId",
                "#/components/parameters/LegalEntityContext",
            },
        }
        for (path, method), expected_refs in expected_deal_parameter_refs.items():
            parameter_refs = {
                parameter.get("$ref")
                for parameter in core_paths.get(path, {}).get(method, {}).get("parameters", [])
                if isinstance(parameter, dict)
            }
            if parameter_refs != expected_refs:
                failures.append(f"FAIL Core API Deal scope/query parameters: {method.upper()} {path}")
        deal_sort = core_parameters.get("DealSort", {}).get("schema", {})
        if (deal_sort.get("default") != "createdAt,desc"
                or deal_sort.get("enum")
                != ["createdAt,asc", "createdAt,desc", "title,asc", "title,desc"]):
            failures.append("FAIL Core API DealSort: single allowlisted sort contract changed")
        page_parameter = core_parameters.get("Page", {}).get("schema", {})
        page_size_parameter = core_parameters.get("PageSize", {}).get("schema", {})
        if (page_parameter.get("default"), page_parameter.get("minimum")) != (0, 0):
            failures.append("FAIL Core API Page: zero-based default changed")
        if ((page_size_parameter.get("default"), page_size_parameter.get("minimum"),
                page_size_parameter.get("maximum")) != (20, 1, 100)):
            failures.append("FAIL Core API PageSize: default or bounds changed")
        deal_mutation_forbidden = core_components.get("responses", {}).get("DealScopedMutationForbidden", {})
        if "DEAL_MUTATION_FORBIDDEN" not in deal_mutation_forbidden.get("description", ""):
            failures.append("FAIL Core API DealScopedMutationForbidden: visible non-initiator code missing")
        cancel_operation = core_paths.get("/deals/{dealId}/cancel", {}).get("post", {})
        if "requestBody" in cancel_operation:
            failures.append("FAIL Core API cancel Deal: business action must remain bodyless")
        deal_location = (core_paths.get("/deals", {}).get("post", {}).get("responses", {})
                .get("201", {}).get("headers", {}).get("Location", {}))
        if deal_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API create Deal: 201 Location header is required")
        invitation_location = (core_paths.get("/deals/{dealId}/invitations", {}).get("post", {}).get("responses", {})
                .get("201", {}).get("headers", {}).get("Location", {}))
        if invitation_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API create invitation: 201 Location header is required")
        idempotency_key = core_parameters.get("IdempotencyKey", {})
        if ((idempotency_key.get("name"), idempotency_key.get("in"), idempotency_key.get("required"),
                idempotency_key.get("schema", {}).get("format")) != ("Idempotency-Key", "header", True, "uuid")):
            failures.append("FAIL Core API IdempotencyKey: required UUID header contract changed")
        ratification_package_id = core_parameters.get("RatificationPackageId", {})
        if (ratification_package_id.get("name"), ratification_package_id.get("in"),
                ratification_package_id.get("required"), ratification_package_id.get("schema", {}).get("format")) != (
                "ratificationPackageId", "path", True, "uuid"):
            failures.append("FAIL Core API RatificationPackageId: required UUID path contract changed")
        document_id = core_parameters.get("DocumentId", {})
        if ((document_id.get("name"), document_id.get("in"), document_id.get("required"),
                document_id.get("schema", {}).get("format")) != ("documentId", "path", True, "uuid")):
            failures.append("FAIL Core API DocumentId: required UUID path contract changed")
        rule_set_version_id = core_parameters.get("RuleSetVersionId", {})
        if ((rule_set_version_id.get("name"), rule_set_version_id.get("in"), rule_set_version_id.get("required"),
                rule_set_version_id.get("schema", {}).get("format")) != ("ruleSetVersionId", "path", True, "uuid")):
            failures.append("FAIL Core API RuleSetVersionId: required UUID path contract changed")
        funding_unit_id = core_parameters.get("FundingUnitId", {})
        if ((funding_unit_id.get("name"), funding_unit_id.get("in"), funding_unit_id.get("required"),
                funding_unit_id.get("schema", {}).get("format")) != ("fundingUnitId", "path", True, "uuid")):
            failures.append("FAIL Core API FundingUnitId: required UUID path contract changed")
        payment_operation_id = core_parameters.get("PaymentOperationId", {})
        if ((payment_operation_id.get("name"), payment_operation_id.get("in"), payment_operation_id.get("required"),
                payment_operation_id.get("schema", {}).get("format")) != ("paymentOperationId", "path", True, "uuid")):
            failures.append("FAIL Core API PaymentOperationId: required UUID path contract changed")
        evidence_submission_id = core_parameters.get("EvidenceSubmissionId", {})
        if ((evidence_submission_id.get("name"), evidence_submission_id.get("in"), evidence_submission_id.get("required"),
                evidence_submission_id.get("schema", {}).get("format")) != ("evidenceSubmissionId", "path", True, "uuid")):
            failures.append("FAIL Core API EvidenceSubmissionId: required UUID path contract changed")
        funding_plan_create_location = (core_paths.get("/deals/{dealId}/funding-plan", {}).get("post", {}).get("responses", {})
                .get("201", {}).get("headers", {}).get("Location", {}))
        if funding_plan_create_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API create funding plan: 201 Location header is required")
        initiate_payment_location = (core_paths.get("/funding-units/{fundingUnitId}/payment-operations", {}).get("post", {}).get("responses", {})
                .get("202", {}).get("headers", {}).get("Location", {}))
        if initiate_payment_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API initiate payment operation: 202 Location header is required")
        reconcile_payment_location = (core_paths.get("/payment-operations/{paymentOperationId}/reconcile", {}).get("post", {}).get("responses", {})
                .get("202", {}).get("headers", {}).get("Location", {}))
        if reconcile_payment_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API reconcile payment operation: 202 Location header is required")
        funding_plan_create_op = core_paths.get("/deals/{dealId}/funding-plan", {}).get("post", {})
        if "requestBody" not in funding_plan_create_op:
            failures.append("FAIL Core API create funding plan: requestBody with expectedVersion is required")
        funding_plan_create_description = funding_plan_create_op.get("description", "")
        if ("IDEMPOTENCY_KEY_REUSED" not in funding_plan_create_description
                or "never client-supplied" not in funding_plan_create_description):
            failures.append("FAIL Core API create funding plan: idempotency or server-copied-amount semantics missing")
        initiate_payment_description = core_paths.get("/funding-units/{fundingUnitId}/payment-operations", {}).get("post", {}).get("description", "")
        if ("provider is never called within the request" not in initiate_payment_description):
            failures.append("FAIL Core API initiate payment operation: provider-outside-request semantics missing")
        reconcile_payment_description = core_paths.get("/payment-operations/{paymentOperationId}/reconcile", {}).get("post", {}).get("description", "")
        if ("never calls the provider within the request" not in reconcile_payment_description):
            failures.append("FAIL Core API reconcile payment operation: provider-outside-request semantics missing")
        finalize_description = core_paths.get("/documents/{documentId}/finalize", {}).get("post", {}).get("description", "")
        if "IDEMPOTENCY_KEY_REUSED" not in finalize_description or "same canonical request" not in finalize_description:
            failures.append("FAIL Core API document finalize: replay and different-request idempotency semantics missing")
        review_accept_description = core_paths.get("/deals/{dealId}/extraction-review/accept", {}).get("post", {}).get("description", "")
        review_accept_location = (core_paths.get("/deals/{dealId}/extraction-review/accept", {}).get("post", {}).get("responses", {})
                .get("201", {}).get("headers", {}).get("Location", {}))
        if ("IDEMPOTENCY_KEY_REUSED" not in review_accept_description
                or "current REVIEW_REQUIRED analysis" not in review_accept_description
                or review_accept_location.get("schema", {}).get("format") != "uri-reference"):
            failures.append("FAIL Core API review acceptance: idempotency, target-state, or 201 Location contract changed")

        fulfillment_status = core_components.get("schemas", {}).get("FulfillmentStatus", {})
        evidence_type = core_components.get("schemas", {}).get("EvidenceType", {})
        evidence_media_type = core_components.get("schemas", {}).get("EvidenceMediaType", {})
        evidence_submission_status = core_components.get("schemas", {}).get("EvidenceSubmissionStatus", {})
        if (fulfillment_status.get("enum") != ["NOT_STARTED", "IN_PROGRESS", "EVIDENCE_REQUIRED", "REVIEW_REQUIRED", "COMPLETED", "CANCELLED"]
                or evidence_type.get("enum") != ["DELIVERY_NOTE", "INVOICE", "VIDEO", "PHOTO", "SIGNED_DOCUMENT", "OTHER"]
                or evidence_media_type.get("enum") != [
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "image/jpeg",
                    "image/png",
                    "video/mp4",
                ]
                or evidence_submission_status.get("enum") != ["PENDING_UPLOAD", "SUBMITTED", "ACCEPTED", "REJECTED"]):
            failures.append("FAIL Core API Slice 12 fulfillment: closed status/type/media enums changed")
        fulfillment_closed_fields = {
            "StartFulfillmentRequest": {"expectedVersion"},
            "CreateEvidenceUploadIntentRequest": {"evidenceType", "mediaType", "fileName", "sizeBytes", "sha256"},
            "FinalizeEvidenceUploadRequest": {"sizeBytes", "sha256"},
            "CancelEvidenceUploadRequest": {"expectedEvidenceVersion"},
            "AcceptEvidenceRequest": {"expectedVersion", "expectedEvidenceVersion"},
            "AcceptWithoutEvidenceRequest": {"expectedDealVersion", "expectedFulfillmentVersion"},
            "RejectEvidenceRequest": {"expectedVersion", "expectedEvidenceVersion", "reason"},
            "MilestoneAvailableActions": {"canUpload"},
            "DealFulfillmentSummary": {"status", "fulfillmentId", "currentEvidenceSubmissionId", "evidencePolicy"},
            "FulfillmentDetail": {"id", "dealId", "status", "sourcePackageId", "evidencePolicy", "milestone", "currentEvidence", "history", "availableActions", "version", "createdAt", "updatedAt"},
            "FulfillmentMilestone": {"id", "title", "description", "ruleReferences", "availableActions", "version"},
            "MilestoneRuleReference": {"ruleReference", "category"},
            "EvidenceUploadIntent": {"evidence", "uploadUrl", "uploadHeaders", "expiresAt"},
            "EvidenceDownloadLink": {"evidenceSubmissionId", "objectVersion", "downloadUrl", "expiresAt"},
        }
        for schema_name, expected_fields in fulfillment_closed_fields.items():
            schema = core_components.get("schemas", {}).get(schema_name, {})
            if (schema.get("additionalProperties") is not False
                    or set(schema.get("properties", {})) != expected_fields
                    or set(schema.get("required", [])) != expected_fields):
                failures.append(f"FAIL Core API Slice 12 {schema_name}: closed field set changed")
        evidence_actions = core_components.get("schemas", {}).get("EvidenceAvailableActions", {})
        if (set(evidence_actions.get("required", [])) != {"canDownload"}
                or set(evidence_actions.get("properties", {}))
                != {"canDownload", "canCancelUpload"}
                or evidence_actions.get("additionalProperties") is not False
                or any(
                    evidence_actions.get("properties", {}).get(name, {}).get("type") != "boolean"
                    for name in ("canDownload", "canCancelUpload")
                )):
            failures.append("FAIL Core API Slice 12 EvidenceAvailableActions: closed action set changed")
        pending_evidence = core_components.get("schemas", {}).get("PendingEvidenceSubmission", {})
        if "cancelledAt" not in set(pending_evidence.get("properties", {})):
            failures.append("FAIL Core API Plan 18C PendingEvidenceSubmission: cancelledAt required")
        fulfillment_actions = core_components.get("schemas", {}).get("FulfillmentAvailableActions", {})
        if (set(fulfillment_actions.get("required", [])) != {"canStart", "canAccept", "canReject"}
                or set(fulfillment_actions.get("properties", {}))
                != {"canStart", "canAccept", "canReject", "canAcceptWithoutEvidence"}
                or fulfillment_actions.get("additionalProperties") is not False
                or any(
                    fulfillment_actions.get("properties", {}).get(name, {}).get("type") != "boolean"
                    for name in ("canStart", "canAccept", "canReject", "canAcceptWithoutEvidence")
                )):
            failures.append("FAIL Core API Slice 12 FulfillmentAvailableActions: closed action set changed")
        if (core_components.get("schemas", {}).get("DealFulfillmentSummary", {})
                .get("properties", {}).get("evidencePolicy", {}).get("$ref")
                != "#/components/schemas/EvidencePolicy"
                or core_components.get("schemas", {}).get("FulfillmentDetail", {})
                .get("properties", {}).get("evidencePolicy", {}).get("$ref")
                != "#/components/schemas/EvidencePolicy"):
            failures.append("FAIL Core API Slice 12 fulfillment: evidencePolicy must $ref EvidencePolicy")
        evidence_submission = core_components.get("schemas", {}).get("EvidenceSubmission", {})
        if (evidence_submission.get("discriminator", {}).get("propertyName") != "status"
                or set(evidence_submission.get("discriminator", {}).get("mapping", {})) != {
                    "PENDING_UPLOAD", "SUBMITTED", "ACCEPTED", "REJECTED"}):
            failures.append("FAIL Core API Slice 12 EvidenceSubmission: discriminator mapping changed")
        start_fulfillment_location = (core_paths.get("/deals/{dealId}/fulfillment", {}).get("post", {}).get("responses", {})
                .get("201", {}).get("headers", {}).get("Location", {}))
        if start_fulfillment_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API start fulfillment: 201 Location header is required")
        evidence_upload_intent_location = (core_paths.get("/deals/{dealId}/fulfillment/evidence/upload-intents", {}).get("post", {}).get("responses", {})
                .get("201", {}).get("headers", {}).get("Location", {}))
        if evidence_upload_intent_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API create evidence upload intent: 201 Location header is required")
        fulfillment_start_conflict = core_components.get("responses", {}).get("FulfillmentStartConflict", {})
        evidence_finalize_conflict = core_components.get("responses", {}).get("EvidenceFinalizeConflict", {})
        evidence_cancel_upload_conflict = core_components.get("responses", {}).get("EvidenceCancelUploadConflict", {})
        evidence_review_conflict = core_components.get("responses", {}).get("EvidenceReviewConflict", {})
        accept_without_evidence_conflict = core_components.get("responses", {}).get("AcceptWithoutEvidenceConflict", {})
        if ("DEAL_STALE_VERSION" not in fulfillment_start_conflict.get("description", "")
                or "DEAL_STATE_CONFLICT" not in fulfillment_start_conflict.get("description", "")
                or "FULFILLMENT_ALREADY_EXISTS" not in fulfillment_start_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in fulfillment_start_conflict.get("description", "")):
            failures.append("FAIL Core API Slice 12 start fulfillment: stable conflict codes changed")
        if ("EVIDENCE_UPLOAD_EXPIRED" not in evidence_finalize_conflict.get("description", "")
                or "EVIDENCE_VERIFICATION_FAILED" not in evidence_finalize_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in evidence_finalize_conflict.get("description", "")):
            failures.append("FAIL Core API Slice 12 evidence finalize: stable conflict codes changed")
        if ("EVIDENCE_UPLOAD_EXPIRED" not in evidence_cancel_upload_conflict.get("description", "")
                or "EVIDENCE_UPLOAD_STATE_CONFLICT" not in evidence_cancel_upload_conflict.get("description", "")
                or "EVIDENCE_STALE_VERSION" not in evidence_cancel_upload_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in evidence_cancel_upload_conflict.get("description", "")):
            failures.append("FAIL Core API Plan 18C cancel-upload: stable conflict codes changed")
        if ("DEAL_STALE_VERSION" not in evidence_review_conflict.get("description", "")
                or "EVIDENCE_STALE_VERSION" not in evidence_review_conflict.get("description", "")
                or "EVIDENCE_STATE_CONFLICT" not in evidence_review_conflict.get("description", "")
                or "FULFILLMENT_COMPLETED" not in evidence_review_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in evidence_review_conflict.get("description", "")):
            failures.append("FAIL Core API Slice 12 evidence review: stable conflict codes changed")
        if ("DEAL_STALE_VERSION" not in accept_without_evidence_conflict.get("description", "")
                or "FULFILLMENT_STALE_VERSION" not in accept_without_evidence_conflict.get("description", "")
                or "FULFILLMENT_EVIDENCE_POLICY_CONFLICT" not in accept_without_evidence_conflict.get("description", "")
                or "FULFILLMENT_EVIDENCE_PRESENT" not in accept_without_evidence_conflict.get("description", "")
                or "FULFILLMENT_STATE_CONFLICT" not in accept_without_evidence_conflict.get("description", "")
                or "FULFILLMENT_COMPLETED" not in accept_without_evidence_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in accept_without_evidence_conflict.get("description", "")):
            failures.append("FAIL Core API Plan 18B accept-without-evidence: stable conflict codes changed")

        video_analysis_status = core_components.get("schemas", {}).get("VideoAnalysisStatus", {})
        video_analysis_actions = core_components.get("schemas", {}).get("VideoAnalysisAvailableActions", {})
        video_analysis_request = core_components.get("schemas", {}).get("RequestVideoAnalysisRequest", {})
        video_analysis_detail = core_components.get("schemas", {}).get("VideoAnalysisDetail", {})
        video_analysis_result = core_components.get("schemas", {}).get("VideoAnalysisResult", {})
        video_analysis_observation_type = core_components.get("schemas", {}).get("VideoAnalysisObservationType", {})
        video_analysis_anomaly_severity = core_components.get("schemas", {}).get("VideoAnalysisAnomalySeverity", {})
        video_analysis_advisory_outcome = core_components.get("schemas", {}).get("VideoAnalysisAdvisoryOutcome", {})
        video_analysis_warning_severity = core_components.get("schemas", {}).get("VideoAnalysisWarningSeverity", {})
        video_analysis_request_forbidden = core_components.get("responses", {}).get("VideoAnalysisRequestForbidden", {})
        video_analysis_request_conflict = core_components.get("responses", {}).get("VideoAnalysisRequestConflict", {})
        forbidden_video_result_fields = {
            "technicalMetadata", "modelProvider", "modelFamily", "modelVersion", "promptVersion",
            "downloadUrl", "objectKey", "storageUrl", "providerReference", "eventPayload", "payload",
        }
        video_analysis_closed_fields = {
            "VideoAnalysisFailureSummary": {"code", "retryRecommended"},
            "VideoAnalysisTimeRange": {"startMs", "endMs"},
            "VideoAnalysisObservation": {"observationReference", "type", "label", "observedValue", "confidence", "timeRange"},
            "VideoAnalysisAnomaly": {"anomalyReference", "type", "severity", "description", "confidence", "timeRange"},
            "VideoAnalysisSummary": {"advisoryOutcome", "reviewReasons"},
            "VideoAnalysisWarning": {"code", "message", "severity", "path", "details"},
            "RequestVideoAnalysisRequest": {"expectedEvidenceVersion"},
            "VideoAnalysisAvailableActions": {"canRequest"},
            "VideoAnalysisDetail": {
                "evidenceSubmissionId", "jobId", "status", "requestedAt", "completedAt", "failedAt",
                "failure", "result", "availableActions",
            },
        }
        if (video_analysis_status.get("enum") != ["NOT_REQUESTED", "QUEUED", "RESULT_AVAILABLE", "FAILED"]
                or set(video_analysis_actions.get("required", [])) != {"canRequest"}
                or set(video_analysis_actions.get("properties", {})) != {"canRequest"}
                or video_analysis_actions.get("additionalProperties") is not False
                or set(video_analysis_request.get("required", [])) != {"expectedEvidenceVersion"}
                or set(video_analysis_request.get("properties", {})) != {"expectedEvidenceVersion"}
                or video_analysis_request.get("additionalProperties") is not False
                or video_analysis_request.get("properties", {}).get("expectedEvidenceVersion", {}).get("minimum") != 0
                or set(video_analysis_detail.get("required", [])) != {
                    "evidenceSubmissionId", "jobId", "status", "requestedAt", "completedAt", "failedAt",
                    "failure", "result", "availableActions"}
                or video_analysis_detail.get("properties", {}).get("result", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/VideoAnalysisResult"}, {"type": "null"}]
                or video_analysis_observation_type.get("enum")
                != ["OBJECT_COUNT", "OBJECT_PRESENCE", "SEQUENCE", "VISIBILITY", "OTHER", "UNKNOWN"]
                or video_analysis_anomaly_severity.get("enum") != ["LOW", "MEDIUM", "HIGH", "UNKNOWN"]
                or video_analysis_advisory_outcome.get("enum")
                != ["NO_ISSUE_DETECTED", "REVIEW_SUGGESTED", "INSUFFICIENT_EVIDENCE", "UNKNOWN"]
                or video_analysis_warning_severity.get("enum") != ["INFO", "WARNING"]
                or forbidden_video_result_fields & set(video_analysis_result.get("properties", {}))
                or "advisory only" not in video_analysis_detail.get("description", "").lower()
                or "VIDEO_ANALYSIS_REQUEST_FORBIDDEN" not in video_analysis_request_forbidden.get("description", "")
                or "VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE" not in video_analysis_request_conflict.get("description", "")
                or "EVIDENCE_STALE_VERSION" not in video_analysis_request_conflict.get("description", "")
                or "VIDEO_ANALYSIS_ACTIVE_JOB_EXISTS" not in video_analysis_request_conflict.get("description", "")
                or "VIDEO_ANALYSIS_ALREADY_COMPLETED" not in video_analysis_request_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in video_analysis_request_conflict.get("description", "")):
            failures.append("FAIL Core API Slice 13 video analysis: state, actions, safe result, or stable conflicts changed")
        for schema_name, expected_fields in video_analysis_closed_fields.items():
            schema = core_components.get("schemas", {}).get(schema_name, {})
            if (schema.get("additionalProperties") is not False
                    or set(schema.get("properties", {})) != expected_fields
                    or set(schema.get("required", [])) != expected_fields):
                failures.append(f"FAIL Core API Slice 13 {schema_name}: closed field set changed")
        warning_details = core_components.get("schemas", {}).get("VideoAnalysisWarningDetails", {})
        if (warning_details.get("additionalProperties") is not False
                or set(warning_details.get("properties", {})) != {"field", "reason", "expected", "observed"}):
            failures.append("FAIL Core API Slice 13 VideoAnalysisWarningDetails: closed field set changed")
        request_video_analysis_location = (core_paths.get(
                "/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", {})
                .get("post", {}).get("responses", {}).get("202", {}).get("headers", {}).get("Location", {}))
        if request_video_analysis_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API request video analysis: 202 Location header is required")
        request_video_analysis_description = core_paths.get(
            "/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis", {}
        ).get("post", {}).get("description", "")
        if ("IDEMPOTENCY_KEY_REUSED" not in request_video_analysis_description
                or "never waits" not in request_video_analysis_description
                or "expectedEvidenceVersion" not in request_video_analysis_description):
            failures.append("FAIL Core API request video analysis: idempotency, optimistic version, or async semantics missing")

        dispute_reason_code = core_components.get("schemas", {}).get("DisputeReasonCode", {})
        dispute_status = core_components.get("schemas", {}).get("DisputeStatus", {})
        dispute_actions = core_components.get("schemas", {}).get("DisputeAvailableActions", {})
        open_dispute_request = core_components.get("schemas", {}).get("OpenDisputeRequest", {})
        create_dispute_comment = core_components.get("schemas", {}).get("CreateDisputeCommentRequest", {})
        acknowledge_dispute = core_components.get("schemas", {}).get("AcknowledgeDisputeRequest", {})
        withdraw_dispute = core_components.get("schemas", {}).get("WithdrawDisputeRequest", {})
        dispute_detail = core_components.get("schemas", {}).get("DisputeDetail", {})
        dispute_snapshot = core_components.get("schemas", {}).get("DisputeOpeningSnapshot", {})
        dispute_evidence_snapshot = core_components.get("schemas", {}).get("DisputeEvidenceSnapshotEntry", {})
        dispute_video_snapshot = core_components.get("schemas", {}).get("DisputeVideoAnalysisSnapshotEntry", {})
        dispute_comment = core_components.get("schemas", {}).get("DisputeComment", {})
        deal_casework_summary = core_components.get("schemas", {}).get("DealCaseworkSummary", {})
        dispute_open_forbidden = core_components.get("responses", {}).get("DisputeOpenForbidden", {})
        dispute_open_conflict = core_components.get("responses", {}).get("DisputeOpenConflict", {})
        dispute_mutation_conflict = core_components.get("responses", {}).get("DisputeMutationConflict", {})
        casework_not_found = core_components.get("responses", {}).get("CaseworkNotFoundOrHidden", {})
        dispute_not_found = core_components.get("responses", {}).get("DisputeNotFoundOrHidden", {})
        forbidden_dispute_snapshot_fields = {
            "objectKey", "downloadUrl", "storageUrl", "presignedUrl", "payload", "providerReference",
            "modelProvider", "modelFamily", "modelVersion", "promptVersion", "technicalMetadata",
        }
        dispute_closed_fields = {
            "DisputeOpeningLegalEntity": {"legalEntityId", "legalName"},
            "DisputeAvailableActions": {"canComment", "canAcknowledge", "canWithdraw"},
            "DisputeSummary": {
                "id", "dealId", "status", "reasonCode", "subject", "openingLegalEntity",
                "openedAt", "acknowledgedAt", "withdrawnAt", "version", "availableActions",
            },
            "DisputeEvidenceSnapshotEntry": {
                "evidenceSubmissionId", "statusAtOpen", "versionAtOpen", "evidenceType", "mediaType",
                "fileName", "objectVersion", "verifiedSizeBytes", "verifiedSha256", "createdAt",
                "submittedAt", "acceptedAt", "rejectedAt", "rejectionReason",
            },
            "DisputeVideoAnalysisSnapshotEntry": {"evidenceSubmissionId", "jobId", "resultId", "result"},
            "DisputeOpeningSnapshot": {
                "ratificationPackageId", "fulfillmentId", "fulfillmentStatusAtOpen",
                "fulfillmentVersionAtOpen", "milestoneId", "milestoneVersionAtOpen",
                "evidence", "videoAnalysis",
            },
            "DisputeDetail": {
                "id", "dealId", "status", "reasonCode", "subject", "statement", "openingLegalEntity",
                "openedAt", "acknowledgedAt", "withdrawnAt", "openingSnapshot", "version", "availableActions",
            },
            "DisputeCommentAuthorAttribution": {"legalEntityId", "legalName", "displayName"},
            "DisputeComment": {"id", "body", "authorAttribution", "createdAt"},
            "OpenDisputeRequest": {"reasonCode", "subject", "statement", "expectedDealVersion", "expectedFulfillmentVersion"},
            "CreateDisputeCommentRequest": {"body", "expectedVersion"},
            "AcknowledgeDisputeRequest": {"expectedVersion"},
            "WithdrawDisputeRequest": {"expectedVersion"},
            "DealCaseworkSummary": {
                "disputeId", "status", "reasonCode", "subject", "openingLegalEntity",
                "openedAt", "acknowledgedAt", "version",
            },
        }
        if (dispute_reason_code.get("enum")
                != ["NON_DELIVERY", "EVIDENCE_QUALITY", "EVIDENCE_REJECTION", "CONTRACT_NON_CONFORMANCE", "OTHER"]
                or dispute_status.get("enum") != ["OPEN", "UNDER_REVIEW", "RESOLVED", "WITHDRAWN"]
                or set(dispute_actions.get("required", [])) != {"canComment", "canAcknowledge", "canWithdraw"}
                or set(dispute_actions.get("properties", {})) != {"canComment", "canAcknowledge", "canWithdraw"}
                or dispute_actions.get("additionalProperties") is not False
                or set(open_dispute_request.get("required", []))
                != {"reasonCode", "subject", "statement", "expectedDealVersion", "expectedFulfillmentVersion"}
                or set(open_dispute_request.get("properties", {}))
                != {"reasonCode", "subject", "statement", "expectedDealVersion", "expectedFulfillmentVersion"}
                or open_dispute_request.get("properties", {}).get("subject", {}).get("maxLength") != 200
                or open_dispute_request.get("properties", {}).get("statement", {}).get("maxLength") != 4000
                or set(create_dispute_comment.get("required", [])) != {"body", "expectedVersion"}
                or create_dispute_comment.get("properties", {}).get("body", {}).get("maxLength") != 4000
                or set(acknowledge_dispute.get("required", [])) != {"expectedVersion"}
                or set(withdraw_dispute.get("required", [])) != {"expectedVersion"}
                or set(dispute_detail.get("required", [])) != dispute_closed_fields["DisputeDetail"]
                or forbidden_dispute_snapshot_fields & set(dispute_evidence_snapshot.get("properties", {}))
                or forbidden_dispute_snapshot_fields & set(dispute_video_snapshot.get("properties", {}))
                or "email" in str(dispute_comment.get("properties", {}))
                or "CASEWORK_NOT_FOUND_OR_HIDDEN" not in casework_not_found.get("description", "")
                or "DISPUTE_NOT_FOUND_OR_HIDDEN" not in dispute_not_found.get("description", "")
                or "DISPUTE_OPEN_FORBIDDEN" not in dispute_open_forbidden.get("description", "")
                or "DEAL_STALE_VERSION" not in dispute_open_conflict.get("description", "")
                or "FULFILLMENT_STALE_VERSION" not in dispute_open_conflict.get("description", "")
                or "DISPUTE_ACTIVE_CASE_EXISTS" not in dispute_open_conflict.get("description", "")
                or "DISPUTE_STALE_VERSION" not in dispute_mutation_conflict.get("description", "")
                or "DISPUTE_STATE_CONFLICT" not in dispute_mutation_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in dispute_open_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in dispute_mutation_conflict.get("description", "")):
            failures.append("FAIL Core API Slice 14A casework: enums, actions, bounds, disclosure, or stable conflicts changed")
        for schema_name, expected_fields in dispute_closed_fields.items():
            schema = core_components.get("schemas", {}).get(schema_name, {})
            if (schema.get("additionalProperties") is not False
                    or set(schema.get("properties", {})) != expected_fields
                    or set(schema.get("required", [])) != expected_fields):
                failures.append(f"FAIL Core API Slice 14A {schema_name}: closed field set changed")

        settlement_status = core_components.get("schemas", {}).get("SettlementStatus", {})
        release_operation_status = core_components.get("schemas", {}).get("ReleaseOperationStatus", {})
        settlement_actions = core_components.get("schemas", {}).get("SettlementAvailableActions", {})
        release_operation_actions = core_components.get("schemas", {}).get("ReleaseOperationAvailableActions", {})
        settlement_detail = core_components.get("schemas", {}).get("SettlementDetail", {})
        release_operation = core_components.get("schemas", {}).get("ReleaseOperation", {})
        release_operation_summary = core_components.get("schemas", {}).get("ReleaseOperationSummary", {})
        deal_settlement_summary = core_components.get("schemas", {}).get("DealSettlementSummary", {})
        request_settlement_release = core_components.get("schemas", {}).get("RequestSettlementReleaseRequest", {})
        reconcile_release_operation = core_components.get("schemas", {}).get("ReconcileReleaseOperationRequest", {})
        settlement_mutation_forbidden = core_components.get("responses", {}).get("SettlementMutationForbidden", {})
        settlement_not_found = core_components.get("responses", {}).get("SettlementNotFoundOrHidden", {})
        settlement_release_conflict = core_components.get("responses", {}).get("SettlementReleaseConflict", {})
        release_operation_not_found = core_components.get("responses", {}).get("ReleaseOperationNotFoundOrHidden", {})
        release_operation_reconcile_conflict = core_components.get("responses", {}).get("ReleaseOperationReconcileConflict", {})
        settlement_closed_fields = {
            "SettlementAvailableActions": {"canRequestRelease", "canReconcileRelease"},
            "ReleaseOperationAvailableActions": {"canReconcile"},
            "ReleaseOperationSummary": {"id", "status", "reconciliationRequired"},
            "ReleaseOperation": {"id", "settlementId", "status", "mode", "reconciliationRequired", "availableActions", "version", "createdAt", "updatedAt"},
            "SettlementDetail": {"id", "dealId", "status", "mode", "disputeWindowDays", "releaseEligibleAt", "currentReleaseOperation", "availableActions", "version", "createdAt", "updatedAt"},
            "DealSettlementSummary": {"settlementId", "status", "currentReleaseOperationId"},
            "RequestSettlementReleaseRequest": {"expectedDealVersion", "expectedSettlementVersion", "expectedFulfillmentVersion", "expectedFundingUnitVersion"},
            "ReconcileReleaseOperationRequest": {"expectedVersion"},
        }
        if (settlement_status.get("enum")
                != ["NOT_READY", "READY", "PROCESSING", "ON_HOLD", "SIMULATED_SETTLED", "FAILED"]
                or release_operation_status.get("enum")
                != ["QUEUED", "PROCESSING", "RECONCILIATION_REQUIRED", "SIMULATED_SETTLED", "SIMULATED_DECLINED", "FAILED_BEFORE_DISPATCH"]
                or set(settlement_actions.get("required", [])) != {"canRequestRelease", "canReconcileRelease"}
                or set(settlement_actions.get("properties", {})) != {"canRequestRelease", "canReconcileRelease"}
                or settlement_actions.get("additionalProperties") is not False
                or set(release_operation_actions.get("required", [])) != {"canReconcile"}
                or set(release_operation_actions.get("properties", {})) != {"canReconcile"}
                or release_operation_actions.get("additionalProperties") is not False
                or release_operation.get("properties", {}).get("mode", {}).get("$ref")
                != "#/components/schemas/PaymentSimulationMode"
                or settlement_detail.get("properties", {}).get("mode", {}).get("$ref")
                != "#/components/schemas/PaymentSimulationMode"
                or release_operation.get("properties", {}).get("reconciliationRequired", {}).get("type") != "boolean"
                or settlement_detail.get("properties", {}).get("currentReleaseOperation", {}).get("anyOf")
                != [{"$ref": "#/components/schemas/ReleaseOperationSummary"}, {"type": "null"}]
                or settlement_detail.get("properties", {}).get("disputeWindowDays", {}).get("anyOf", [{}])[-1]
                != {"type": "null"}
                or settlement_detail.get("properties", {}).get("releaseEligibleAt", {}).get("anyOf", [{}])[-1]
                != {"type": "null"}
                or deal_settlement_summary.get("properties", {}).get("currentReleaseOperationId", {}).get("anyOf", [{}])[-1]
                != {"type": "null"}
                or request_settlement_release.get("properties", {}).get("expectedDealVersion", {}).get("maximum") != 9007199254740991
                or request_settlement_release.get("properties", {}).get("expectedSettlementVersion", {}).get("maximum") != 9007199254740991
                or request_settlement_release.get("properties", {}).get("expectedFulfillmentVersion", {}).get("maximum") != 9007199254740991
                or request_settlement_release.get("properties", {}).get("expectedFundingUnitVersion", {}).get("maximum") != 9007199254740991
                or reconcile_release_operation.get("properties", {}).get("expectedVersion", {}).get("maximum") != 9007199254740991
                or "SETTLEMENT_MUTATION_FORBIDDEN" not in settlement_mutation_forbidden.get("description", "")
                or "SETTLEMENT_NOT_FOUND" not in settlement_not_found.get("description", "")
                or "DEAL_NOT_FOUND" not in settlement_not_found.get("description", "")
                or "SETTLEMENT_STALE_VERSION" not in settlement_release_conflict.get("description", "")
                or "SETTLEMENT_CONTRACTUAL_WINDOW_MISSING" not in settlement_release_conflict.get("description", "")
                or "SETTLEMENT_DISPUTE_WINDOW_NOT_ELAPSED" not in settlement_release_conflict.get("description", "")
                or "SETTLEMENT_ACTIVE_DISPUTE" not in settlement_release_conflict.get("description", "")
                or "RELEASE_OPERATION_ALREADY_EXISTS" not in settlement_release_conflict.get("description", "")
                or "SETTLEMENT_ALREADY_TERMINAL" not in settlement_release_conflict.get("description", "")
                or "IDEMPOTENCY_KEY_REUSED" not in settlement_release_conflict.get("description", "")
                or "RELEASE_OPERATION_NOT_FOUND" not in release_operation_not_found.get("description", "")
                or "RELEASE_OPERATION_STALE_VERSION" not in release_operation_reconcile_conflict.get("description", "")
                or "RELEASE_RECONCILIATION_UNAVAILABLE" not in release_operation_reconcile_conflict.get("description", "")
                or "RELEASE_OUTCOME_UNKNOWN" not in release_operation_reconcile_conflict.get("description", "")):
            failures.append("FAIL Core API Plan 17 settlement: closed enums, projections, requests, or stable conflicts changed")
        for schema_name, expected_fields in settlement_closed_fields.items():
            schema = core_components.get("schemas", {}).get(schema_name, {})
            if (schema.get("additionalProperties") is not False
                    or set(schema.get("properties", {})) != expected_fields
                    or set(schema.get("required", [])) != expected_fields):
                failures.append(f"FAIL Core API Plan 17 {schema_name}: closed field set changed")
        release_operation_id = core_parameters.get("ReleaseOperationId", {})
        if ((release_operation_id.get("name"), release_operation_id.get("in"), release_operation_id.get("required"),
                release_operation_id.get("schema", {}).get("format")) != ("operationId", "path", True, "uuid")):
            failures.append("FAIL Core API ReleaseOperationId: required UUID path contract changed")
        release_location = (core_paths.get("/deals/{dealId}/settlement/release", {}).get("post", {}).get("responses", {})
                .get("202", {}).get("headers", {}).get("Location", {}))
        reconcile_release_location = (core_paths.get("/release-operations/{operationId}/reconcile", {}).get("post", {}).get("responses", {})
                .get("202", {}).get("headers", {}).get("Location", {}))
        if (release_location.get("schema", {}).get("format") != "uri-reference"
                or reconcile_release_location.get("schema", {}).get("format") != "uri-reference"):
            failures.append("FAIL Core API settlement release/reconcile: 202 Location header is required")

        dispute_sort = core_parameters.get("DisputeSort", {}).get("schema", {})
        dispute_comment_sort = core_parameters.get("DisputeCommentSort", {}).get("schema", {})
        if (dispute_sort.get("default") != "openedAt,desc"
                or dispute_sort.get("enum") != ["openedAt,asc", "openedAt,desc"]):
            failures.append("FAIL Core API DisputeSort: single allowlisted sort contract changed")
        if (dispute_comment_sort.get("default") != "createdAt,asc"
                or dispute_comment_sort.get("enum") != ["createdAt,asc", "createdAt,desc"]):
            failures.append("FAIL Core API DisputeCommentSort: single allowlisted sort contract changed")
        dispute_id = core_parameters.get("DisputeId", {})
        if ((dispute_id.get("name"), dispute_id.get("in"), dispute_id.get("required"),
                dispute_id.get("schema", {}).get("format")) != ("disputeId", "path", True, "uuid")):
            failures.append("FAIL Core API DisputeId: required UUID path contract changed")
        open_dispute_location = (core_paths.get("/deals/{dealId}/disputes", {}).get("post", {}).get("responses", {})
                .get("201", {}).get("headers", {}).get("Location", {}))
        if open_dispute_location.get("schema", {}).get("format") != "uri-reference":
            failures.append("FAIL Core API open dispute: 201 Location header is required")
        open_dispute_description = core_paths.get("/deals/{dealId}/disputes", {}).get("post", {}).get("description", "")
        if ("IDEMPOTENCY_KEY_REUSED" not in open_dispute_description
                or "expectedDealVersion" not in open_dispute_description
                or "expectedFulfillmentVersion" not in open_dispute_description
                or "never accepts client-supplied" not in open_dispute_description.lower()):
            failures.append("FAIL Core API open dispute: idempotency, optimistic version, or snapshot-input semantics missing")

        for (path, method), expected_ref in EXPECTED_CORE_REQUEST_SCHEMAS.items():
            actual_ref = (core_paths.get(path, {}).get(method, {}).get("requestBody", {})
                    .get("content", {}).get("application/json", {}).get("schema", {}).get("$ref"))
            if actual_ref != expected_ref:
                failures.append(f"FAIL Core API request schema: {method.upper()} {path}")
        for (path, method, status), expected_ref in EXPECTED_CORE_SUCCESS_SCHEMAS.items():
            actual_ref = (core_paths.get(path, {}).get(method, {}).get("responses", {}).get(status, {})
                    .get("content", {}).get("application/json", {}).get("schema", {}).get("$ref"))
            if actual_ref != expected_ref:
                failures.append(f"FAIL Core API success schema: {method.upper()} {path} {status}")
        if "content" in core_paths.get("/auth/logout", {}).get("post", {}).get("responses", {}).get("204", {}):
            failures.append("FAIL Core API logout: 204 response must not contain a body")
        for (path, method, status), response_name in EXPECTED_CORE_ERROR_RESPONSES.items():
            expected_ref = f"#/components/responses/{response_name}"
            actual_ref = core_paths.get(path, {}).get(method, {}).get("responses", {}).get(status, {}).get("$ref")
            if actual_ref != expected_ref:
                failures.append(f"FAIL Core API error response: {method.upper()} {path} {status}")
        validate_closed_error_catalogs(core_openapi, failures)
        validate_error_catalog_fixtures(core_openapi, failures)
        for message in asyncapi.get("components", {}).get("messages", {}).values():
            reference = message.get("payload", {}).get("$ref")
            if isinstance(reference, str) and reference.startswith("../"):
                target = (asyncapi_path.parent / reference).resolve()
                if not target.exists():
                    failures.append(f"FAIL AsyncAPI reference {reference}: file does not exist")
        if not failures:
            print("PASS AsyncAPI/OpenAPI YAML, paths, components, channels, and external references")
    except Exception as exc:  # noqa: BLE001 - this is a CLI validator
        failures.append(f"FAIL API contract documents: {exc}")


def validate() -> int:
    schema_paths = sorted(SCHEMA_ROOT.rglob("*.schema.json"))
    failures: list[str] = []
    schemas, store, load_failures = load_schemas(schema_paths)
    failures.extend(load_failures)

    duplicate_ids = find_duplicate_ids(schema_paths, schemas)
    if duplicate_ids:
        for schema_id, paths in duplicate_ids.items():
            failures.append(f"FAIL duplicate schema $id {schema_id}: {', '.join(paths)}")
    else:
        print("PASS duplicate schema IDs: none")

    id_pattern = re.compile(r"^https://schemas\.m4trust\.internal/ai(?:/[a-z0-9-]+)+/1\.0\.0$")
    for path in schema_paths:
        schema = schemas.get(path)
        if schema is None:
            continue
        try:
            Draft202012Validator.check_schema(schema)
            schema_id = schema.get("$id")
            if not isinstance(schema_id, str) or not schema_id.startswith(SCHEMA_ID_NAMESPACE) or not id_pattern.match(schema_id):
                failures.append(f"FAIL schema {path.relative_to(ROOT)}: $id must use the canonical versioned namespace")
            else:
                print(f"PASS schema {path.relative_to(ROOT)}")
        except Exception as exc:  # noqa: BLE001 - this is a CLI validator
            failures.append(f"FAIL schema {path.relative_to(ROOT)}: {exc}")

    for schema_name in CONCRETE_EVENT_SCHEMAS:
        path = ROOT / schema_name
        schema = schemas.get(path)
        if schema is None:
            failures.append(f"FAIL event schema {schema_name}: schema was not loaded")
            continue
        properties = event_properties(schema)
        required = set()
        for branch in schema.get("allOf", []):
            if isinstance(branch, dict):
                required.update(branch.get("required", []))
        for field in ("eventType", "jobType", "schemaVersion"):
            if field not in properties or field not in required:
                failures.append(f"FAIL event schema {schema_name}: {field} is not constrained and required")
        if properties.get("eventType", {}).get("const") is None:
            failures.append(f"FAIL event schema {schema_name}: eventType must use const")
        if properties.get("schemaVersion", {}).get("const") != "1.0.0":
            failures.append(f"FAIL event schema {schema_name}: schemaVersion must be const 1.0.0")
    if not failures:
        print("PASS concrete event schemaVersion/eventType/jobType constraints")

    validate_contract_documents(failures)
    validate_core_internal_openapi(failures)
    validate_bundle_digest(failures)

    for fixture_name, schema_name in FIXTURE_SCHEMAS.items():
        fixture_path = ROOT / fixture_name
        schema_path = ROOT / schema_name
        try:
            instance = read_json(fixture_path)
            errors = errors_for(schema_path, schemas[schema_path], instance, store)
            if errors:
                failures.append(f"FAIL fixture {fixture_path.relative_to(ROOT)}: {format_errors(errors)}")
            else:
                print(f"PASS valid fixture {fixture_path.relative_to(ROOT)}")
        except Exception as exc:  # noqa: BLE001 - this is a CLI validator
            failures.append(f"FAIL fixture {fixture_path.relative_to(ROOT)}: {exc}")

    document_request_path = ROOT / "schemas/document-extraction/requested-event-1.0.0.schema.json"
    document_completed_path = ROOT / "schemas/document-extraction/completed-event-1.0.0.schema.json"
    document_failed_path = ROOT / "schemas/document-extraction/failed-event-1.0.0.schema.json"
    request = read_json(ROOT / "examples/document-extraction/minimum-request.json")
    invalid_version = copy.deepcopy(request)
    invalid_version["schemaVersion"] = "1.1.0"
    expect_invalid("schemaVersion 1.1.0 against requested-event-1.0.0", document_request_path, invalid_version, store, failures)

    non_utc = copy.deepcopy(request)
    non_utc["occurredAt"] = "2026-07-13T15:00:00+03:00"
    expect_invalid("non-UTC occurredAt offset", document_request_path, non_utc, store, failures)

    retryable_failure_path = ROOT / "schemas/document-extraction/failed-event-1.0.0.schema.json"
    retryable = read_json(ROOT / "examples/document-extraction/retryable-failure.json")
    invalid_category = copy.deepcopy(retryable)
    invalid_category["payload"]["error"]["category"] = "INVALID_INPUT"
    expect_invalid("INVALID_INPUT with MODEL_PROVIDER_TIMEOUT", retryable_failure_path, invalid_category, store, failures)
    invalid_retry_flag = copy.deepcopy(retryable)
    invalid_retry_flag["payload"]["error"]["retryRecommended"] = False
    expect_invalid("RETRYABLE_TECHNICAL with retryRecommended false", retryable_failure_path, invalid_retry_flag, store, failures)
    invalid_non_retry = read_json(ROOT / "examples/document-extraction/non-retryable-failure.json")
    invalid_non_retry["payload"]["error"]["retryRecommended"] = True
    expect_invalid("NON_RETRYABLE_TECHNICAL with retryRecommended true", retryable_failure_path, invalid_non_retry, store, failures)

    completed = read_json(ROOT / "examples/document-extraction/success-result.json")
    future_optional = copy.deepcopy(completed)
    future_optional["payload"]["result"]["summary"]["futureOptionalMetadata"] = {"value": "ignored by older consumers"}
    expect_valid("future optional document summary metadata", document_completed_path, future_optional, store, failures)

    video_completed_path = ROOT / "schemas/video-analysis/completed-event-1.0.0.schema.json"
    video_completed = read_json(ROOT / "examples/video-analysis/success-result.json")
    video_future_optional = copy.deepcopy(video_completed)
    video_future_optional["payload"]["result"]["summary"]["futureOptionalMetadata"] = {"value": "ignored by older consumers"}
    expect_valid("future optional video summary metadata", video_completed_path, video_future_optional, store, failures)

    video_result_future_optional = copy.deepcopy(video_completed)
    video_result_future_optional["payload"]["result"]["futureOptionalResultMetadata"] = {"value": "ignored by older consumers"}
    expect_valid("future optional video result metadata", video_completed_path, video_result_future_optional, store, failures)

    strict_value = copy.deepcopy(completed)
    strict_value["payload"]["result"]["rules"][0]["structuredValue"]["futureField"] = "must be rejected"
    expect_invalid("unknown property in closed structured-value variant", document_completed_path, strict_value, store, failures)

    legal_basis_unknown = copy.deepcopy(completed)
    legal_basis_unknown["payload"]["result"]["rules"][0]["legalBasis"]["excerpt"] = "must be rejected"
    expect_invalid("unknown property in closed legalBasis object", document_completed_path, legal_basis_unknown, store, failures)

    legal_basis_bad_source = copy.deepcopy(completed)
    legal_basis_bad_source["payload"]["result"]["rules"][0]["legalBasis"]["source"] = "unknown-law"
    expect_invalid("legalBasis source outside closed enum", document_completed_path, legal_basis_bad_source, store, failures)

    legal_basis_missing_article = copy.deepcopy(completed)
    del legal_basis_missing_article["payload"]["result"]["rules"][0]["legalBasis"]["articleNo"]
    expect_invalid("legalBasis without required articleNo", document_completed_path, legal_basis_missing_article, store, failures)

    if find_duplicate_ids([Path("one.json"), Path("two.json")], {Path("one.json"): {"$id": "duplicate"}, Path("two.json"): {"$id": "duplicate"}}):
        print("PASS duplicate schema ID detector negative case")
    else:
        failures.append("FAIL duplicate schema ID detector negative case: duplicate was not detected")

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        print(f"Validation failed: {len(failures)} problem(s).", file=sys.stderr)
        return 1

    print(f"Validation succeeded: {len(schema_paths)} schemas and {len(FIXTURE_SCHEMAS)} valid fixtures; expected-invalid checks passed.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--print-digest":
        print(contract_bundle_digest(ROOT), end="")
        raise SystemExit(0)
    sys.exit(validate())
