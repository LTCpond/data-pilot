param(
    [long]$DatasourceId = 1,
    [string]$Question = '查询订单数量',
    [int]$MaxRows = 100,
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$body = @{ question = $Question; maxRows = $MaxRows } | ConvertTo-Json
$accepted = Invoke-RestMethod -Method Post `
    -Uri "$BaseUrl/api/datasources/$DatasourceId/queries/async" `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
$queryId = $accepted.data.queryId
Write-Host "Query $queryId accepted. SSE: $BaseUrl$($accepted.data.eventsUrl)"

while ($true) {
    try {
        $response = Invoke-WebRequest -Method Get -Uri "$BaseUrl/api/queries/$queryId/result"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -notin @(202, 409)) { throw }
        $response = $_.Exception.Response
    }
    $payload = $response.Content | ConvertFrom-Json
    Write-Host "Status: $($payload.data.status)"
    if ($response.StatusCode -eq 200) {
        $payload.data.result | ConvertTo-Json -Depth 10
        break
    }
    if ($response.StatusCode -eq 409) {
        throw "Query ended with $($payload.data.status): $($payload.data.errorCode)"
    }
    Start-Sleep -Seconds 1
}
