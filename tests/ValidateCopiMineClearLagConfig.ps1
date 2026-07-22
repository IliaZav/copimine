$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$jar = Join-Path $root 'minecraft\server\plugins\ClearLag.jar'
$config = Join-Path $root 'minecraft\server\plugins\ClearLag\config.yml'
$messages = Join-Path $root 'minecraft\server\plugins\ClearLag\messages.yml'
$packaging = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'scripts\package_full_release.ps1')
$versions = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'deploy\plugin_versions.json')
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($path in @($jar, $config, $messages)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing ClearLag release artifact: $path") }
}

if (Test-Path -LiteralPath $jar) {
  $sha1 = (Get-FileHash -LiteralPath $jar -Algorithm SHA1).Hash.ToLowerInvariant()
  if ($sha1 -ne 'fef74c3598f2de56ae4b134cf5b2e300cb40b302') { $errors.Add("Unexpected ClearLag SHA1: $sha1") }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
  try {
    $entry = $zip.GetEntry('plugin.yml')
    if ($null -eq $entry) { $errors.Add('ClearLag.jar does not contain plugin.yml.') }
    else {
      $reader = [System.IO.StreamReader]::new($entry.Open())
      try { $pluginYml = $reader.ReadToEnd() } finally { $reader.Dispose() }
      if ($pluginYml -notmatch '(?m)^name:\s*ClearLag\s*$') { $errors.Add('ClearLag plugin.yml name is incorrect.') }
      if ($pluginYml -notmatch '(?m)^version:\s*1\.12\.1\s*$') { $errors.Add('ClearLag plugin.yml version must be 1.12.1.') }
    }
  } finally { $zip.Dispose() }
}

if (Test-Path -LiteralPath $config) {
  $text = Get-Content -Raw -Encoding UTF8 $config
  $checks = @(
    @{ Pattern = '(?m)^\s+enabled:\s*false\s*$'; Message = 'ClearLag must not enable mob spawn limits, AI despawn, dynamic view distance, or live updates.' },
    @{ Pattern = '(?ms)^auto-clear:.*?clear-items:\s*true.*?clear-entities:\s*false'; Message = 'ClearLag auto-clear must target dropped items only.' },
    @{ Pattern = '(?ms)^\s+redstone:\s*.*?enabled:\s*false'; Message = 'ClearLag must not alter redstone timing.' },
    @{ Pattern = '(?m)^\s+tnt-no-drops:\s*false\s*$'; Message = 'ClearLag must not remove TNT drops.' },
    @{ Pattern = '(?ms)^\s+item-merge-optimization:.*?enabled:\s*true'; Message = 'ClearLag item merge optimization must be enabled.' },
    @{ Pattern = '(?ms)^\s{2}performance-warnings:.*?enabled:\s*false'; Message = 'ClearLag performance warnings must stay disabled to avoid chat noise.' },
    @{ Pattern = '(?ms)^\s+excluded-items:.*?NETHERITE_SWORD.*?TOTEM_OF_UNDYING.*?COMPASS'; Message = 'ClearLag must protect CopiMine artifact base materials from item cleanup.' },
    @{ Pattern = '(?ms)^dynamic-view-distance:.*?enabled:\s*false'; Message = 'ClearLag must not change the server view distance.' },
    @{ Pattern = '(?ms)^\s+mob-spawn-limiter:.*?enabled:\s*false'; Message = 'ClearLag must not limit mob spawning.' },
    @{ Pattern = '(?ms)^\s+ai-optimization:.*?enabled:\s*false'; Message = 'ClearLag must not despawn distant mobs.' }
  )
  foreach ($check in $checks) { if ($text -notmatch $check.Pattern) { $errors.Add($check.Message) } }
}

if ($packaging -notmatch [regex]::Escape('minecraft\server\plugins\ClearLag.jar')) { $errors.Add('Release packaging must include ClearLag.jar.') }
if ($packaging -notmatch [regex]::Escape('minecraft\server\plugins\ClearLag\config.yml')) { $errors.Add('Release packaging must include ClearLag config.') }
if ($versions -notmatch '"ClearLag\.jar":\s*"1\.12\.1"') { $errors.Add('deploy/plugin_versions.json must record ClearLag 1.12.1.') }

if ($errors.Count -gt 0) { throw ("ClearLag validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ClearLag validation passed: release jar is pinned and only safe dropped-item cleanup is enabled.'
