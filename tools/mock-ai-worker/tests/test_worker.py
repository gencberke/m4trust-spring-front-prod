from __future__ import annotations

import json
from types import SimpleNamespace
from unittest.mock import Mock

from m4trust_mock_worker.processing import DocumentDownloader, Processor, ScenarioSelector
from m4trust_mock_worker.worker import RabbitWorker


def make_worker(contracts):
    processor = Processor(contracts, DocumentDownloader(1, 1, backoff=lambda _: None), ScenarioSelector("success"))
    return RabbitWorker(Mock(), processor)


def test_ack_happens_only_after_persistent_confirmed_publish(
    contracts, request_event, download_server
):
    request_event["payload"]["input"]["download"]["url"] = download_server
    channel = Mock()
    channel.basic_publish.return_value = None
    method = SimpleNamespace(delivery_tag=7, redelivered=False)

    make_worker(contracts)._handle(channel, method, None, json.dumps(request_event).encode())

    channel.basic_ack.assert_called_once_with(delivery_tag=7)
    channel.basic_nack.assert_not_called()
    publish = channel.basic_publish.call_args.kwargs
    assert publish["mandatory"] is True
    assert publish["properties"].content_type == "application/json"
    assert publish["properties"].delivery_mode == 2


def test_contract_invalid_request_is_dead_lettered_without_publish(contracts, request_event):
    del request_event["jobId"]
    channel = Mock()
    method = SimpleNamespace(delivery_tag=9, redelivered=False)

    make_worker(contracts)._handle(channel, method, None, json.dumps(request_event).encode())

    channel.basic_publish.assert_not_called()
    channel.basic_ack.assert_not_called()
    channel.basic_nack.assert_called_once_with(delivery_tag=9, requeue=False)


def test_uncertain_publish_is_retried_once_then_dead_lettered(contracts, request_event, download_server):
    request_event["payload"]["input"]["download"]["url"] = download_server
    channel = Mock()
    channel.basic_publish.side_effect = RuntimeError("unsafe detail must not be logged")
    worker = make_worker(contracts)

    worker._handle(channel, SimpleNamespace(delivery_tag=1, redelivered=False), None, json.dumps(request_event).encode())
    worker._handle(channel, SimpleNamespace(delivery_tag=2, redelivered=True), None, json.dumps(request_event).encode())

    assert channel.basic_nack.call_args_list[0].kwargs == {"delivery_tag": 1, "requeue": True}
    assert channel.basic_nack.call_args_list[1].kwargs == {"delivery_tag": 2, "requeue": False}
