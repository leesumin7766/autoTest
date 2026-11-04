$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    Set-Location ..
    if (!(Test-Path -LiteralPath 'agent-config.json')) { throw 'Place agent-config.json next to package.json first.' }
    # HTTPS 관리 서버(사내 CA): Windows 신뢰 저장소에 설치한 인증서 또는 package.json 옆의 server-ca.crt 를 신뢰한다.
    $serverCa = Join-Path (Get-Location) 'server-ca.crt'
    if (Test-Path -LiteralPath $serverCa) { $env:NODE_EXTRA_CA_CERTS = $serverCa }
    & node --use-system-ca remote-agent/agent.js agent-config.json
    if ($LASTEXITCODE -ne 0) { throw 'Agent stopped. Check configuration and installation.' }
} finally { Pop-Location }
