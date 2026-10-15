param(
  [Parameter(Mandatory = $true)][string]$JavaHome,
  [Parameter(Mandatory = $true)][string]$MavenRepository,
  [Parameter(Mandatory = $true)][string]$PaperApiJar
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$profile = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$lock = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/java-plugin-compile-dependencies.lock.json') -Raw | ConvertFrom-Json
[xml]$metadata = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/gradle/verification-metadata.xml')
$version = (& (Join-Path $JavaHome 'bin/java.exe') --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $version -notmatch ('(?m)^openjdk ' + [regex]::Escape($profile.buildJavaVersion) + ' ')) {
  throw "Paper 26.3 compile regression requires exact Java $($profile.buildJavaVersion)."
}
if ((Get-FileHash -LiteralPath $PaperApiJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $profile.paper.apiSha256) {
  throw 'Paper 26.3 compile regression requires the exact profile API artifact.'
}
# Fall back before the repair exists, so RED reproduces the real compiler error.
$selected = if ($lock.paper26_3Dependencies) { @($lock.paper26_3Dependencies) } else { @($lock.dependencies) }
$classpath = @($PaperApiJar)
foreach ($entry in $selected) {
  $path = Join-Path $MavenRepository $entry.path
  if ((Get-Item -LiteralPath $path).Length -ne $entry.size -or
      (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
    throw "Paper 26.3 regression dependency failed size/SHA256 verification: $($entry.path)"
  }
  $classpath += $path
}
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('copimine-paper263-compile-' + [guid]::NewGuid().ToString('N'))
try {
  New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
  $source = Join-Path $temporaryRoot 'PaperAdventureSignatureProbe.java'
  $probeSource = @'
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
final class PaperAdventureSignatureProbe {
    void inspect(Player player) {
        player.getUniqueId();
        player.sendMessage(Component.text("locked Adventure signature"));
    }
}
'@
  [IO.File]::WriteAllText($source, $probeSource, [Text.UTF8Encoding]::new($false))
  & (Join-Path $JavaHome 'bin/javac.exe') -proc:none -encoding UTF-8 -cp ($classpath -join [IO.Path]::PathSeparator) -d $temporaryRoot $source
  if ($LASTEXITCODE -ne 0) { throw 'Paper 26.3 Adventure public-signature regression failed to compile.' }
  if ($lock.paper26_3Api -cne $profile.paper.api) { throw 'Paper 26.3 dependency graph targets a different API version.' }
  foreach ($entry in $selected) {
    $filename = ($entry.path -split '/')[-1]
    $verified = @($metadata.'verification-metadata'.components.component.artifact | Where-Object { $_.name -ceq $filename })
    if ($verified.Count -ne 1 -or $verified[0].sha256.value -cne $entry.sha256) {
      throw "Paper 26.3 dependency is not pinned by existing Gradle verification metadata: $($entry.path)"
    }
  }
} finally {
  $resolvedTemporary = [IO.Path]::GetFullPath($temporaryRoot)
  $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
  if (-not $resolvedTemporary.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Compile regression cleanup escaped the temporary directory.' }
  Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host 'Paper 26.3 locked Adventure signature compilation passed.'
