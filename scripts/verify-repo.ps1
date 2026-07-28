$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$InitialStatus = (git -C $RepoRoot status --porcelain=v1) -join "`n"
$Total = [System.Diagnostics.Stopwatch]::StartNew()
$BaseSha = $env:M4TRUST_BASE_SHA
if (-not $BaseSha) {
    $BaseSha = (git -C $RepoRoot merge-base HEAD origin/main 2>$null)
}
if (-not $BaseSha) {
    $BaseSha = (git -C $RepoRoot rev-parse HEAD)
}
$BaseOpenApi = [System.IO.Path]::GetTempFileName()
$BaseOpenApiText = git -C $RepoRoot show "${BaseSha}:contracts/openapi/core-api-v1.yaml"
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText(
    $BaseOpenApi,
    (($BaseOpenApiText -join [Environment]::NewLine) + [Environment]::NewLine),
    $Utf8NoBom
)

if ($env:M4TRUST_PYTHON) {
    $ContractPython = $env:M4TRUST_PYTHON
    if (-not [System.IO.Path]::IsPathRooted($ContractPython) -and
        ($ContractPython.Contains("/") -or $ContractPython.Contains("\"))) {
        $ContractPython = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $ContractPython))
    }
    $env:M4TRUST_PYTHON = $ContractPython
} elseif (Test-Path (Join-Path $RepoRoot "contracts/.venv/Scripts/python.exe")) {
    $ContractPython = Join-Path $RepoRoot "contracts/.venv/Scripts/python.exe"
} else {
    $ContractPython = "python"
}

if ($env:M4TRUST_TOOLS_PYTHON) {
    $ToolsPython = $env:M4TRUST_TOOLS_PYTHON
    if (-not [System.IO.Path]::IsPathRooted($ToolsPython) -and
        ($ToolsPython.Contains("/") -or $ToolsPython.Contains("\"))) {
        $ToolsPython = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $ToolsPython))
    }
} elseif ($env:M4TRUST_PYTHON) {
    $ToolsPython = $env:M4TRUST_PYTHON
} elseif (Test-Path (Join-Path $RepoRoot "tools/mock-ai-worker/.venv/Scripts/python.exe")) {
    $ToolsPython = Join-Path $RepoRoot "tools/mock-ai-worker/.venv/Scripts/python.exe"
} else {
    $ToolsPython = "python"
}

function Invoke-Stage {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )
    Write-Host "=== $Name ==="
    $Timer = [System.Diagnostics.Stopwatch]::StartNew()
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE"
    }
    $Timer.Stop()
    Write-Host ("PASS {0} ({1:n1}s)" -f $Name, $Timer.Elapsed.TotalSeconds)
}

Invoke-Stage "contracts" {
    & $ContractPython (Join-Path $RepoRoot "contracts/scripts/validate_contracts.py")
}
Invoke-Stage "contract negative fixture" {
    & $ContractPython (Join-Path $RepoRoot "contracts/scripts/compare_openapi_structure.py") `
        --expected (Join-Path $RepoRoot "contracts/openapi/core-api-v1.yaml") `
        --actual (Join-Path $RepoRoot "contracts/openapi/core-api-v1.yaml") `
        --negative-fixture
}
Invoke-Stage "OpenAPI compatibility against $BaseSha" {
    & $ContractPython (Join-Path $RepoRoot "contracts/scripts/compare_openapi_structure.py") `
        --expected $BaseOpenApi `
        --actual (Join-Path $RepoRoot "contracts/openapi/core-api-v1.yaml")
}
Invoke-Stage "core API" {
    & (Join-Path $RepoRoot "services/core-api/mvnw.cmd") `
        --batch-mode --no-transfer-progress `
        --file (Join-Path $RepoRoot "services/core-api/pom.xml") verify
}
Invoke-Stage "frontend generated contract" {
    npm --prefix (Join-Path $RepoRoot "frontend") run generate:api:check
}
Invoke-Stage "frontend lint" {
    npm --prefix (Join-Path $RepoRoot "frontend") run lint
}
Invoke-Stage "frontend format" {
    npm --prefix (Join-Path $RepoRoot "frontend") run format:check
}
Invoke-Stage "frontend tests" {
    npm --prefix (Join-Path $RepoRoot "frontend") run test
}
Invoke-Stage "frontend build" {
    npm --prefix (Join-Path $RepoRoot "frontend") run build:check
}
Invoke-Stage "mock AI worker tests" {
    $env:PYTHONPATH = Join-Path $RepoRoot "tools/mock-ai-worker/src"
    & $ToolsPython -m pytest (Join-Path $RepoRoot "tools/mock-ai-worker/tests")
}
Invoke-Stage "Moka emulator tests" {
    $env:PYTHONPATH = Join-Path $RepoRoot "tools/moka-emulator/src"
    & $ToolsPython -m unittest discover `
        -s (Join-Path $RepoRoot "tools/moka-emulator/tests") -p "test_*.py" -v
}
Invoke-Stage "Markdown references" {
    & $ContractPython (Join-Path $RepoRoot "scripts/check-markdown-links.py")
}
Invoke-Stage "architecture references" {
    & $ContractPython (Join-Path $RepoRoot "scripts/check-architecture-references.py")
}
Invoke-Stage "Flyway history" {
    & $ContractPython (Join-Path $RepoRoot "scripts/check-flyway-history.py") `
        --base $BaseSha
}
Invoke-Stage "repository map" {
    & $ContractPython (Join-Path $RepoRoot "scripts/generate-repo-map.py") --check
}
Invoke-Stage "whitespace" {
    git -C $RepoRoot diff --check
}

$FinalStatus = (git -C $RepoRoot status --porcelain=v1) -join "`n"
if ($FinalStatus -ne $InitialStatus) {
    throw "Validation changed the working tree."
}

$Total.Stop()
Remove-Item -Force $BaseOpenApi
Write-Host ("PASS repository validation ({0:n1}s total)" -f $Total.Elapsed.TotalSeconds)
