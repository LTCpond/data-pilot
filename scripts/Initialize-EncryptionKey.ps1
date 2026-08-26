param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$resolvedPath = (Resolve-Path $EnvFile).Path
$content = Get-Content -LiteralPath $resolvedPath -Raw
$match = [regex]::Match($content, '(?m)^DATA_PILOT_ENCRYPTION_KEY=(.*)$')

if ($match.Success) {
    try {
        $existingKey = [Convert]::FromBase64String($match.Groups[1].Value.Trim())
        if ($existingKey.Length -eq 32) {
            return
        }
    }
    catch {
        # Replace missing or invalid example values below.
    }
}

$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$generatedKey = [Convert]::ToBase64String($bytes)

if ($match.Success) {
    $content = [regex]::Replace(
        $content,
        '(?m)^DATA_PILOT_ENCRYPTION_KEY=.*$',
        "DATA_PILOT_ENCRYPTION_KEY=$generatedKey")
}
else {
    $content = $content.TrimEnd() + "`r`nDATA_PILOT_ENCRYPTION_KEY=$generatedKey`r`n"
}

[System.IO.File]::WriteAllText(
    $resolvedPath,
    $content,
    (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'Generated a local AES-256 encryption key in .env.'
