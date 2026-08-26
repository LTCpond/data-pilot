param(
    [ValidateSet('baseline', 'final')]
    [string]$Round = 'baseline',
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Load-Env.ps1')
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Import-DotEnv -Path (Resolve-Path $EnvFile)

foreach ($name in @(
    'DATA_PILOT_AI_API_KEY',
    'DATA_PILOT_AI_MODEL',
    'DATA_PILOT_AI_BASE_URL',
    'DATA_PILOT_ENCRYPTION_KEY',
    'DATA_PILOT_MANAGEMENT_DB_USERNAME',
    'DATA_PILOT_MANAGEMENT_DB_PASSWORD'
)) {
    if (-not (Get-Item "Env:$name" -ErrorAction SilentlyContinue).Value) {
        throw "Missing required variable: $name"
    }
}

if ($env:DATA_PILOT_AI_MODEL -ne 'deepseek-v4-pro') {
    throw 'The evaluation is fixed to deepseek-v4-pro.'
}
if ($env:DATA_PILOT_AI_BASE_URL.TrimEnd('/') -ne 'https://api.deepseek.com') {
    throw 'The evaluation requires the official DeepSeek base URL.'
}

$reportDirectory = Join-Path $repoRoot 'data-pilot-api\target\text-to-sql-evaluation'
$baselineReport = Join-Path $reportDirectory 'round-baseline.json'
$finalReport = Join-Path $reportDirectory 'round-final.json'
if ($Round -eq 'baseline' -and (Test-Path -LiteralPath $baselineReport)) {
    throw 'The baseline round has already run. Delete target reports manually only when starting a new evaluation cycle.'
}
if ($Round -eq 'final') {
    if (-not (Test-Path -LiteralPath $baselineReport)) {
        throw 'Run the baseline round before the final round.'
    }
    if (Test-Path -LiteralPath $finalReport) {
        throw 'The final round has already run; a third round is not allowed.'
    }
    $baseline = Get-Content -LiteralPath $baselineReport -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([double]$baseline.passRate -ge 0.90 `
            -and [int]$baseline.answerablePassed -ge 15 `
            -and [int]$baseline.rejectedPassed -eq 3) {
        Write-Host 'Baseline already meets the threshold; the final round is intentionally skipped.' -ForegroundColor Green
        exit 0
    }
}

$javaHome = if ($env:DATA_PILOT_JAVA_HOME) { $env:DATA_PILOT_JAVA_HOME } else { 'D:\mc\zulu' }
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
    throw "Java 21 was not found at $javaHome."
}

$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome 'bin') + ';' + $env:PATH
$env:DATA_PILOT_RUN_AI_EVALUATION = 'true'
$env:DATA_PILOT_AI_ENABLED = 'true'

Write-Host "Running the $Round DeepSeek evaluation sequentially (20 fixed questions)..."
Push-Location $repoRoot
try {
    & mvn -pl data-pilot-api -am `
        '-Dtest=DeepSeekTextToSqlEvaluationTest' `
        '-Dsurefire.failIfNoSpecifiedTests=false' `
        "-Ddata-pilot.evaluation.round=$Round" `
        '-DforkCount=1' `
        test
    $mavenExitCode = $LASTEXITCODE
} finally {
    Pop-Location
    Remove-Item Env:DATA_PILOT_RUN_AI_EVALUATION -ErrorAction SilentlyContinue
}

$reportPath = if ($Round -eq 'baseline') { $baselineReport } else { $finalReport }
if (Test-Path -LiteralPath $reportPath) {
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host ("Result: {0}/20, pass rate {1:P1}, answerable {2}/17, rejected {3}/3." -f `
        $report.passed, [double]$report.passRate, $report.answerablePassed, $report.rejectedPassed)
    Write-Host "Report: $reportPath"
}
if ($mavenExitCode -ne 0) {
    throw "The $Round evaluation did not meet the acceptance threshold."
}

Write-Host 'DeepSeek evaluation passed.' -ForegroundColor Green
