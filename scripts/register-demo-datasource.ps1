param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [switch]$VerifyIdempotency
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Load-Env.ps1')
Import-DotEnv -Path (Resolve-Path $EnvFile)

$name = '电商演示库'
$listResponse = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/datasources"
$existing = @($listResponse.data) | Where-Object { $_.name -eq $name } | Select-Object -First 1

if ($null -eq $existing) {
    $request = @{
        name = $name
        description = '本地 Text-to-SQL 演示数据'
        jdbcUrl = "jdbc:mysql://$($env:DATA_PILOT_MYSQL_HOST):$($env:DATA_PILOT_MYSQL_PORT)/ecommerce_demo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
        username = $env:DATA_PILOT_BUSINESS_DB_USERNAME
        password = $env:DATA_PILOT_BUSINESS_DB_PASSWORD
    } | ConvertTo-Json
    $created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/datasources" `
        -ContentType 'application/json; charset=utf-8' -Body $request
    $datasourceId = $created.data.id
    Write-Host "Created demo datasource with ID $datasourceId."
}
else {
    $datasourceId = $existing.id
    Write-Host "Demo datasource already exists with ID $datasourceId."
}

$first = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/datasources/$datasourceId/sync"
Write-Host "Synchronized: $($first.data.tableCount) tables, $($first.data.columnCount) columns, $($first.data.foreignKeyCount) foreign keys."

if ($VerifyIdempotency) {
    $second = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/datasources/$datasourceId/sync"
    if ($first.data.tableCount -ne $second.data.tableCount `
            -or $first.data.columnCount -ne $second.data.columnCount `
            -or $first.data.foreignKeyCount -ne $second.data.foreignKeyCount) {
        throw 'Metadata counts changed during the second synchronization.'
    }
    Write-Host 'Second synchronization returned identical counts.' -ForegroundColor Green
}

$schema = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/datasources/$datasourceId/schema"
if (@($schema.data.tables).Count -ne 5) {
    throw "Expected 5 ecommerce tables, got $(@($schema.data.tables).Count)."
}
Write-Host 'Demo datasource registration and schema verification passed.' -ForegroundColor Green
