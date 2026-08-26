param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Load-Env.ps1')
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Import-DotEnv -Path (Resolve-Path $EnvFile)

$javaHome = if ($env:DATA_PILOT_JAVA_HOME) { $env:DATA_PILOT_JAVA_HOME } else { 'D:\mc\zulu' }
if (-not (Test-Path (Join-Path $javaHome 'bin\java.exe'))) {
    throw "Java 21 was not found at $javaHome. Set DATA_PILOT_JAVA_HOME in .env."
}

$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome 'bin') + ';' + $env:PATH
Push-Location $repoRoot
try {
    & mvn clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    & java -jar 'data-pilot-api\target\data-pilot-api-1.0-SNAPSHOT.jar'
    if ($LASTEXITCODE -ne 0) { throw 'Data Pilot application exited with an error.' }
} finally {
    Pop-Location
}
