$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$packageRoot = Join-Path $projectRoot ('build/remote-crawl-agent/' + [guid]::NewGuid().ToString('N'))
$archivePath = Join-Path $projectRoot 'frontend/public/downloads/autotest-crawl-agent.zip'
New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $packageRoot 'remote-agent') -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $archivePath) -Force | Out-Null
foreach ($file in @('package.json', 'package-lock.json')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "crawler-service/$file") -Destination $packageRoot -Force
}
foreach ($file in @('agent.js','engine.js','walker.js','screen.js','navigation.js','probe.js','protocol.js','Install.ps1','Start-Agent.ps1')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "crawler-service/remote-agent/$file") -Destination (Join-Path $packageRoot 'remote-agent') -Force
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/REMOTE_CRAWL_AGENT.md') -Destination (Join-Path $packageRoot 'README.md') -Force
Compress-Archive -Path (Join-Path $packageRoot '*') -DestinationPath $archivePath -Force
Write-Host "Created $archivePath"
