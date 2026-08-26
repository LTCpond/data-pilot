param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [switch]$IncludeOllama
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$resolvedEnvFile = Resolve-Path $EnvFile
$composeFile = Join-Path $repoRoot 'docker-compose.middleware.yml'
$services = @('mysql', 'redis', 'postgres')
if ($IncludeOllama) {
    $services += 'ollama'
}

Write-Host "Starting Data Pilot middleware: $($services -join ', ')..."
$composeArguments = @(
    'compose',
    '--env-file', $resolvedEnvFile,
    '-f', $composeFile,
    'up', '-d', '--wait'
) + $services
& docker @composeArguments
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to start Data Pilot middleware.'
}

Write-Host 'Data Pilot middleware is ready.' -ForegroundColor Green
