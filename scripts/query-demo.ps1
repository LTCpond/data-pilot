param(
    [long]$DatasourceId = 1,
    [string]$Question = '查询最近30天销售额最高的10种商品',
    [int]$MaxRows = 100,
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'

$payload = @{
    question = $Question
    maxRows = $MaxRows
} | ConvertTo-Json

$response = Invoke-RestMethod `
    -Uri "$BaseUrl/api/datasources/$DatasourceId/queries" `
    -Method Post `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) `
    -TimeoutSec 60

if ($response.code -ne 0) {
    throw "Query failed: $($response.message)"
}

$response.data | ConvertTo-Json -Depth 10
