from __future__ import annotations

import copy

from m4trust_mock_worker.processing import DocumentDownloader, Processor, ScenarioSelector


def processor(contracts, scenario="auto"):
    return Processor(contracts, DocumentDownloader(2, 2, backoff=lambda _: None), ScenarioSelector(scenario))


def test_local_host_override_preserves_the_signed_host_header():
    downloader = DocumentDownloader(2, 1, host_override="host.docker.internal")

    connection_url, host_header = downloader._connection_target(
        "http://localhost:9000/bucket/object?X-Amz-Signature=signed"
    )

    assert connection_url == (
        "http://host.docker.internal:9000/bucket/object?X-Amz-Signature=signed"
    )
    assert host_header == "localhost:9000"


def test_success_downloads_and_emits_correlated_contract_valid_result(
    contracts, request_event, download_server
):
    request_event["payload"]["input"]["download"]["url"] = download_server

    messages = processor(contracts).process(request_event)

    assert len(messages) == 1
    routing_key, event = messages[0]
    assert routing_key == "ai.document-extraction.completed.v1"
    contracts.validate_completed(event)
    assert event["jobId"] == request_event["jobId"]
    assert event["tenantId"] == request_event["tenantId"]
    assert event["transactionId"] == request_event["transactionId"]
    assert event["subjectId"] == request_event["subjectId"]
    assert event["correlationId"] == request_event["correlationId"]
    assert event["causationId"] == request_event["eventId"]
    assert event["payload"]["result"]["document"]["contentSha256"] == request_event["payload"]["input"]["sha256"]
    assert event["payload"]["result"]["rules"][0]["legalBasis"]["source"] == "tbk-6098"
    assert "scenario" not in repr(event).lower()


def test_retryable_failure_is_selected_by_file_name_after_real_download(
    contracts, request_event, download_server
):
    request_event["payload"]["input"]["download"]["url"] = download_server
    request_event["payload"]["input"]["fileName"] = "fail-retryable-contract.pdf"

    messages = processor(contracts).process(request_event)

    routing_key, event = messages[0]
    assert routing_key == "ai.document-extraction.failed.v1"
    assert event["payload"]["error"]["category"] == "RETRYABLE_TECHNICAL"
    assert event["payload"]["error"]["retryRecommended"] is True
    contracts.validate_failed(event)


def test_duplicate_scenario_publishes_same_event_identity_twice(
    contracts, request_event, download_server
):
    request_event["payload"]["input"]["download"]["url"] = download_server
    request_event["payload"]["input"]["fileName"] = "duplicate-contract.pdf"

    messages = processor(contracts).process(request_event)

    assert len(messages) == 2
    assert messages[0] == messages[1]
    assert messages[0][1]["eventId"] == messages[1][1]["eventId"]


def test_hash_mismatch_becomes_safe_non_retryable_failure(
    contracts, request_event, download_server
):
    request_event["payload"]["input"]["download"]["url"] = download_server
    request_event["payload"]["input"]["sha256"] = "0" * 64

    messages = processor(contracts).process(request_event)

    _, event = messages[0]
    assert event["payload"]["error"] == {
        "category": "NON_RETRYABLE_TECHNICAL",
        "code": "CONTENT_HASH_MISMATCH",
        "message": "The downloaded object hash does not match the request.",
        "retryRecommended": False,
        "details": {"field": "input.sha256", "reason": "content hash mismatch"},
    }
    contracts.validate_failed(event)


def test_size_mismatch_is_invalid_expected_object(contracts, request_event, download_server):
    request_event["payload"]["input"]["download"]["url"] = download_server
    request_event["payload"]["input"]["sizeBytes"] += 1

    _, event = processor(contracts).process(request_event)[0]

    assert event["payload"]["error"]["code"] == "INVALID_EXPECTED_OBJECT"
    contracts.validate_failed(event)


def test_config_can_select_duplicate_without_contract_pollution(contracts, request_event, download_server):
    request_event["payload"]["input"]["download"]["url"] = download_server

    messages = processor(contracts, "duplicate").process(copy.deepcopy(request_event))

    assert len(messages) == 2
    assert all("scenario" not in repr(event).lower() for _, event in messages)


def test_subject_document_identity_mismatch_fails_before_download(contracts, request_event):
    request_event["payload"]["input"]["documentId"] = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

    _, event = processor(contracts).process(request_event)[0]

    assert event["payload"]["error"]["code"] == "INVALID_EXPECTED_OBJECT"
    assert event["causationId"] == request_event["eventId"]
    contracts.validate_failed(event)


def test_unsupported_requested_output_schema_version_is_stable_failure(contracts, request_event):
    request_event["payload"]["processing"]["requestedOutputSchemaVersion"] = "2.0.0"

    _, event = processor(contracts).process(request_event)[0]

    assert event["payload"]["error"]["code"] == "UNSUPPORTED_SCHEMA_VERSION"
    assert event["payload"]["error"]["retryRecommended"] is False
    contracts.validate_failed(event)
