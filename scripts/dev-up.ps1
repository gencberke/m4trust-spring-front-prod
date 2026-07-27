[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectName = "m4trust-local"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot "infra\compose.yaml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker CLI was not found. Install and start Docker Desktop before running dev-up."
    exit 1
}

Write-Host "Starting local infrastructure (project: $projectName, profile: mock-ai)..."
& docker compose `
    --project-name $projectName `
    --file $composeFile `
    --profile mock-ai `
    up --detach --build

if ($LASTEXITCODE -ne 0) {
    Write-Error "Local infrastructure start failed (docker exit code: $LASTEXITCODE)."
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Infrastructure started. Verify health (postgres, rabbitmq, minio should be healthy):"
Write-Host "  docker compose --project-name $projectName --file infra\compose.yaml ps"
Write-Host ""
Write-Host "Do not rely on 'docker compose ... --wait' exiting zero: minio-bootstrap is a"
Write-Host "one-shot container and can make --wait report failure even when core services are up."
Write-Host ""
Write-Host "Next steps:"
Write-Host ""
Write-Host "  Core API (from repository root):"
Write-Host "    cd services\core-api"
Write-Host "    `$env:SPRING_PROFILES_ACTIVE = 'local'; .\mvnw.cmd clean spring-boot:run"
Write-Host ""
Write-Host "  Funding/settlement demo or simulated payment behavior:"
Write-Host "    `$env:SPRING_PROFILES_ACTIVE = 'local,local-sandbox'; .\mvnw.cmd clean spring-boot:run"
Write-Host ""
Write-Host "  Frontend:"
Write-Host "    Copy-Item frontend\.env.example frontend\.env   # sets CORE_API_PROXY_TARGET"
Write-Host "    cd frontend"
Write-Host "    npm ci; npm run generate:api; npm run dev"
