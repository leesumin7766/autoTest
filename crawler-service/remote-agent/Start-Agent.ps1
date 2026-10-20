$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    Set-Location ..
    if (!(Test-Path -LiteralPath 'agent-config.json')) { throw 'Place agent-config.json next to package.json first.' }
    & node remote-agent/agent.js agent-config.json
    if ($LASTEXITCODE -ne 0) { throw 'Agent stopped. Check configuration and installation.' }
} finally { Pop-Location }
