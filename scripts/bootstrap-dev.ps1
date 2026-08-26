param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string]$MySqlContainer = 'data-pilot-mysql'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Initialize-EncryptionKey.ps1')
. (Join-Path $PSScriptRoot 'Load-Env.ps1')
Import-DotEnv -Path (Resolve-Path $EnvFile)

foreach ($name in @(
    'DATA_PILOT_MYSQL_ROOT_PASSWORD',
    'DATA_PILOT_MANAGEMENT_DB_USERNAME',
    'DATA_PILOT_MANAGEMENT_DB_PASSWORD',
    'DATA_PILOT_BUSINESS_DB_USERNAME',
    'DATA_PILOT_BUSINESS_DB_PASSWORD'
)) {
    if (-not (Get-Item "Env:$name" -ErrorAction SilentlyContinue).Value) {
        throw "Missing required variable: $name"
    }
}

$managementUser = $env:DATA_PILOT_MANAGEMENT_DB_USERNAME
$businessUser = $env:DATA_PILOT_BUSINESS_DB_USERNAME
if ($managementUser -notmatch '^[A-Za-z0-9_]+$' -or $businessUser -notmatch '^[A-Za-z0-9_]+$') {
    throw 'Database usernames may contain only letters, digits, and underscores.'
}

function Escape-MySqlLiteral([string]$value) {
    return $value.Replace("'", "''")
}

$managementPassword = Escape-MySqlLiteral $env:DATA_PILOT_MANAGEMENT_DB_PASSWORD
$businessPassword = Escape-MySqlLiteral $env:DATA_PILOT_BUSINESS_DB_PASSWORD
$bootstrapSql = @"
CREATE DATABASE IF NOT EXISTS ``data_pilot`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS ``ecommerce_demo`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$managementUser'@'%' IDENTIFIED BY '$managementPassword';
ALTER USER '$managementUser'@'%' IDENTIFIED BY '$managementPassword';
GRANT ALL PRIVILEGES ON ``data_pilot``.* TO '$managementUser'@'%';
CREATE USER IF NOT EXISTS '$businessUser'@'%' IDENTIFIED BY '$businessPassword';
ALTER USER '$businessUser'@'%' IDENTIFIED BY '$businessPassword';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$businessUser'@'%';
GRANT SELECT ON ``ecommerce_demo``.* TO '$businessUser'@'%';
FLUSH PRIVILEGES;
"@

Write-Host 'Creating databases and least-privilege users...'
& docker exec -e "MYSQL_PWD=$($env:DATA_PILOT_MYSQL_ROOT_PASSWORD)" $MySqlContainer `
    mysql -uroot --default-character-set=utf8mb4 -e $bootstrapSql
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to create databases or users.'
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$schemaFile = Join-Path $repoRoot 'database\mysql\ecommerce_schema.sql'
$seedFile = Join-Path $repoRoot 'database\mysql\ecommerce_seed.sql'

Write-Host 'Applying ecommerce schema...'
& docker cp $schemaFile "${MySqlContainer}:/tmp/data-pilot-ecommerce-schema.sql"
if ($LASTEXITCODE -ne 0) { throw 'Failed to copy ecommerce schema.' }
& docker exec -e "MYSQL_PWD=$($env:DATA_PILOT_MYSQL_ROOT_PASSWORD)" $MySqlContainer `
    mysql -uroot --default-character-set=utf8mb4 ecommerce_demo -e 'source /tmp/data-pilot-ecommerce-schema.sql'
if ($LASTEXITCODE -ne 0) { throw 'Failed to apply ecommerce schema.' }

Write-Host 'Loading deterministic demo data...'
& docker cp $seedFile "${MySqlContainer}:/tmp/data-pilot-ecommerce-seed.sql"
if ($LASTEXITCODE -ne 0) { throw 'Failed to copy ecommerce seed data.' }
& docker exec -e "MYSQL_PWD=$($env:DATA_PILOT_MYSQL_ROOT_PASSWORD)" $MySqlContainer `
    mysql -uroot --default-character-set=utf8mb4 ecommerce_demo -e 'source /tmp/data-pilot-ecommerce-seed.sql'
if ($LASTEXITCODE -ne 0) { throw 'Failed to load ecommerce seed data.' }

Write-Host 'Development databases initialized successfully.' -ForegroundColor Green
