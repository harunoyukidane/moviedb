<#
.SYNOPSIS
  Demo bootstrap for Windows PowerShell (§12.2, §12.4). Brings up the stack,
  waiting on Docker health checks (not sleeps), then runs the one-shot TMDB
  importer to seed a browsable catalogue.

.DESCRIPTION
  Native PowerShell port of scripts/setup.sh. Docker Desktop must be running.
  Everything runs locally in Docker containers; nothing is deployed remotely.

.PARAMETER SkipSeed
  Bring up an empty, usable app without seeding.

.PARAMETER Cloudflare
  Seed via the HOST (not the importer container). Use this when Docker containers
  cannot reach the internet because Cloudflare WARP / a VPN tunnels only the host.
  It publishes People's gRPC to localhost, trusts the intercepting proxy CA, and
  runs the importer on the host.

.EXAMPLE
  ./scripts/setup.ps1
.EXAMPLE
  ./scripts/setup.ps1 -SkipSeed
.EXAMPLE
  ./scripts/setup.ps1 -Cloudflare
#>
[CmdletBinding()]
param(
  [switch]$SkipSeed,
  [switch]$Cloudflare
)

$ErrorActionPreference = 'Stop'

# repo root = parent of this script's folder
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# --- 1. validate tooling ---
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Write-Error 'docker is not installed or not on PATH.'
  exit 1
}
try { docker compose version *> $null } catch {
  Write-Error "'docker compose' (v2) is required."
  exit 1
}

# --- load .env if present (TMDB_READ_TOKEN etc.) into this process' env ---
if (Test-Path .env) {
  Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
      $idx = $line.IndexOf('=')
      $name = $line.Substring(0, $idx).Trim()
      $value = $line.Substring($idx + 1).Trim()
      # strip surrounding quotes if present
      if ($value.Length -ge 2 -and (($value[0] -eq '"' -and $value[-1] -eq '"') -or ($value[0] -eq "'" -and $value[-1] -eq "'"))) {
        $value = $value.Substring(1, $value.Length - 2)
      }
      [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
  }
}

# --- 2 & 3. build the boot jars on the host, then build+start containers ---
# The images copy host-built jars (see the Dockerfiles), so build them first.
# The trust-store flag lets Gradle download through a TLS-intercepting proxy on
# Windows; harmless elsewhere.
Write-Host '==> Building service jars on the host...'
Push-Location backend
try {
  & .\gradlew.bat ':catalogue-service:bootJar' ':people-service:bootJar' `
    --no-daemon --console=plain '-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT'
  if ($LASTEXITCODE -ne 0) { throw "gradle bootJar failed (exit $LASTEXITCODE)" }
} finally {
  Pop-Location
}

# The frontend image copies a host-built adapter-node bundle (same rationale as
# the backend images: avoids npm install inside the container).
Write-Host '==> Building the frontend on the host...'
Push-Location frontend
try {
  if (-not (Test-Path node_modules)) { npm install --no-audit --no-fund }
  npm run build
  if ($LASTEXITCODE -ne 0) { throw "frontend build failed (exit $LASTEXITCODE)" }
} finally {
  Pop-Location
}

Write-Host '==> Starting stack (building images, waiting for health)...'
docker compose up -d --build --wait
if ($LASTEXITCODE -ne 0) { Write-Error 'docker compose up failed.'; exit 1 }

$cataloguePort = if ($env:CATALOGUE_HTTP_PORT) { $env:CATALOGUE_HTTP_PORT } else { '8080' }

# --- decide whether to seed ---
$token = $env:TMDB_READ_TOKEN
if ($SkipSeed) {
  Write-Host '==> -SkipSeed given: leaving the app empty.'
}
elseif ([string]::IsNullOrWhiteSpace($token)) {
  # Never fake success (§12.2): explain exactly how to seed later.
  Write-Host ''
  Write-Host '==> TMDB_READ_TOKEN is not set - starting an EMPTY but usable app.'
  Write-Host '    To seed the demo catalogue later:'
  Write-Host '      1. Create a TMDB v4 Read Access Token: https://www.themoviedb.org/settings/api'
  Write-Host '      2. Add it to .env:   TMDB_READ_TOKEN=your-token'
  Write-Host '      3. Re-run:           ./scripts/setup.ps1'
}
else {
  if ($Cloudflare) {
    Write-Host '==> Seeding on the HOST (Cloudflare/WARP mode)...'
    & (Join-Path $PSScriptRoot 'seed-host.ps1')
    if ($LASTEXITCODE -eq 0) { Write-Host '==> Seed complete.' }
    else { Write-Warning "host seed exited with code $LASTEXITCODE; some items may not have imported." }
  }
  else {
    Write-Host '==> Seeding demo data via the one-shot importer (in-container)...'
    # 4. run the one-shot importer (seed profile); it exits non-zero on failure.
    docker compose --profile seed run --rm --build demo-importer
    if ($LASTEXITCODE -eq 0) {
      Write-Host '==> Seed complete.'
    } else {
      Write-Warning "importer exited with code $LASTEXITCODE. The app is up; some items may not have imported."
      Write-Warning 'If containers cannot reach the internet (Cloudflare WARP / VPN), re-run with -Cloudflare.'
    }
  }
}

# --- 5. print URLs, counts, and test commands ---
Write-Host ''
Write-Host '======================================================================'
Write-Host ' MovieDB is up.'
$frontendPort = if ($env:FRONTEND_PORT) { $env:FRONTEND_PORT } else { '4173' }
Write-Host "   Browser UI (BFF):   http://localhost:$frontendPort"
Write-Host "   Catalogue GraphQL:  http://localhost:$cataloguePort/graphql"
Write-Host "   Catalogue health:   http://localhost:$cataloguePort/actuator/health"
Write-Host ''
Write-Host ' Demo record counts (via GraphQL):'
try {
  $body = '{"query":"{ movies(page:{limit:1,offset:0}){ total } }"}'
  $resp = Invoke-RestMethod -Method Post -Uri "http://localhost:$cataloguePort/graphql" `
    -ContentType 'application/json' -Body $body -TimeoutSec 10
  Write-Host "   movies total: $($resp.data.movies.total)"
} catch {
  Write-Host '   (could not query counts; the API may still be warming up)'
}
Write-Host ''
Write-Host ' Try it (PowerShell):'
@'
   Invoke-RestMethod -Method Post -Uri http://localhost:PORT/graphql `
     -ContentType 'application/json' `
     -Body '{"query":"{ movies(page:{limit:5,offset:0}){ total items{ title } } }"}'
'@ -replace 'PORT', $cataloguePort | Write-Host
Write-Host '======================================================================'
