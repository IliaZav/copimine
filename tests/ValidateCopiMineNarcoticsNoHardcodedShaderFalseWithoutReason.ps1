$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$visual = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
if ($visual -match 'supportsShaderRuntime\(\)\s*\{\s*return false;\s*\}') {
  throw 'supportsShaderRuntime is still a hardcoded false stub.'
}
if ($visual -notmatch [regex]::Escape('shaderSupportReason()')) { throw 'Shader reason method missing.' }
if ($visual -notmatch [regex]::Escape('manifestFlag("shader_supported")')) { throw 'Shader capability is not tied to manifest detection.' }
Write-Host 'Shader capability is not a hardcoded false stub.'
