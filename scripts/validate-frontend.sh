#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../frontend"
if [[ "${1:-}" == "--full" ]]; then
  npm run generate:api:check
  npm run typecheck:fast
else
  npm run typecheck:fast
fi
