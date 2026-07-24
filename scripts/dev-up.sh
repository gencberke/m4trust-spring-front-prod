#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
project_name="m4trust-local"
compose_file="$repo_root/infra/compose.yaml"

cd "$repo_root"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker CLI not found. Install and start Docker before running dev-up." >&2
  exit 1
fi

echo "Starting local infrastructure (project: $project_name, profile: mock-ai)..."
docker compose \
  --project-name "$project_name" \
  --file "$compose_file" \
  --profile mock-ai \
  up --detach --build

echo
echo "Infrastructure started. Verify health (postgres, rabbitmq, minio should be healthy):"
echo "  docker compose --project-name $project_name --file infra/compose.yaml ps"
echo
echo "Do not rely on 'docker compose ... --wait' exiting zero: minio-bootstrap is a"
echo "one-shot container and can make --wait report failure even when core services are up."
echo
echo "Next steps:"
echo
echo "  Core API (from repository root):"
echo "    cd services/core-api"
echo "    SPRING_PROFILES_ACTIVE=local ./mvnw clean spring-boot:run"
echo
echo "  Funding/settlement demo or simulated payment behavior:"
echo "    SPRING_PROFILES_ACTIVE=local,local-sandbox ./mvnw clean spring-boot:run"
echo
echo "  Frontend:"
echo "    cp frontend/.env.example frontend/.env   # sets CORE_API_PROXY_TARGET"
echo "    cd frontend"
echo "    npm ci && npm run generate:api && npm run dev"
