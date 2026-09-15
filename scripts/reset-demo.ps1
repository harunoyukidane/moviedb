<#
.SYNOPSIS
  Reset the demo (§12.2). Stops the stack and removes ONLY this project's named
  Compose volumes (databases + artwork). Local demo data will be permanently
  removed. Native PowerShell port of scripts/reset-demo.sh.

.PARAMETER Yes
  Skip the confirmation prompt (non-interactive).

.EXAMPLE
  ./scripts/reset-demo.ps1
.EXAMPLE
  ./scripts/reset-demo.ps1 -Yes
#>
[CmdletBinding()]
param(
  [switch]$Yes
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host 'WARNING: this will remove this project''s containers AND its named volumes:'
Write-Host '  - catalogue-db-data, people-db-data            (all movies, people, credits)'
Write-Host '  - minio-data                                   (all uploaded images/artwork)'
Write-Host 'Local demo data will be permanently deleted. Other Docker projects are untouched.'

if (-not $Yes) {
  $reply = Read-Host 'Proceed? [y/N]'
  if ($reply -notmatch '^(y|Y|yes|YES)$') {
    Write-Host 'Aborted.'
    exit 0
  }
}

# `down -v` removes only the volumes declared in THIS compose project.
Write-Host '==> Removing this project''s containers and named volumes...'
docker compose --profile seed down -v --remove-orphans
Write-Host '==> Done. Run ./scripts/setup.ps1 to rebuild and reseed.'
