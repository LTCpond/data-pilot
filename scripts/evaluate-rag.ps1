param([string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'))

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Load-Env.ps1')
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Import-DotEnv -Path (Resolve-Path $EnvFile)

foreach ($name in @(
    'DATA_PILOT_AI_API_KEY','DATA_PILOT_AI_MODEL','DATA_PILOT_AI_BASE_URL',
    'DATA_PILOT_ENCRYPTION_KEY','DATA_PILOT_MANAGEMENT_DB_USERNAME',
    'DATA_PILOT_MANAGEMENT_DB_PASSWORD','DATA_PILOT_VECTOR_DB_USERNAME',
    'DATA_PILOT_VECTOR_DB_PASSWORD'
)) {
    if (-not (Get-Item "Env:$name" -ErrorAction SilentlyContinue).Value) {
        throw "Missing required variable: $name"
    }
}
if ($env:DATA_PILOT_AI_MODEL -ne 'deepseek-v4-pro' -or $env:DATA_PILOT_AI_BASE_URL.TrimEnd('/') -ne 'https://api.deepseek.com') {
    throw 'The paired evaluation requires deepseek-v4-pro through the official DeepSeek endpoint.'
}

$reportDirectory = Join-Path $repoRoot 'data-pilot-api\target\text-to-sql-evaluation'
$fullReport = Join-Path $reportDirectory 'round-full-schema-50.json'
$ragReport = Join-Path $reportDirectory 'round-rag-50.json'
$runMarker = Join-Path $reportDirectory 'rag-comparison.started'
if ((Test-Path -LiteralPath $runMarker) `
        -or (Test-Path -LiteralPath $fullReport) `
        -or (Test-Path -LiteralPath $ragReport)) {
    throw 'The one allowed 40-task comparison already started. Reports must not be overwritten automatically.'
}

$javaHome = if ($env:DATA_PILOT_JAVA_HOME) { $env:DATA_PILOT_JAVA_HOME } else { 'D:\mc\zulu' }
$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome 'bin') + ';' + $env:PATH
$env:DATA_PILOT_RAG_ENABLED = 'true'
$env:DATA_PILOT_EMBEDDING_PROVIDER = 'ollama'
$env:DATA_PILOT_RUN_RAG_RETRIEVAL_EVALUATION = 'true'

Push-Location $repoRoot
try {
    Write-Host 'Running retrieval-only calibration (no DeepSeek calls)...'
    & mvn -pl data-pilot-api -am '-Dtest=SchemaRetrievalEvaluationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
    if ($LASTEXITCODE -ne 0) { throw 'Required-table retrieval did not meet the threshold.' }
    Remove-Item Env:DATA_PILOT_RUN_RAG_RETRIEVAL_EVALUATION -ErrorAction SilentlyContinue

    New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    Set-Content -LiteralPath $runMarker -Value (Get-Date -Format o) -Encoding ascii
    $env:DATA_PILOT_RUN_AI_EVALUATION = 'true'
    foreach ($run in @(
        @{ Mode = 'FULL_SCHEMA'; Round = 'full-schema-50' },
        @{ Mode = 'RAG'; Round = 'rag-50' }
    )) {
        $env:DATA_PILOT_RAG_MODE = $run.Mode
        Write-Host "Running 20 DeepSeek tasks in $($run.Mode) mode..."
        & mvn -pl data-pilot-api -am '-Dtest=DeepSeekTextToSqlEvaluationTest' `
            '-Dsurefire.failIfNoSpecifiedTests=false' `
            '-Ddata-pilot.evaluation.datasource-name=电商RAG演示库' `
            "-Ddata-pilot.evaluation.round=$($run.Round)" '-DforkCount=1' test
        if ($LASTEXITCODE -ne 0) { throw "$($run.Mode) evaluation failed." }
    }
} finally {
    Pop-Location
    Remove-Item Env:DATA_PILOT_RUN_AI_EVALUATION -ErrorAction SilentlyContinue
    Remove-Item Env:DATA_PILOT_RUN_RAG_RETRIEVAL_EVALUATION -ErrorAction SilentlyContinue
}

$full = Get-Content -LiteralPath $fullReport -Raw -Encoding UTF8 | ConvertFrom-Json
$rag = Get-Content -LiteralPath $ragReport -Raw -Encoding UTF8 | ConvertFrom-Json
$tokenReduction = if ([double]$full.promptTokens -eq 0) { 0 } else { 1 - ([double]$rag.promptTokens / [double]$full.promptTokens) }
$accuracyDrop = [int]$full.passed - [int]$rag.passed
$comparison = @"
# Data Pilot Schema RAG 对照评测

- 全量 Schema：$($full.passed)/20，Prompt Token $($full.promptTokens)
- RAG：$($rag.passed)/20，Prompt Token $($rag.promptTokens)
- Prompt Token 降幅：$([Math]::Round($tokenReduction * 100, 2))%
- RAG 平均 Prompt 表数：$($rag.averagePromptTableCount)/50
- RAG 平均检索延迟：$($rag.averageRetrievalDurationMs) ms
- RAG 回退次数：$($rag.fallbackCount)
- 准确题数下降：$accuracyDrop
"@
$comparisonPath = Join-Path $reportDirectory 'comparison.md'
Set-Content -LiteralPath $comparisonPath -Value $comparison -Encoding UTF8
if ($tokenReduction -lt 0.30 -or [double]$rag.averagePromptTableCount -gt 12 -or $accuracyDrop -gt 1) {
    throw 'The paired evaluation did not meet the RAG comparison thresholds.'
}
Write-Host "RAG comparison passed. Report: $comparisonPath" -ForegroundColor Green
