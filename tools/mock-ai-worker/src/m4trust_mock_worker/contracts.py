from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from jsonschema import FormatChecker
from jsonschema.exceptions import ValidationError
from jsonschema.validators import validator_for
from referencing import Registry, Resource

DOCUMENT_JOB_TYPE = "DOCUMENT_EXTRACTION"
VIDEO_JOB_TYPE = "VIDEO_ANALYSIS"


@dataclass(frozen=True)
class JobContractSet:
    request_schema_id: str
    completed_schema_id: str
    failed_schema_id: str
    examples_dir: str


JOB_CONTRACTS = {
    DOCUMENT_JOB_TYPE: JobContractSet(
        request_schema_id="https://schemas.m4trust.internal/ai/document-extraction/requested-event/1.0.0",
        completed_schema_id="https://schemas.m4trust.internal/ai/document-extraction/completed-event/1.0.0",
        failed_schema_id="https://schemas.m4trust.internal/ai/document-extraction/failed-event/1.0.0",
        examples_dir="document-extraction",
    ),
    VIDEO_JOB_TYPE: JobContractSet(
        request_schema_id="https://schemas.m4trust.internal/ai/video-analysis/requested-event/1.0.0",
        completed_schema_id="https://schemas.m4trust.internal/ai/video-analysis/completed-event/1.0.0",
        failed_schema_id="https://schemas.m4trust.internal/ai/video-analysis/failed-event/1.0.0",
        examples_dir="video-analysis",
    ),
}


class ContractViolation(ValueError):
    pass


class ContractValidator:
    def __init__(self, contracts_dir: Path):
        self._contracts_dir = contracts_dir
        schemas = [_load_json(path) for path in (contracts_dir / "schemas").rglob("*.schema.json")]
        registry = Registry()
        for schema in schemas:
            registry = registry.with_resource(schema["$id"], Resource.from_contents(schema))
        by_id = {schema["$id"]: schema for schema in schemas}
        schema_ids = {
            schema_id
            for contract in JOB_CONTRACTS.values()
            for schema_id in (
                contract.request_schema_id,
                contract.completed_schema_id,
                contract.failed_schema_id,
            )
        }
        self._validators = {
            schema_id: validator_for(by_id[schema_id])(
                by_id[schema_id], registry=registry, format_checker=FormatChecker()
            )
            for schema_id in schema_ids
        }

    def validate_request(self, event: dict[str, Any]) -> None:
        self._validate(self._contracts(event["jobType"]).request_schema_id, event)

    def validate_completed(self, event: dict[str, Any]) -> None:
        self._validate(self._contracts(event["jobType"]).completed_schema_id, event)

    def validate_failed(self, event: dict[str, Any]) -> None:
        self._validate(self._contracts(event["jobType"]).failed_schema_id, event)

    def fixture_payload(self, name: str, job_type: str = DOCUMENT_JOB_TYPE) -> dict[str, Any]:
        examples_dir = self._contracts(job_type).examples_dir
        return _load_json(self._contracts_dir / "examples" / examples_dir / name)["payload"]

    def _contracts(self, job_type: str) -> JobContractSet:
        try:
            return JOB_CONTRACTS[job_type]
        except KeyError as error:
            raise ContractViolation("Unsupported job type") from error

    def _validate(self, schema_id: str, event: dict[str, Any]) -> None:
        try:
            self._validators[schema_id].validate(event)
        except ValidationError as error:
            raise ContractViolation("Message does not satisfy the committed contract") from error


def _load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as source:
        return json.load(source)
