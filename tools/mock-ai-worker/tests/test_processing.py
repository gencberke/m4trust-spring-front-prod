from m4trust_mock_worker.processing import DocumentDownloader, Processor, ScenarioSelector


def test_document_success_downloads_and_emits_a_contract_valid_correlated_result(
    contracts, request_event, download_server
):
    request_event["payload"]["input"]["download"]["url"] = download_server

    messages = Processor(
        contracts, DocumentDownloader(2, 2, backoff=lambda _: None), ScenarioSelector("success")
    ).process(request_event)

    assert len(messages) == 1
    routing_key, event = messages[0]
    assert routing_key == "ai.document-extraction.completed.v1"
    contracts.validate_completed(event)
    assert event["jobId"] == request_event["jobId"]
    assert event["causationId"] == request_event["eventId"]
    assert event["payload"]["result"]["document"]["contentSha256"] == request_event["payload"]["input"]["sha256"]
