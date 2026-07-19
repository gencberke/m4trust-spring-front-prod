#!/usr/bin/env python3
"""Lightweight validation for the M4Trust contract foundation."""

from __future__ import annotations

import copy
import json
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
}
REQUIRED_CORE_API_SCHEMAS = {
    "RegisterRequest", "LoginRequest", "PublicUser", "CurrentUser", "CsrfToken",
    "CreateLegalEntityRequest", "LegalEntity", "LegalEntityRole",
    "LegalEntityMembership", "LegalEntityMembershipList",
    "LegalEntityMember", "LegalEntityMemberList",
    "CreateDealRequest", "UpdateDealRequest", "UpdateDealPartiesRequest", "DealStatus",
    "DealLifecycleProjection", "DealAvailableActions", "DealSummary",
    "DealParticipant", "DealPartyRole", "DealParty", "DealDetail", "DealPage", "UtcTimestamp", "ProblemDetail", "FieldError",
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
}


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
        if (set(actions.get("required", [])) != {"canUpdate", "canCancel", "canCreateInvitation", "canManageParties", "canCreateDocumentUploadIntent", "canRequestAnalysis"}
                or set(actions.get("properties", {})) != {"canUpdate", "canCancel", "canCreateInvitation", "canManageParties", "canCreateDocumentUploadIntent", "canRequestAnalysis", "canReviewExtraction"}
                or actions.get("additionalProperties") is not False
                or any(
                    actions.get("properties", {}).get(name, {}).get("type") != "boolean"
                    for name in ("canUpdate", "canCancel", "canCreateInvitation", "canManageParties", "canCreateDocumentUploadIntent", "canRequestAnalysis", "canReviewExtraction")
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
        detail_fields = common_deal_fields | {"description", "buyer", "seller", "participants", "currentDocument", "analysis", "currentRuleSet"}
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
                != [{"$ref": "#/components/schemas/RuleSetVersionSummary"}, {"type": "null"}]):
            failures.append("FAIL Core API DealDetail: party and participant-role projection changed")

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
            ("/documents/{documentId}/finalize", "post"): {
                "#/components/parameters/DocumentId",
                "#/components/parameters/LegalEntityContext",
                "#/components/parameters/IdempotencyKey",
            },
            ("/documents/{documentId}/download-link", "post"): {
                "#/components/parameters/DocumentId",
                "#/components/parameters/LegalEntityContext",
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
        document_id = core_parameters.get("DocumentId", {})
        if ((document_id.get("name"), document_id.get("in"), document_id.get("required"),
                document_id.get("schema", {}).get("format")) != ("documentId", "path", True, "uuid")):
            failures.append("FAIL Core API DocumentId: required UUID path contract changed")
        rule_set_version_id = core_parameters.get("RuleSetVersionId", {})
        if ((rule_set_version_id.get("name"), rule_set_version_id.get("in"), rule_set_version_id.get("required"),
                rule_set_version_id.get("schema", {}).get("format")) != ("ruleSetVersionId", "path", True, "uuid")):
            failures.append("FAIL Core API RuleSetVersionId: required UUID path contract changed")
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
    sys.exit(validate())
