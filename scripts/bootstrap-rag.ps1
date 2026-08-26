param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string]$PostgresContainer = 'data-pilot-postgres',
    [string]$MySqlContainer = 'data-pilot-mysql',
    [switch]$SkipOllama
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Load-Env.ps1')
Import-DotEnv -Path (Resolve-Path $EnvFile)

$required = @(
    'DATA_PILOT_MYSQL_ROOT_PASSWORD',
    'DATA_PILOT_BUSINESS_DB_USERNAME',
    'DATA_PILOT_BUSINESS_DB_PASSWORD',
    'DATA_PILOT_POSTGRES_ADMIN_PASSWORD',
    'DATA_PILOT_VECTOR_DB_USERNAME',
    'DATA_PILOT_VECTOR_DB_PASSWORD'
)
foreach ($name in $required) {
    if (-not (Get-Item "Env:$name" -ErrorAction SilentlyContinue).Value) {
        throw "Missing required variable: $name"
    }
}

$vectorDatabase = if ($env:DATA_PILOT_VECTOR_DB_NAME) { $env:DATA_PILOT_VECTOR_DB_NAME } else { 'data_pilot_vector' }
$vectorUser = $env:DATA_PILOT_VECTOR_DB_USERNAME
$postgresAdmin = if ($env:DATA_PILOT_POSTGRES_ADMIN_USERNAME) { $env:DATA_PILOT_POSTGRES_ADMIN_USERNAME } else { 'postgres' }
$postgresPassword = $env:DATA_PILOT_POSTGRES_ADMIN_PASSWORD
foreach ($identifier in @($vectorDatabase, $vectorUser, $postgresAdmin)) {
    if ($identifier -notmatch '^[A-Za-z0-9_]+$') {
        throw 'PostgreSQL database and user names may contain only letters, digits, and underscores.'
    }
}
$escapedVectorPassword = $env:DATA_PILOT_VECTOR_DB_PASSWORD.Replace("'", "''")

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$composeFile = Join-Path $repoRoot 'docker-compose.middleware.yml'
$composeServices = @('postgres', 'mysql')
if (-not $SkipOllama) {
    $composeServices += 'ollama'
}

Write-Host "Starting Data Pilot RAG middleware: $($composeServices -join ', ')..."
$composeArguments = @(
    'compose',
    '--env-file', (Resolve-Path $EnvFile),
    '-f', $composeFile,
    'up', '-d', '--wait'
) + $composeServices
& docker @composeArguments
if ($LASTEXITCODE -ne 0) { throw 'Failed to start Data Pilot RAG middleware.' }

if (-not $SkipOllama) {
    Write-Host 'Pulling bge-m3 explicitly (the application never downloads models)...'
    & docker exec data-pilot-ollama ollama pull bge-m3
    if ($LASTEXITCODE -ne 0) { throw 'Failed to pull bge-m3.' }
}

Write-Host 'Creating the least-privilege pgvector database...'
$roleSql = "DO `$`$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$vectorUser') THEN CREATE ROLE $vectorUser LOGIN PASSWORD '$escapedVectorPassword'; ELSE ALTER ROLE $vectorUser PASSWORD '$escapedVectorPassword'; END IF; END `$`$;"
& docker exec -e "PGPASSWORD=$postgresPassword" $PostgresContainer psql -U $postgresAdmin -d $postgresAdmin -v ON_ERROR_STOP=1 -c $roleSql
if ($LASTEXITCODE -ne 0) { throw 'Failed to create the pgvector role.' }

$databaseExists = & docker exec -e "PGPASSWORD=$postgresPassword" $PostgresContainer psql -U $postgresAdmin -d $postgresAdmin -tAc "SELECT 1 FROM pg_database WHERE datname = '$vectorDatabase'"
if (($databaseExists | Out-String).Trim() -ne '1') {
    & docker exec -e "PGPASSWORD=$postgresPassword" $PostgresContainer createdb -U $postgresAdmin -O $vectorUser $vectorDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create the pgvector database.' }
}
$extensionSql = 'CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS hstore; CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";'
& docker exec -e "PGPASSWORD=$postgresPassword" $PostgresContainer psql -U $postgresAdmin -d $vectorDatabase -v ON_ERROR_STOP=1 -c $extensionSql
if ($LASTEXITCODE -ne 0) { throw 'Failed to create PostgreSQL extensions.' }

Write-Host 'Creating the 50-table RAG evaluation database...'
$businessUser = $env:DATA_PILOT_BUSINESS_DB_USERNAME
$businessPassword = $env:DATA_PILOT_BUSINESS_DB_PASSWORD.Replace("'", "''")
$mysqlSql = @"
CREATE DATABASE IF NOT EXISTS ``ecommerce_rag_demo`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$businessUser'@'%' IDENTIFIED BY '$businessPassword';
GRANT SELECT ON ``ecommerce_rag_demo``.* TO '$businessUser'@'%';
FLUSH PRIVILEGES;
"@
& docker exec -e "MYSQL_PWD=$($env:DATA_PILOT_MYSQL_ROOT_PASSWORD)" $MySqlContainer mysql -uroot --default-character-set=utf8mb4 -e $mysqlSql
if ($LASTEXITCODE -ne 0) { throw 'Failed to create ecommerce_rag_demo.' }

foreach ($item in @(
    @{ Source = 'database\mysql\ecommerce_schema.sql'; Target = '/tmp/data-pilot-rag-schema.sql' },
    @{ Source = 'database\mysql\ecommerce_seed.sql'; Target = '/tmp/data-pilot-rag-seed.sql' },
    @{ Source = 'database\mysql\ecommerce_rag_distractors.sql'; Target = '/tmp/data-pilot-rag-distractors.sql' }
)) {
    & docker cp (Join-Path $repoRoot $item.Source) "${MySqlContainer}:$($item.Target)"
    if ($LASTEXITCODE -ne 0) { throw "Failed to copy $($item.Source)." }
    & docker exec -e "MYSQL_PWD=$($env:DATA_PILOT_MYSQL_ROOT_PASSWORD)" $MySqlContainer mysql -uroot --default-character-set=utf8mb4 ecommerce_rag_demo -e "source $($item.Target)"
    if ($LASTEXITCODE -ne 0) { throw "Failed to apply $($item.Source)." }
}

if ($SkipOllama) {
    Write-Host 'pgvector and ecommerce_rag_demo are ready; Ollama was intentionally skipped.' -ForegroundColor Green
} else {
    Write-Host 'Ollama, pgvector, and ecommerce_rag_demo are ready.' -ForegroundColor Green
}
