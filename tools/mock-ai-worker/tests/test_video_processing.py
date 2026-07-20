import copy

import pytest

from m4trust_mock_worker.contracts import ContractViolation
from m4trust_mock_worker.processing import DocumentDownloader, Processor, ScenarioSelector


def processor(contracts, scenario="auto"):
    return Processor(contracts, DocumentDownloader(2, 2, backoff=lambda _: None), ScenarioSelector(scenario))


def test_success_downloads_and_emits_correlated_contract_valid_result(
    contracts, video_request_event, video_download_server
):
    video_request_event["payload"]["input"]["download"]["url"] = video_download_server

    messages = processor(contracts).process(video_request_event)

    assert len(messages) == 1
    routing_key, event = messages[0]
    assert routing_key == "ai.video-analysis.completed.v1"
    contracts.validate_completed(event)
    assert event["jobId"] == video_request_event["jobId"]
    assert event["subjectId"] == video_request_event["subjectId"]
    assert event["producer"]["service"] == "m4trust-ai-worker"
    assert event["payload"]["result"]["summary"]["advisoryOutcome"] == "NO_ISSUE_DETECTED"


def test_warning_scenario_emits_advisory_completed_result(
    contracts, video_request_event, video_download_server
):
    video_request_event["payload"]["input"]["download"]["url"] = video_download_server
    video_request_event["payload"]["input"]["fileName"] = "warning-delivery.mp4"

    routing_key, event = processor(contracts).process(video_request_event)[0]

    assert routing_key == "ai.video-analysis.completed.v1"
    assert event["payload"]["result"]["summary"]["advisoryOutcome"] == "REVIEW_SUGGESTED"
    assert event["payload"]["warnings"]
    contracts.validate_completed(event)


def test_retryable_failure_is_selected_by_file_name_after_real_download(
    contracts, video_request_event, video_download_server
):
    video_request_event["payload"]["input"]["download"]["url"] = video_download_server
    video_request_event["payload"]["input"]["fileName"] = "fail-retryable-delivery.mp4"

    routing_key, event = processor(contracts).process(video_request_event)[0]

    assert routing_key == "ai.video-analysis.failed.v1"
    assert event["payload"]["error"]["retryRecommended"] is True
    contracts.validate_failed(event)


def test_duplicate_scenario_publishes_same_event_identity_twice(
    contracts, video_request_event, video_download_server
):
    video_request_event["payload"]["input"]["download"]["url"] = video_download_server
    video_request_event["payload"]["input"]["fileName"] = "duplicate-delivery.mp4"

    messages = processor(contracts).process(video_request_event)

    assert len(messages) == 2
    assert messages[0] == messages[1]


def test_subject_video_identity_mismatch_fails_before_download(contracts, video_request_event):
    video_request_event["payload"]["input"]["videoId"] = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

    _, event = processor(contracts).process(video_request_event)[0]

    assert event["payload"]["error"]["code"] == "INVALID_EXPECTED_OBJECT"
    contracts.validate_failed(event)


def test_non_mp4_media_type_is_rejected(contracts, video_request_event):
    video_request_event["payload"]["input"]["mediaType"] = "video/quicktime"

    _, event = processor(contracts).process(video_request_event)[0]

    assert event["payload"]["error"]["code"] == "INVALID_EXPECTED_OBJECT"
    contracts.validate_failed(event)


def test_unsupported_analysis_profile_is_rejected(contracts, video_request_event):
    video_request_event["payload"]["processing"]["analysisProfile"] = "UNKNOWN_PROFILE"

    with pytest.raises(ContractViolation):
        processor(contracts).process(video_request_event)


def test_unsupported_requested_output_schema_version_is_stable_failure(
    contracts, video_request_event
):
    video_request_event["payload"]["processing"]["requestedOutputSchemaVersion"] = "2.0.0"

    _, event = processor(contracts).process(video_request_event)[0]

    assert event["payload"]["error"]["code"] == "UNSUPPORTED_SCHEMA_VERSION"
    contracts.validate_failed(event)


def test_expired_deadline_is_rejected(contracts, video_request_event):
    video_request_event["payload"]["deadlineAt"] = "2020-01-01T00:00:00.000Z"

    _, event = processor(contracts).process(video_request_event)[0]

    assert event["payload"]["error"]["code"] == "INVALID_DEADLINE"
    contracts.validate_failed(event)


def test_hash_mismatch_becomes_safe_non_retryable_failure(
    contracts, video_request_event, video_download_server
):
    video_request_event["payload"]["input"]["download"]["url"] = video_download_server
    video_request_event["payload"]["input"]["sha256"] = "0" * 64

    _, event = processor(contracts).process(video_request_event)[0]

    assert event["payload"]["error"]["code"] == "CONTENT_HASH_MISMATCH"
    contracts.validate_failed(event)


def test_config_can_select_warning_without_contract_pollution(
    contracts, video_request_event, video_download_server
):
    video_request_event["payload"]["input"]["download"]["url"] = video_download_server

    _, event = processor(contracts, "warning").process(copy.deepcopy(video_request_event))[0]

    assert event["payload"]["result"]["summary"]["advisoryOutcome"] == "REVIEW_SUGGESTED"
    assert "scenario" not in repr(event).lower()
