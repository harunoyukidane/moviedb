$thumb = '4445A1872326875B2C8014D4276E88FD8A227B01'
$cert = Get-ChildItem 'Cert:\LocalMachine\Root', 'Cert:\CurrentUser\Root' -ErrorAction SilentlyContinue |
  Where-Object { $_.Thumbprint -eq $thumb } | Select-Object -First 1
if (-not $cert) { Write-Error 'CA not found'; exit 1 }
$pem = "-----BEGIN CERTIFICATE-----`n"
$pem += [System.Convert]::ToBase64String($cert.RawData, 'InsertLineBreaks')
$pem += "`n-----END CERTIFICATE-----`n"
$out = Join-Path (Split-Path -Parent $PSScriptRoot) 'demo\proxy-ca.pem'
Set-Content -Path $out -Value $pem -Encoding ascii
Write-Output ('wrote ' + $out)
