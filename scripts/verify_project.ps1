$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPom = Join-Path $projectRoot "backend\pom.xml"
$frontendConfig = Join-Path $projectRoot "tsconfig.json"
$typeScriptCommand = Join-Path $projectRoot "node_modules\.bin\tsc.cmd"

function Invoke-Step {
    param(
        [string]$Name,
        [string]$CommandPath,
        [string[]]$Arguments
    )

    Write-Host "Running $Name..."
    & $CommandPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        [Console]::Error.WriteLine("$Name failed (exit code $LASTEXITCODE).")
        exit 1
    }
    Write-Host "$Name passed."
}

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($null -eq $maven) {
    [Console]::Error.WriteLine("Backend tests unavailable: mvn.cmd was not found on PATH.")
    exit 1
}
Invoke-Step "backend tests" $maven.Source @("-q", "-f", $backendPom, "test")

if (-not (Test-Path -LiteralPath $typeScriptCommand)) {
    [Console]::Error.WriteLine("Frontend type check unavailable: run npm ci in the project root first.")
    exit 1
}
Invoke-Step "frontend type check" $typeScriptCommand @("--project", $frontendConfig, "--noEmit")

$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if ($null -eq $npm) {
    [Console]::Error.WriteLine("Frontend build unavailable: npm.cmd was not found on PATH.")
    exit 1
}
Invoke-Step "frontend build" $npm.Source @("--prefix", $projectRoot, "run", "build")

Write-Host "Project verification passed."
