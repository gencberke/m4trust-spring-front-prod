#!/usr/bin/env python3
"""Lightweight validation for M4Trust JSON Schemas and canonical fixtures."""

from __future__ import annotations

import json
import sys
import warnings
from pathlib import Path
from typing import Any

warnings.filterwarnings("ignore", message="jsonschema.RefResolver is deprecated", category=DeprecationWarning)

try:
    import jsonschema
    from jsonschema import Draft202012Validator, FormatChecker, RefResolver
except ImportError as exc:  # pragma: no cover - exercised by the CLI error path
    print("Missing dependency: install contracts/requirements.txt (jsonschema).", file=sys.stderr)
    raise SystemExit(2) from exc


ROOT = Path(__file__).resolve().parents[1]
SCHEMA_ROOT = ROOT / "schemas"
EXAMPLE_ROOT = ROOT / "examples"


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError("top-level JSON value must be an object")
    return value


def file_uri(path: Path) -> str:
    return path.resolve().as_uri()


def load_schema_store(paths: list[Path]) -> dict[str, dict[str, Any]]:
    store: dict[str, dict[str, Any]] = {}
    for path in paths:
        schema = read_json(path)
        store[file_uri(path)] = schema
        schema_id = schema.get("$id")
        if isinstance(schema_id, str):
            store[schema_id] = schema
    return store


def validator_for(schema_path: Path, schema: dict[str, Any], store: dict[str, dict[str, Any]]) -> Draft202012Validator:
    resolver = RefResolver(
        base_uri=file_uri(schema_path),
        referrer=schema,
        store=store,
    )
    return Draft202012Validator(schema, resolver=resolver, format_checker=FormatChecker())


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


def validate() -> int:
    schema_paths = sorted(SCHEMA_ROOT.rglob("*.schema.json"))
    if not schema_paths:
        print("No JSON Schema files found.", file=sys.stderr)
        return 1

    store = load_schema_store(schema_paths)
    failures: list[str] = []

    for path in schema_paths:
        try:
            schema = read_json(path)
            Draft202012Validator.check_schema(schema)
            print(f"PASS schema {path.relative_to(ROOT)}")
        except Exception as exc:  # noqa: BLE001 - this is a CLI validator
            failures.append(f"FAIL schema {path.relative_to(ROOT)}: {exc}")

    for fixture_name, schema_name in FIXTURE_SCHEMAS.items():
        fixture_path = ROOT / fixture_name
        schema_path = ROOT / schema_name
        try:
            instance = read_json(fixture_path)
            schema = read_json(schema_path)
            validator = validator_for(schema_path, schema, store)
            errors = sorted(validator.iter_errors(instance), key=lambda error: list(error.path))
            if errors:
                details = "; ".join(f"{'/'.join(map(str, error.path)) or '<root>'}: {error.message}" for error in errors)
                failures.append(f"FAIL fixture {fixture_path.relative_to(ROOT)}: {details}")
            else:
                print(f"PASS fixture {fixture_path.relative_to(ROOT)}")
        except Exception as exc:  # noqa: BLE001 - this is a CLI validator
            failures.append(f"FAIL fixture {fixture_path.relative_to(ROOT)}: {exc}")

    if failures:
        print("\n".join(failures), file=sys.stderr)
        print(f"Validation failed: {len(failures)} problem(s).", file=sys.stderr)
        return 1

    print(f"Validation succeeded: {len(schema_paths)} schemas and {len(FIXTURE_SCHEMAS)} fixtures.")
    return 0


if __name__ == "__main__":
    sys.exit(validate())
