#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
project_name="m4trust-local"
compose_file="$repo_root/infra/compose.yaml"

dry_run=0
if [[ "${1:-}" == "--dry-run" ]]; then
  dry_run=1
fi

cd "$repo_root"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker CLI not found. Install and start Docker before resetting local infrastructure." >&2
  exit 1
fi

target="local Docker Compose project '$project_name' (containers, network, and named volumes)"
operation="docker compose --project-name $project_name --file $compose_file down --volumes --remove-orphans"

if [[ "$dry_run" -eq 1 ]]; then
  echo "Would remove only $target"
  echo "  $operation"
  exit 0
fi

echo "Removing only $target..."
docker compose \
  --project-name "$project_name" \
  --file "$compose_file" \
  down --volumes --remove-orphans

echo "Local infrastructure project '$project_name' was reset."
