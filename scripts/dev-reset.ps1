[CmdletBinding(SupportsShouldProcess)]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectName = "m4trust-local"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot "infra\compose.yaml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker CLI was not found. Install and start Docker Desktop before resetting local infrastructure."
    exit 1
}

$target = "local Docker Compose project '$projectName' (containers, network, and named volumes)"
$operation = "docker compose --project-name $projectName --file $composeFile down --volumes --remove-orphans"

if (-not $PSCmdlet.ShouldProcess($target, $operation)) {
    exit 0
}

Write-Host "Removing only $target..."
& docker compose --project-name $projectName --file $composeFile down --volumes --remove-orphans

if ($LASTEXITCODE -ne 0) {
    Write-Error "Local Compose project reset failed (docker exit code: $LASTEXITCODE)."
    exit $LASTEXITCODE
}

Write-Host "Local infrastructure project '$projectName' was reset."
