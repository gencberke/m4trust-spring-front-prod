#!/usr/bin/env python3
"""Structural OpenAPI comparison for committed vs runtime (or two documents).

Compares paths, methods, parameters (name/in/required), security requirements,
response status codes, media types, and schema $refs. Description text and
example ordering are ignored.
"""

from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError as exc:  # pragma: no cover
    print("Missing dependency: install contracts/requirements.txt (PyYAML).", file=sys.stderr)
    raise SystemExit(2) from exc

HTTP_METHODS = ("get", "put", "post", "delete", "options", "head", "patch", "trace")


def load_openapi(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        document = json.loads(text)
    else:
        document = yaml.safe_load(text)
    if not isinstance(document, dict):
        raise ValueError(f"{path}: OpenAPI root must be a mapping")
    return document


def server_base_path(document: dict[str, Any]) -> str:
    servers = document.get("servers") or []
    if not servers:
        return ""
    url = str(servers[0].get("url") or "").rstrip("/")
    if url.startswith("http://") or url.startswith("https://"):
        # Keep only the path component of an absolute server URL.
        from urllib.parse import urlparse

        return urlparse(url).path.rstrip("/")
    return url


def normalize_path(path: str, base: str) -> str:
    import re

    normalized = path if path.startswith("/") else f"/{path}"
    if base and normalized.startswith(base + "/"):
        normalized = normalized[len(base) :]
    elif base and normalized == base:
        normalized = "/"
    elif normalized.startswith("/api/v1/"):
        # Runtime springdoc emits absolute servlet paths including /api/v1.
        normalized = normalized[len("/api/v1") :]
    elif normalized == "/api/v1":
        normalized = "/"
    # Path-parameter names may differ between design YAML and servlet reflection;
    # compare positionally (matches Java OpenApiStructuralFingerprint).
    return re.sub(r"\{[^}/]+\}", "{}", normalized)


def schema_ref(node: Any) -> str | None:
    if not isinstance(node, dict):
        return None
    ref = node.get("$ref")
    if isinstance(ref, str):
        return ref
    for key in ("allOf", "oneOf", "anyOf"):
        items = node.get(key)
        if isinstance(items, list):
            for item in items:
                found = schema_ref(item)
                if found:
                    return found
    items = node.get("items")
    if isinstance(items, dict):
        return schema_ref(items)
    return None


def content_media_types(content: Any) -> set[str]:
    if not isinstance(content, dict):
        return set()
    return set(content.keys())


def content_schema_refs(content: Any) -> set[str]:
    refs: set[str] = set()
    if not isinstance(content, dict):
        return refs
    for media in content.values():
        if isinstance(media, dict):
            ref = schema_ref(media.get("schema"))
            if ref:
                refs.add(ref)
    return refs


def normalize_security(security: Any) -> list[tuple[str, tuple[str, ...]]]:
    if security is None:
        return []
    if not isinstance(security, list):
        return [("__invalid__", ())]
    normalized: list[tuple[str, tuple[str, ...]]] = []
    for requirement in security:
        if not isinstance(requirement, dict) or not requirement:
            normalized.append(("__empty__", ()))
            continue
        for name in sorted(requirement.keys()):
            scopes = requirement.get(name) or []
            if not isinstance(scopes, list):
                scopes = [str(scopes)]
            normalized.append((str(name), tuple(str(scope) for scope in scopes)))
    return sorted(normalized)


def extract_parameters(operation: dict[str, Any]) -> set[tuple[str, str, bool]]:
    params: set[tuple[str, str, bool]] = set()
    for parameter in operation.get("parameters") or []:
        if not isinstance(parameter, dict):
            continue
        if "$ref" in parameter:
            # Keep only the local name so ordering/description of shared params
            # does not create noise; identity is still comparable.
            ref = str(parameter["$ref"]).rsplit("/", 1)[-1]
            params.add((ref, "ref", True))
            continue
        name = parameter.get("name")
        location = parameter.get("in")
        if not isinstance(name, str) or not isinstance(location, str):
            continue
        if location not in {"path", "query", "header", "cookie"}:
            continue
        required = bool(parameter.get("required", location == "path"))
        params.add((name, location, required))
    return params


def extract_operation_structure(operation: dict[str, Any]) -> dict[str, Any]:
    responses = operation.get("responses") or {}
    status_codes = {str(code) for code in responses.keys()}
    media_types: set[str] = set()
    schema_refs: set[str] = set()
    for response in responses.values():
        if not isinstance(response, dict):
            continue
        if "$ref" in response:
            schema_refs.add(str(response["$ref"]))
            continue
        content = response.get("content")
        media_types |= content_media_types(content)
        schema_refs |= content_schema_refs(content)

    request_body = operation.get("requestBody")
    if isinstance(request_body, dict):
        if "$ref" in request_body:
            schema_refs.add(str(request_body["$ref"]))
        else:
            content = request_body.get("content")
            media_types |= content_media_types(content)
            schema_refs |= content_schema_refs(content)

    return {
        "parameters": extract_parameters(operation),
        "security": normalize_security(operation.get("security")),
        "status_codes": status_codes,
        "media_types": media_types,
        "schema_refs": schema_refs,
    }


def extract_structure(document: dict[str, Any], *, path_prefix_filter: str | None = None) -> dict[tuple[str, str], dict[str, Any]]:
    base = server_base_path(document)
    structure: dict[tuple[str, str], dict[str, Any]] = {}
    for raw_path, item in (document.get("paths") or {}).items():
        if not isinstance(item, dict):
            continue
        path = normalize_path(str(raw_path), base)
        if path_prefix_filter and not path.startswith(path_prefix_filter):
            # Runtime docs may include actuator/error; keep only public API when asked.
            if not path.startswith(path_prefix_filter.rstrip("/") ):
                continue
        for method in HTTP_METHODS:
            operation = item.get(method)
            if isinstance(operation, dict):
                structure[(path, method)] = extract_operation_structure(operation)
    return structure


def compare_structures(
    expected: dict[tuple[str, str], dict[str, Any]],
    actual: dict[tuple[str, str], dict[str, Any]],
) -> list[str]:
    diffs: list[str] = []
    expected_keys = set(expected)
    actual_keys = set(actual)
    for path, method in sorted(expected_keys - actual_keys):
        diffs.append(f"missing operation: {method.upper()} {path}")
    for path, method in sorted(actual_keys - expected_keys):
        diffs.append(f"unexpected operation: {method.upper()} {path}")
    for key in sorted(expected_keys & actual_keys):
        path, method = key
        left = expected[key]
        right = actual[key]
        if left["parameters"] != right["parameters"]:
            diffs.append(
                f"parameter drift {method.upper()} {path}: "
                f"expected={sorted(left['parameters'])} actual={sorted(right['parameters'])}"
            )
        if left["security"] != right["security"]:
            diffs.append(
                f"security drift {method.upper()} {path}: "
                f"expected={left['security']} actual={right['security']}"
            )
        if left["status_codes"] != right["status_codes"]:
            diffs.append(
                f"status drift {method.upper()} {path}: "
                f"expected={sorted(left['status_codes'])} actual={sorted(right['status_codes'])}"
            )
        if left["media_types"] != right["media_types"]:
            diffs.append(
                f"media-type drift {method.upper()} {path}: "
                f"expected={sorted(left['media_types'])} actual={sorted(right['media_types'])}"
            )
        if left["schema_refs"] != right["schema_refs"]:
            diffs.append(
                f"schema $ref drift {method.upper()} {path}: "
                f"expected={sorted(left['schema_refs'])} actual={sorted(right['schema_refs'])}"
            )
    return diffs


def inject_fake_path(document: dict[str, Any]) -> dict[str, Any]:
    mutated = copy.deepcopy(document)
    paths = mutated.setdefault("paths", {})
    paths["/__drift_probe__/fake"] = {
        "get": {
            "operationId": "driftProbeFake",
            "responses": {"200": {"description": "probe"}},
        }
    }
    return mutated


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected", type=Path, required=True, help="Committed OpenAPI YAML/JSON")
    parser.add_argument("--actual", type=Path, required=True, help="Runtime or other OpenAPI YAML/JSON")
    parser.add_argument(
        "--negative-fixture",
        action="store_true",
        help="Mutate a copy of expected with a fake path and require comparison failure",
    )
    args = parser.parse_args(argv)

    expected_doc = load_openapi(args.expected)
    if args.negative_fixture:
        actual_doc = inject_fake_path(expected_doc)
        expected_structure = extract_structure(expected_doc)
        actual_structure = extract_structure(actual_doc)
        diffs = compare_structures(expected_structure, actual_structure)
        if not diffs:
            print("FAIL negative fixture: expected structural drift was not detected", file=sys.stderr)
            return 1
        print(f"PASS negative fixture detected {len(diffs)} drift(s)")
        for diff in diffs:
            print(f"  {diff}")
        return 0

    actual_doc = load_openapi(args.actual)
    diffs = compare_structures(extract_structure(expected_doc), extract_structure(actual_doc))
    if diffs:
        print(f"FAIL OpenAPI structural drift: {len(diffs)} difference(s)", file=sys.stderr)
        for diff in diffs:
            print(diff, file=sys.stderr)
        return 1
    print("PASS OpenAPI structural comparison")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
