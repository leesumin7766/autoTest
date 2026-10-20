$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    Set-Location ..
    if (!(Get-Command node -ErrorAction SilentlyContinue)) { throw 'Install Node.js 24 LTS first.' }
    $nodeVersion = & node -p 'process.versions.node.split(".")[0]'
    if ($nodeVersion -ne '24') { throw 'This agent requires Node.js 24 LTS.' }
    & npm.cmd ci --omit=dev
    if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
    & npx.cmd playwright install chromium
    if ($LASTEXITCODE -ne 0) { throw 'Chromium installation failed.' }
    $config = Join-Path (Get-Location) 'agent-config.json'
    if (Test-Path -LiteralPath $config) {
        $acl = Get-Acl -LiteralPath $config
        $acl.SetAccessRuleProtection($true, $false)
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
        $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')
        $acl.SetAccessRule($rule)
        Set-Acl -LiteralPath $config -AclObject $acl
    }
    Write-Host 'Installation complete. Run remote-agent\Start-Agent.ps1 to connect.'
} finally { Pop-Location }
