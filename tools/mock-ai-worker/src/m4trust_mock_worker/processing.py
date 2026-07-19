from __future__ import annotations

import copy
import hashlib
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Callable

from .contracts import ContractValidator


@dataclass(frozen=True)
class DownloadFailure(Exception):
    category: str
    code: str
    message: str
    details: dict[str, Any]
    retry_recommended: bool


class DocumentDownloader:
    def __init__(self, timeout_seconds: float, max_attempts: int, backoff: Callable[[float], None] = time.sleep):
        self._timeout_seconds = timeout_seconds
        self._max_attempts = max_attempts
        self._backoff = backoff

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
        for attempt in range(1, self._max_attempts + 1):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "m4trust-mock-ai-worker/1.0"})
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
        return "success"


class EventFactory:
    def __init__(self, contracts: ContractValidator):
        self._contracts = contracts

    def completed(self, request: dict[str, Any]) -> dict[str, Any]:
        payload = copy.deepcopy(self._contracts.fixture_payload("success-result.json"))
        payload["result"]["document"]["contentSha256"] = request["payload"]["input"]["sha256"].lower()
        event = self._envelope(request, "ai.job.completed.v1", payload, "completed")
        self._contracts.validate_completed(event)
        return event

    def retryable_failure(self, request: dict[str, Any]) -> dict[str, Any]:
        payload = copy.deepcopy(self._contracts.fixture_payload("retryable-failure.json"))
        event = self._envelope(request, "ai.job.failed.v1", payload, "failed")
        self._contracts.validate_failed(event)
        return event

    def download_failure(self, request: dict[str, Any], failure: DownloadFailure) -> dict[str, Any]:
        fixture = "retryable-failure.json" if failure.retry_recommended else "non-retryable-failure.json"
        payload = copy.deepcopy(self._contracts.fixture_payload(fixture))
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

    @staticmethod
    def _envelope(request: dict[str, Any], event_type: str, payload: dict[str, Any], suffix: str) -> dict[str, Any]:
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
            "producer": {"service": "m4trust-mock-ai-worker", "version": "1.0.0"},
            "payload": payload,
        }


class Processor:
    def __init__(self, contracts: ContractValidator, downloader: DocumentDownloader, selector: ScenarioSelector):
        self._contracts = contracts
        self._downloader = downloader
        self._selector = selector
        self._events = EventFactory(contracts)

    def process(self, request: dict[str, Any]) -> list[tuple[str, dict[str, Any]]]:
        self._contracts.validate_request(request)
        try:
            self._validate_expected_request(request)
            self._downloader.download_and_verify(request["payload"]["input"])
        except DownloadFailure as failure:
            event = self._events.download_failure(request, failure)
            return [("ai.document-extraction.failed.v1", event)]

        scenario = self._selector.select(request["payload"]["input"]["fileName"])
        if scenario == "retryable_failure":
            return [("ai.document-extraction.failed.v1", self._events.retryable_failure(request))]
        completed = self._events.completed(request)
        messages = [("ai.document-extraction.completed.v1", completed)]
        return messages * 2 if scenario == "duplicate" else messages

    @staticmethod
    def _validate_expected_request(request: dict[str, Any]) -> None:
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
        deadline = datetime.fromisoformat(payload["deadlineAt"].replace("Z", "+00:00"))
        if deadline <= datetime.now(timezone.utc):
            raise DownloadFailure(
                "INVALID_INPUT", "INVALID_DEADLINE", "The processing deadline has expired.",
                {"field": "deadlineAt", "reason": "expired"}, False,
            )
