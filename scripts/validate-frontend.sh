#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../frontend"
if [[ "${1:-}" == "--full" ]]; then
  npm run typecheck
else
  npm run typecheck:fast
fi
