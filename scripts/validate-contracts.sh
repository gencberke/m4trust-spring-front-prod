#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
venv="$root/contracts/.venv"
if [[ ! -d "$venv" ]]; then
  python3 -m venv "$venv"
fi
# shellcheck disable=SC1091
source "$venv/bin/activate"
pip install -q -r "$root/contracts/requirements.txt"
python "$root/contracts/scripts/validate_contracts.py"
