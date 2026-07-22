#!/usr/bin/env bash
# Smoke-check a built Core API image for non-root runtime and packaged schemas.
# Usage: ./services/core-api/docker/smoke-image.sh m4trust-core-api:<tag>
# Requires host tools: docker, jar (JDK), python3.
set -euo pipefail

IMAGE="${1:?image tag required}"
WORKDIR="$(mktemp -d)"
cleanup() { rm -rf "${WORKDIR}"; }
trap cleanup EXIT

echo "Checking non-root user..."
user="$(docker image inspect "${IMAGE}" --format '{{ .Config.User }}')"
test "${user}" = "m4trust"

echo "Extracting application JAR from image..."
cid="$(docker create "${IMAGE}")"
docker cp "${cid}:/app/app.jar" "${WORKDIR}/app.jar"
docker rm "${cid}" >/dev/null

echo "Parsing every packaged runtime schema from the image classpath..."
mkdir -p "${WORKDIR}/extract"
(
  cd "${WORKDIR}/extract"
  jar tf "${WORKDIR}/app.jar" | grep -E '^BOOT-INF/classes/contracts/schemas/.*\.json$' > schemas.txt
  test -s schemas.txt
  while IFS= read -r entry; do
    jar xf "${WORKDIR}/app.jar" "${entry}"
  done < schemas.txt
)
PYTHON_BIN="$(command -v python3 || command -v python || true)"
if [ -z "${PYTHON_BIN}" ]; then
  echo "python3/python required for schema parse smoke" >&2
  exit 1
fi
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

echo "Smoke OK for ${IMAGE}"
