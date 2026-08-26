param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Load-Env.ps1')
Import-DotEnv -Path (Resolve-Path $EnvFile)

$name = '电商RAG演示库'
$list = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/datasources"
$existing = @($list.data) | Where-Object { $_.name -eq $name } | Select-Object -First 1
if ($null -eq $existing) {
    $body = @{
        name = $name
        description = '包含5张业务表和45张干扰表的Schema RAG评测库'
        jdbcUrl = "jdbc:mysql://$($env:DATA_PILOT_MYSQL_HOST):$($env:DATA_PILOT_MYSQL_PORT)/ecommerce_rag_demo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
        username = $env:DATA_PILOT_BUSINESS_DB_USERNAME
        password = $env:DATA_PILOT_BUSINESS_DB_PASSWORD
    } | ConvertTo-Json
    $created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/datasources" -ContentType 'application/json; charset=utf-8' -Body $body
    $datasourceId = $created.data.id
} else {
    $datasourceId = $existing.id
}

$sync = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/datasources/$datasourceId/sync"
if ($sync.data.tableCount -ne 50) {
    throw "Expected 50 tables, got $($sync.data.tableCount)."
}
if ($sync.data.ragStatus -ne 'READY') {
    throw 'Schema metadata synchronized, but the RAG index is not READY.'
}
$index = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/datasources/$datasourceId/rag-index"
if ($index.data.documentCount -ne 50) {
    throw "Expected 50 active vector documents, got $($index.data.documentCount)."
}
Write-Host "RAG demo datasource $datasourceId is READY with 50 documents." -ForegroundColor Green
