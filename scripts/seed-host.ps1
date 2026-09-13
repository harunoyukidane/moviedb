<#
.SYNOPSIS
  Seed the demo by running the TMDB importer ON THE HOST (not in a container).

.DESCRIPTION
  Use this when Docker containers cannot reach the public internet (e.g. Cloudflare
  WARP / a VPN tunnels the host but not the Docker VM), which makes the in-container
  importer time out on TMDB. The host has connectivity, so we:
    1. publish People's gRPC port to localhost (via compose.host-seed.yaml),
    2. build the importer on the host,
    3. run it against localhost targets.

  Requires TMDB_READ_TOKEN in .env (or the environment) and the stack already up.

.EXAMPLE
  ./scripts/seed-host.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# load .env
if (Test-Path .env) {
  Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
      $idx = $line.IndexOf('=')
      $name = $line.Substring(0, $idx).Trim()
      $value = $line.Substring($idx + 1).Trim()
      if ($value.Length -ge 2 -and (($value[0] -eq '"' -and $value[-1] -eq '"') -or ($value[0] -eq "'" -and $value[-1] -eq "'"))) {
        $value = $value.Substring(1, $value.Length - 2)
      }
      [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
  }
}

if ([string]::IsNullOrWhiteSpace($env:TMDB_READ_TOKEN)) {
  Write-Error 'TMDB_READ_TOKEN is not set (put it in .env). Cannot seed.'
  exit 2
}

# 1. bring up the stack with People gRPC published to localhost
Write-Host '==> Ensuring stack is up with People gRPC published to localhost...'
docker compose -f compose.yaml -f compose.host-seed.yaml up -d --wait
if ($LASTEXITCODE -ne 0) { Write-Error 'docker compose up failed.'; exit 1 }

# 2. build the importer on the host
Write-Host '==> Building the importer on the host...'
Push-Location demo/importer
try {
  if (-not (Test-Path node_modules)) { npm install --no-audit --no-fund }
  npm run build
  if ($LASTEXITCODE -ne 0) { throw 'importer build failed' }

  # 3. run against localhost targets (host has internet via WARP)
  Write-Host '==> Running the importer on the host...'
  $env:PEOPLE_GRPC_TARGET = '127.0.0.1:9090'
  $env:PEOPLE_HTTP_URL = 'http://127.0.0.1:8081'
  $env:CATALOGUE_GRAPHQL_URL = 'http://127.0.0.1:8080/graphql'
  $env:CATALOGUE_HTTP_URL = 'http://127.0.0.1:8080'
  $env:TMDB_MANIFEST_PATH = (Join-Path $repoRoot 'demo/tmdb-movie-ids.txt')
  # If the network intercepts TLS (e.g. Cloudflare WARP), Node's fetch rejects the
  # proxy cert. Trust it by exporting the proxy root CA and pointing Node at a
  # bundle that also preserves any pre-existing NODE_EXTRA_CA_CERTS.
  $caBundle = Join-Path $repoRoot 'demo/ca-bundle.pem'
  $exportScript = Join-Path $PSScriptRoot 'export-proxy-ca.ps1'
  & $exportScript | Out-Null
  $proxyCa = Join-Path $repoRoot 'demo/proxy-ca.pem'
  $parts = @()
  if ($env:NODE_EXTRA_CA_CERTS -and (Test-Path $env:NODE_EXTRA_CA_CERTS)) { $parts += (Get-Content $env:NODE_EXTRA_CA_CERTS -Raw) }
  if (Test-Path $proxyCa) { $parts += (Get-Content $proxyCa -Raw) }
  if ($parts.Count -gt 0) {
    Set-Content -Path $caBundle -Value ($parts -join "`n") -Encoding ascii
    $env:NODE_EXTRA_CA_CERTS = $caBundle
    Write-Host ('    (using CA bundle for TLS-intercepting proxy: ' + $caBundle + ')')
  }
  npm run seed
  $code = $LASTEXITCODE
} finally {
  Pop-Location
}

if ($code -eq 0) {
  Write-Host '==> Seed complete.'
} else {
  Write-Warning "importer exited with code $code; some items may not have imported."
}
exit $code
