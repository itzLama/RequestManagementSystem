param(
    [ValidateSet("round-trip", "put-persistence", "verify-persistence", "unauthorized")]
    [string]$Mode = "round-trip"
)

$ErrorActionPreference = "Stop"
$pocDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $pocDirectory ".env"

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Create documentation/poc/rustfs-storage/.env from .env.example first."
}

foreach ($line in Get-Content -LiteralPath $envFile) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
        continue
    }

    $separator = $trimmed.IndexOf("=")
    if ($separator -lt 1) {
        throw "Invalid .env entry. Expected NAME=value."
    }

    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim()
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

$env:RUSTFS_POC_ENABLED = "true"
$wrapper = Join-Path $pocDirectory "../../../backend/mvnw.cmd"
& $wrapper -f (Join-Path $pocDirectory "pom.xml") exec:java "-Dexec.args=$Mode"
if ($LASTEXITCODE -ne 0) {
    throw "RustFS POC failed with exit code $LASTEXITCODE."
}
