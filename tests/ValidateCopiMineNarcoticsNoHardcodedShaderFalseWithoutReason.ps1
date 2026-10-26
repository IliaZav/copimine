$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$visual = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($method in @('supportsShaderRuntime()', 'supportsClientShaderLikeRuntime()', 'supportsClientZipShaderpackRuntime()')) {
    if ($visual -notmatch ([regex]::Escape($method) + '\s*\{\s*return false;\s*\}')) {
        throw "$method must stay explicitly false while narcotics shaders are retired."
    }
}
if ($visual -notmatch [regex]::Escape('shaderSupportReason()')) { throw 'Shader reason method missing.' }
if ($visual -notmatch 'narcotics screen shaders retired') { throw 'Runtime must explain that narcotics screen shaders are retired.' }
if ($visual -match 'sendVisualStart|sendVisualRefresh') { throw 'Retiring shaders must remove every visual start/refresh call.' }
Write-Host 'Retired shader capability is explicit and explained.'