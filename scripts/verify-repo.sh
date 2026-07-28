#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
started_at="$(date +%s)"
initial_status="$(git -C "$repo_root" status --porcelain=v1)"
base_sha="${M4TRUST_BASE_SHA:-}"
if [[ -z "$base_sha" ]]; then
  base_sha="$(git -C "$repo_root" merge-base HEAD origin/main 2>/dev/null || true)"
fi
if [[ -z "$base_sha" ]]; then
  base_sha="$(git -C "$repo_root" rev-parse HEAD)"
fi
base_openapi="$(mktemp)"
trap 'rm -f "$base_openapi"' EXIT
git -C "$repo_root" show \
  "$base_sha:contracts/openapi/core-api-v1.yaml" > "$base_openapi"

if [[ -n "${M4TRUST_PYTHON:-}" ]]; then
  contract_python="$M4TRUST_PYTHON"
  if [[ "$contract_python" != /* && "$contract_python" == */* ]]; then
    contract_python="$repo_root/$contract_python"
  fi
  export M4TRUST_PYTHON="$contract_python"
elif [[ -x "$repo_root/contracts/.venv/bin/python" ]]; then
  contract_python="$repo_root/contracts/.venv/bin/python"
else
  contract_python="python3"
fi

if [[ -n "${M4TRUST_TOOLS_PYTHON:-}" ]]; then
  tools_python="$M4TRUST_TOOLS_PYTHON"
  if [[ "$tools_python" != /* && "$tools_python" == */* ]]; then
    tools_python="$repo_root/$tools_python"
  fi
elif [[ -n "${M4TRUST_PYTHON:-}" ]]; then
  tools_python="$M4TRUST_PYTHON"
elif [[ -x "$repo_root/tools/mock-ai-worker/.venv/bin/python" ]]; then
  tools_python="$repo_root/tools/mock-ai-worker/.venv/bin/python"
else
  tools_python="python3"
fi

run_stage() {
  local label="$1"
  shift
  local stage_started
  local stage_finished
  stage_started="$(date +%s)"
  echo "=== $label ==="
  "$@"
  stage_finished="$(date +%s)"
  echo "PASS $label ($((stage_finished - stage_started))s)"
}

run_stage "contracts" \
  "$contract_python" "$repo_root/contracts/scripts/validate_contracts.py"
run_stage "contract negative fixture" \
  "$contract_python" "$repo_root/contracts/scripts/compare_openapi_structure.py" \
  --expected "$repo_root/contracts/openapi/core-api-v1.yaml" \
  --actual "$repo_root/contracts/openapi/core-api-v1.yaml" \
  --negative-fixture
run_stage "OpenAPI compatibility against $base_sha" \
  "$contract_python" "$repo_root/contracts/scripts/compare_openapi_structure.py" \
  --expected "$base_openapi" \
  --actual "$repo_root/contracts/openapi/core-api-v1.yaml"

run_stage "core API" \
  "$repo_root/services/core-api/mvnw" \
  --batch-mode --no-transfer-progress \
  --file "$repo_root/services/core-api/pom.xml" verify

run_stage "frontend generated contract" \
  npm --prefix "$repo_root/frontend" run generate:api:check
run_stage "frontend lint" npm --prefix "$repo_root/frontend" run lint
run_stage "frontend format" npm --prefix "$repo_root/frontend" run format:check
run_stage "frontend tests" npm --prefix "$repo_root/frontend" run test
run_stage "frontend build" npm --prefix "$repo_root/frontend" run build:check

run_stage "mock AI worker tests" \
  env PYTHONPATH="$repo_root/tools/mock-ai-worker/src" \
  "$tools_python" -m pytest "$repo_root/tools/mock-ai-worker/tests"
run_stage "Moka emulator tests" \
  env PYTHONPATH="$repo_root/tools/moka-emulator/src" \
  "$tools_python" -m unittest discover \
  -s "$repo_root/tools/moka-emulator/tests" -p "test_*.py" -v

run_stage "Markdown references" \
  "$contract_python" "$repo_root/scripts/check-markdown-links.py"
run_stage "architecture references" \
  "$contract_python" "$repo_root/scripts/check-architecture-references.py"
run_stage "Flyway history" \
  "$contract_python" "$repo_root/scripts/check-flyway-history.py" \
  --base "$base_sha"
run_stage "repository map" \
  "$contract_python" "$repo_root/scripts/generate-repo-map.py" --check
run_stage "whitespace" git -C "$repo_root" diff --check

final_status="$(git -C "$repo_root" status --porcelain=v1)"
if [[ "$final_status" != "$initial_status" ]]; then
  echo "FAIL validation changed the working tree" >&2
  diff <(printf '%s\n' "$initial_status") <(printf '%s\n' "$final_status") || true
  exit 1
fi

finished_at="$(date +%s)"
echo "PASS repository validation ($((finished_at - started_at))s total)"
