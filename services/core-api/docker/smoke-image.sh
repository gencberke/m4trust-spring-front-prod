#!/usr/bin/env bash
# Smoke-check a built Core API image for non-root runtime, packaged schemas,
# and ADR-016 complete packaged contract-bundle digest vs source + image label.
# Usage: ./services/core-api/docker/smoke-image.sh m4trust-core-api:<tag>
# Requires host tools: docker, jar (JDK), python3; repo root as cwd for source digest.
set -euo pipefail

IMAGE="${1:?image tag required}"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
WORKDIR="$(mktemp -d)"
cleanup() { rm -rf "${WORKDIR}"; }
trap cleanup EXIT

PYTHON_BIN="${PYTHON_BIN:-}"
if [ -z "${PYTHON_BIN}" ]; then
  PYTHON_BIN="$(command -v python3 || command -v python || true)"
fi
if [ -z "${PYTHON_BIN}" ]; then
  echo "python3/python required for smoke digest (set PYTHON_BIN if needed)" >&2
  exit 1
fi

echo "Checking non-root user..."
user="$(docker image inspect "${IMAGE}" --format '{{ .Config.User }}')"
test "${user}" = "m4trust"

echo "Reading contract-bundle digest label..."
label_digest="$(docker image inspect "${IMAGE}" --format '{{ index .Config.Labels "io.m4trust.contract-bundle-digest" }}')"
test -n "${label_digest}"
test "${label_digest}" != "unknown"

echo "Computing source contract-bundle digest..."
source_digest="$("${PYTHON_BIN}" "${ROOT}/contracts/scripts/validate_contracts.py" --print-digest)"
test -n "${source_digest}"

echo "Extracting application JAR from image..."
cid="$(docker create "${IMAGE}")"
docker cp "${cid}:/app/app.jar" "${WORKDIR}/app.jar"
docker rm "${cid}" >/dev/null

echo "Extracting packaged contracts and parsing every schema JSON..."
mkdir -p "${WORKDIR}/extract"
(
  cd "${WORKDIR}/extract"
  jar tf "${WORKDIR}/app.jar" | grep -E '^BOOT-INF/classes/contracts/' > contracts.txt
  test -s contracts.txt
  while IFS= read -r entry; do
    jar xf "${WORKDIR}/app.jar" "${entry}"
  done < contracts.txt
)
"${PYTHON_BIN}" - "${WORKDIR}/extract/BOOT-INF/classes/contracts/schemas" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
files = sorted(root.rglob("*.json"))
if not files:
    print("no schemas extracted", file=sys.stderr)
    sys.exit(1)
for path in files:
    json.loads(path.read_text(encoding="utf-8"))
print(f"parsed {len(files)} runtime schema(s)")
PY

echo "Recomputing complete packaged bundle digest from JAR classpath contracts..."
packaged_digest="$("${PYTHON_BIN}" - "${WORKDIR}/extract/BOOT-INF/classes/contracts" "${ROOT}/contracts/scripts/validate_contracts.py" <<'PY'
import importlib.util
import pathlib
import sys

contracts_root = pathlib.Path(sys.argv[1])
validator = pathlib.Path(sys.argv[2])
spec = importlib.util.spec_from_file_location("validate_contracts", validator)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)
print(module.contract_bundle_digest(contracts_root), end="")
PY
)"

echo "source=${source_digest}"
echo "label=${label_digest}"
echo "packaged=${packaged_digest}"
test "${packaged_digest}" = "${source_digest}"
test "${packaged_digest}" = "${label_digest}"

echo "Smoke OK for ${IMAGE}"
