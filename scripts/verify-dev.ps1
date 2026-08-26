param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string]$MySqlContainer = 'data-pilot-mysql',
    [string]$RedisContainer = 'data-pilot-redis',
    [switch]$CheckHttp
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Load-Env.ps1')
Import-DotEnv -Path (Resolve-Path $EnvFile)

function Invoke-MySql {
    param(
        [string]$User,
        [string]$Password,
        [string]$Database,
        [string]$Sql,
        [switch]$ExpectFailure
    )

    $arguments = @('exec', '-e', "MYSQL_PWD=$Password", $MySqlContainer, 'mysql', "-u$User", '-Nse', $Sql)
    if ($Database) {
        $arguments = @('exec', '-e', "MYSQL_PWD=$Password", $MySqlContainer, 'mysql', "-u$User", $Database, '-Nse', $Sql)
    }
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & docker @arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($ExpectFailure) {
        if ($exitCode -eq 0) { throw "Expected MySQL statement to fail: $Sql" }
        return
    }
    if ($exitCode -ne 0) { throw "MySQL check failed: $output" }
    return $output
}

Write-Host 'Checking management database read/write access...'
Invoke-MySql -User $env:DATA_PILOT_MANAGEMENT_DB_USERNAME `
    -Password $env:DATA_PILOT_MANAGEMENT_DB_PASSWORD -Database 'data_pilot' `
    -Sql 'CREATE TEMPORARY TABLE dp_smoke_test(id INT); INSERT INTO dp_smoke_test VALUES (1); SELECT COUNT(*) FROM dp_smoke_test;'

Write-Host 'Checking management user isolation...'
Invoke-MySql -User $env:DATA_PILOT_MANAGEMENT_DB_USERNAME `
    -Password $env:DATA_PILOT_MANAGEMENT_DB_PASSWORD -Database 'data_pilot' `
    -Sql 'SELECT COUNT(*) FROM ecommerce_demo.users;' -ExpectFailure

Write-Host 'Checking business database data and read-only permissions...'
$counts = Invoke-MySql -User $env:DATA_PILOT_BUSINESS_DB_USERNAME `
    -Password $env:DATA_PILOT_BUSINESS_DB_PASSWORD -Database 'ecommerce_demo' `
    -Sql "SELECT CONCAT((SELECT COUNT(*) FROM users), ',', (SELECT COUNT(*) FROM shops), ',', (SELECT COUNT(*) FROM products), ',', (SELECT COUNT(*) FROM orders), ',', (SELECT COUNT(*) FROM order_items));"
if (($counts | Select-Object -Last 1).Trim() -ne '20,5,20,60,120') {
    throw "Unexpected ecommerce row counts: $counts"
}

foreach ($sql in @(
    'UPDATE products SET name = name WHERE id = 1;',
    'DELETE FROM products WHERE id = 999999;',
    'CREATE TABLE forbidden_write(id INT);'
)) {
    Invoke-MySql -User $env:DATA_PILOT_BUSINESS_DB_USERNAME `
        -Password $env:DATA_PILOT_BUSINESS_DB_PASSWORD -Database 'ecommerce_demo' `
        -Sql $sql -ExpectFailure
}

Write-Host 'Checking Redis DB 1...'
$redisPing = & docker exec $RedisContainer redis-cli -a $env:DATA_PILOT_REDIS_PASSWORD --no-auth-warning -n 1 ping
if ($LASTEXITCODE -ne 0 -or $redisPing.Trim() -ne 'PONG') {
    throw "Redis check failed: $redisPing"
}

if ($CheckHttp) {
    Write-Host 'Checking application health endpoint...'
    $response = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -Method Get -TimeoutSec 5
    if ($response.code -ne 0 -or $response.data.status -ne 'UP') {
        throw 'Application health endpoint is not UP.'
    }
}

Write-Host 'All development environment checks passed.' -ForegroundColor Green
