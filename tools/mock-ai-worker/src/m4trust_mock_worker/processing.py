from __future__ import annotations

import copy
import hashlib
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Callable

from .contracts import ContractValidator, ContractViolation, DOCUMENT_JOB_TYPE, VIDEO_JOB_TYPE


@dataclass(frozen=True)
class DownloadFailure(Exception):
    category: str
    code: str
    message: str
    details: dict[str, Any]
    retry_recommended: bool


class DocumentDownloader:
    def __init__(self, timeout_seconds: float, max_attempts: int,
                 backoff: Callable[[float], None] = time.sleep,
                 host_override: str | None = None):
        self._timeout_seconds = timeout_seconds
        self._max_attempts = max_attempts
        self._backoff = backoff
        self._host_override = host_override

    def download_and_verify(self, input_data: dict[str, Any]) -> bytes:
        expires_at = datetime.fromisoformat(input_data["download"]["expiresAt"].replace("Z", "+00:00"))
        if expires_at <= datetime.now(timezone.utc):
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_DOWNLOAD_REFERENCE", "The download reference has expired.",
                {"field": "input.download.expiresAt", "reason": "expired"}, False,
            )

        document = self._download_with_retry(input_data["download"]["url"])
        if len(document) != input_data["sizeBytes"]:
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_EXPECTED_OBJECT", "The downloaded object size does not match the request.",
                {"field": "input.sizeBytes", "reason": "size mismatch"}, False,
            )
        actual_hash = hashlib.sha256(document).hexdigest()
        if actual_hash.lower() != input_data["sha256"].lower():
            raise DownloadFailure(
                "NON_RETRYABLE_TECHNICAL", "CONTENT_HASH_MISMATCH", "The downloaded object hash does not match the request.",
                {"field": "input.sha256", "reason": "content hash mismatch"}, False,
            )
        return document

    def _download_with_retry(self, url: str) -> bytes:
        connection_url, host_header = self._connection_target(url)
        last_error: Exception | None = None
        for attempt in range(1, self._max_attempts + 1):
            try:
                headers = {"User-Agent": "m4trust-mock-ai-worker/1.0"}
                if host_header:
                    headers["Host"] = host_header
                request = urllib.request.Request(connection_url, headers=headers)
                with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                    return response.read()
            except urllib.error.HTTPError as error:
                if error.code < 500:
                    raise DownloadFailure(
                        "INVALID_INPUT", "INVALID_DOWNLOAD_REFERENCE", "The download reference could not be read.",
                        {"field": "input.download", "reason": "unavailable reference"}, False,
                    ) from error
                last_error = error
            except (urllib.error.URLError, TimeoutError, OSError) as error:
                last_error = error
            if attempt < self._max_attempts:
                self._backoff(0.1 * (2 ** (attempt - 1)))
        raise DownloadFailure(
            "RETRYABLE_TECHNICAL", "OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE",
            "The object storage dependency is temporarily unavailable.",
            {"dependency": "object-storage", "reason": "temporarily unavailable", "retryAfterMs": 1000}, True,
        ) from last_error

    def _connection_target(self, url: str) -> tuple[str, str | None]:
        if not self._host_override:
            return url, None
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "http":
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_DOWNLOAD_REFERENCE",
                "The local download host override only supports HTTP.",
                {"field": "input.download", "reason": "unsupported local override"}, False,
            )
        port = f":{parsed.port}" if parsed.port else ""
        target = urllib.parse.urlunsplit(
            (parsed.scheme, f"{self._host_override}{port}", parsed.path, parsed.query, parsed.fragment)
        )
        return target, parsed.netloc


class ScenarioSelector:
    def __init__(self, configured_scenario: str):
        self._configured_scenario = configured_scenario

    def select(self, file_name: str) -> str:
        if self._configured_scenario != "auto":
            return self._configured_scenario
        normalized = file_name.lower()
        if normalized.startswith("fail-retryable"):
            return "retryable_failure"
        if normalized.startswith("duplicate"):
            return "duplicate"
        if normalized.startswith("fail-warning") or normalized.startswith("warning"):
            return "warning"
        return "success"


class EventFactory:
    CANONICAL_PRODUCER = {"service": "m4trust-ai-worker", "version": "1.0.0"}

    def __init__(self, contracts: ContractValidator):
        self._contracts = contracts

    def document_completed(self, request: dict[str, Any]) -> dict[str, Any]:
        payload = copy.deepcopy(self._contracts.fixture_payload("success-result.json", DOCUMENT_JOB_TYPE))
        payload["result"]["document"]["contentSha256"] = request["payload"]["input"]["sha256"].lower()
        event = self._envelope(request, "ai.job.completed.v1", payload, "completed")
        self._contracts.validate_completed(event)
        return event

    def video_completed(self, request: dict[str, Any], fixture_name: str) -> dict[str, Any]:
        payload = copy.deepcopy(self._contracts.fixture_payload(fixture_name, VIDEO_JOB_TYPE))
        event = self._envelope(request, "ai.job.completed.v1", payload, "completed")
        self._contracts.validate_completed(event)
        return event

    def retryable_failure(self, request: dict[str, Any]) -> dict[str, Any]:
        payload = copy.deepcopy(self._contracts.fixture_payload("retryable-failure.json", request["jobType"]))
        event = self._envelope(request, "ai.job.failed.v1", payload, "failed")
        self._contracts.validate_failed(event)
        return event

    def download_failure(self, request: dict[str, Any], failure: DownloadFailure) -> dict[str, Any]:
        fixture = "retryable-failure.json" if failure.retry_recommended else "non-retryable-failure.json"
        payload = copy.deepcopy(self._contracts.fixture_payload(fixture, request["jobType"]))
        payload["error"] = {
            "category": failure.category,
            "code": failure.code,
            "message": failure.message,
            "retryRecommended": failure.retry_recommended,
            "details": failure.details,
        }
        payload["attempt"] = {"attemptNumber": 1, "maxAttempts": 3 if failure.retry_recommended else 1}
        event = self._envelope(request, "ai.job.failed.v1", payload, "failed")
        self._contracts.validate_failed(event)
        return event

    def _envelope(self, request: dict[str, Any], event_type: str, payload: dict[str, Any], suffix: str) -> dict[str, Any]:
        return {
            "eventId": str(uuid.uuid4()),
            "eventType": event_type,
            "schemaVersion": "1.0.0",
            "occurredAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "correlationId": request["correlationId"],
            "causationId": request["eventId"],
            "jobId": request["jobId"],
            "jobType": request["jobType"],
            "tenantId": request["tenantId"],
            "transactionId": request["transactionId"],
            "subjectId": request["subjectId"],
            "idempotencyKey": f'{request["idempotencyKey"]}:mock-{suffix}',
            "producer": self.CANONICAL_PRODUCER,
            "payload": payload,
        }


class Processor:
    DOCUMENT_COMPLETED = "ai.document-extraction.completed.v1"
    DOCUMENT_FAILED = "ai.document-extraction.failed.v1"
    VIDEO_COMPLETED = "ai.video-analysis.completed.v1"
    VIDEO_FAILED = "ai.video-analysis.failed.v1"

    def __init__(self, contracts: ContractValidator, downloader: DocumentDownloader, selector: ScenarioSelector):
        self._contracts = contracts
        self._downloader = downloader
        self._selector = selector
        self._events = EventFactory(contracts)

    def process(self, request: dict[str, Any]) -> list[tuple[str, dict[str, Any]]]:
        job_type = request["jobType"]
        if job_type == DOCUMENT_JOB_TYPE:
            return self._process_document(request)
        if job_type == VIDEO_JOB_TYPE:
            return self._process_video(request)
        raise ContractViolation("Unsupported job type")

    def _process_document(self, request: dict[str, Any]) -> list[tuple[str, dict[str, Any]]]:
        return self._process(
            request,
            self.DOCUMENT_FAILED,
            self.DOCUMENT_COMPLETED,
            self._validate_document_request,
        )

    def _process_video(self, request: dict[str, Any]) -> list[tuple[str, dict[str, Any]]]:
        return self._process(
            request,
            self.VIDEO_FAILED,
            self.VIDEO_COMPLETED,
            self._validate_video_request,
            warning_fixture="warning-result.json",
        )

    def _process(
        self,
        request: dict[str, Any],
        failed_routing_key: str,
        completed_routing_key: str,
        validate_request: Callable[[dict[str, Any]], None],
        warning_fixture: str | None = None,
    ) -> list[tuple[str, dict[str, Any]]]:
        self._contracts.validate_request(request)
        try:
            validate_request(request)
            self._downloader.download_and_verify(request["payload"]["input"])
        except DownloadFailure as failure:
            return [(failed_routing_key, self._events.download_failure(request, failure))]

        scenario = self._selector.select(request["payload"]["input"]["fileName"])
        if scenario == "retryable_failure":
            return [(failed_routing_key, self._events.retryable_failure(request))]

        if request["jobType"] == VIDEO_JOB_TYPE:
            fixture_name = warning_fixture if scenario == "warning" else "success-result.json"
            completed = self._events.video_completed(request, fixture_name)
        else:
            completed = self._events.document_completed(request)

        messages = [(completed_routing_key, completed)]
        return messages * 2 if scenario == "duplicate" else messages

    @staticmethod
    def _validate_document_request(request: dict[str, Any]) -> None:
        payload = request["payload"]
        if request["subjectId"] != payload["input"]["documentId"]:
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_EXPECTED_OBJECT", "The requested object identity is inconsistent.",
                {"field": "input.documentId", "reason": "subject mismatch"}, False,
            )
        if payload["processing"]["requestedOutputSchemaVersion"] != "1.0.0":
            raise DownloadFailure(
                "NON_RETRYABLE_TECHNICAL", "UNSUPPORTED_SCHEMA_VERSION", "The requested output schema version is unsupported.",
                {"field": "processing.requestedOutputSchemaVersion", "reason": "unsupported schema version"}, False,
            )
        Processor._validate_deadline(payload)

    @staticmethod
    def _validate_video_request(request: dict[str, Any]) -> None:
        payload = request["payload"]
        input_data = payload["input"]
        processing = payload["processing"]
        if request["subjectId"] != input_data["videoId"]:
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_EXPECTED_OBJECT", "The requested object identity is inconsistent.",
                {"field": "input.videoId", "reason": "subject mismatch"}, False,
            )
        if input_data["mediaType"] != "video/mp4":
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_EXPECTED_OBJECT", "The requested media type is unsupported.",
                {"field": "input.mediaType", "reason": "unsupported media type"}, False,
            )
        if processing["analysisProfile"] != "DELIVERY_EVIDENCE_DEFAULT":
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_EXPECTED_OBJECT", "The requested analysis profile is unsupported.",
                {"field": "processing.analysisProfile", "reason": "unsupported analysis profile"}, False,
            )
        if processing["requestedOutputSchema"] != "m4trust.video-analysis-result":
            raise DownloadFailure(
                "NON_RETRYABLE_TECHNICAL", "UNSUPPORTED_SCHEMA_VERSION", "The requested output schema is unsupported.",
                {"field": "processing.requestedOutputSchema", "reason": "unsupported output schema"}, False,
            )
        if processing["requestedOutputSchemaVersion"] != "1.0.0":
            raise DownloadFailure(
                "NON_RETRYABLE_TECHNICAL", "UNSUPPORTED_SCHEMA_VERSION", "The requested output schema version is unsupported.",
                {"field": "processing.requestedOutputSchemaVersion", "reason": "unsupported schema version"}, False,
            )
        Processor._validate_deadline(payload)

    @staticmethod
    def _validate_deadline(payload: dict[str, Any]) -> None:
        deadline = datetime.fromisoformat(payload["deadlineAt"].replace("Z", "+00:00"))
        if deadline <= datetime.now(timezone.utc):
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_DEADLINE", "The processing deadline has expired.",
                {"field": "deadlineAt", "reason": "expired"}, False,
            )
