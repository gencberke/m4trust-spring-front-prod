from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from jsonschema import FormatChecker
from jsonschema.exceptions import ValidationError
from jsonschema.validators import validator_for
from referencing import Registry, Resource

REQUEST_SCHEMA_ID = "https://schemas.m4trust.internal/ai/document-extraction/requested-event/1.0.0"
COMPLETED_SCHEMA_ID = "https://schemas.m4trust.internal/ai/document-extraction/completed-event/1.0.0"
FAILED_SCHEMA_ID = "https://schemas.m4trust.internal/ai/document-extraction/failed-event/1.0.0"


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
        self._validators = {
            schema_id: validator_for(by_id[schema_id])(
                by_id[schema_id], registry=registry, format_checker=FormatChecker()
            )
            for schema_id in (REQUEST_SCHEMA_ID, COMPLETED_SCHEMA_ID, FAILED_SCHEMA_ID)
        }

    def validate_request(self, event: dict[str, Any]) -> None:
        self._validate(REQUEST_SCHEMA_ID, event)

    def validate_completed(self, event: dict[str, Any]) -> None:
        self._validate(COMPLETED_SCHEMA_ID, event)

    def validate_failed(self, event: dict[str, Any]) -> None:
        self._validate(FAILED_SCHEMA_ID, event)

    def fixture_payload(self, name: str) -> dict[str, Any]:
        return _load_json(self._contracts_dir / "examples" / "document-extraction" / name)["payload"]

    def _validate(self, schema_id: str, event: dict[str, Any]) -> None:
        try:
            self._validators[schema_id].validate(event)
        except ValidationError as error:
            raise ContractViolation("Message does not satisfy the committed contract") from error


def _load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as source:
        return json.load(source)
