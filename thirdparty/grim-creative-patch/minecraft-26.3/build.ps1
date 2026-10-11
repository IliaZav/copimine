[CmdletBinding()]
param([string]$JavaHome)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
. (Join-Path $root 'scripts/minecraft/DownloadPinnedArtifact.ps1')
$profile=Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$stage=Join-Path $root 'build/minecraft-26.3/server-plugins'
$inputJar=Join-Path $stage 'grimac-bukkit-2.3.74-f5bbe9c.jar'
Assert-DirectoryTreeNoReparse -Path $stage -Description 'Grim patch plugin staging directory' -StopAt $root
Assert-AtomicFileDestination -Path $inputJar -Description 'Grim patch input JAR' -StopAt $root
$expected='a6768ab769ac8801b7d03567528424b0a8e7310cecd31dc70d100b521ab959e3'
if((Get-FileHash -LiteralPath $inputJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected){throw 'Unexpected upstream Grim artifact refused'}
$bin=if($JavaHome){Join-Path $JavaHome 'bin'}else{Split-Path -Parent (Get-Command javac).Source}
$java=Join-Path $bin 'java.exe'
$version=(& $java --version | Out-String)
if($LASTEXITCODE -ne 0 -or $version -notmatch '(?m)^(?:openjdk|java)(?: version)?[ " ]+(?<major>\d+)' -or
   [int]$Matches.major -ne $profile.grimPatchJavaMajor){throw "Grim patch reproducibility requires Java $($profile.grimPatchJavaMajor)"}
$source=Join-Path $PSScriptRoot 'src/ac/grim/grimac/utils/lists/CorrectingPlayerInventoryStorage.java'
$classes=Join-Path $root 'build/minecraft-26.3/grim-patch-classes'
$outputJar=Join-Path $stage 'GrimAC-2.3.74-f5bbe9c-creative-fix.jar'
Assert-AtomicFileDestination -Path $source -Description 'Grim patch source' -StopAt $root
Reset-GeneratedDirectory -Path $classes -Description 'Grim patch class output' -StopAt $root
Assert-AtomicFileDestination -Path $outputJar -Description 'Grim patch JAR output' -StopAt $root
& (Join-Path $bin 'javac.exe') --release 17 -encoding UTF-8 -cp $inputJar -d $classes $source
if($LASTEXITCODE -ne 0){throw 'Grim compatibility class compilation failed'}
Assert-DirectoryTreeNoReparse -Path $classes -Description 'Grim patch class output' -StopAt $root
Assert-AtomicFileDestination -Path $outputJar -Description 'Grim patch JAR output' -StopAt $root
if(Test-Path -LiteralPath $outputJar -PathType Leaf){Remove-Item -LiteralPath $outputJar -Force -ErrorAction Stop}
Copy-Item -LiteralPath $inputJar -Destination $outputJar -Force
Assert-AtomicFileDestination -Path $outputJar -Description 'Grim patch JAR output' -StopAt $root
& (Join-Path $bin 'jar.exe') --update --file $outputJar --date=2020-01-01T00:00:00Z -C $classes 'ac/grim/grimac/utils/lists/CorrectingPlayerInventoryStorage.class'
if($LASTEXITCODE -ne 0){throw 'Grim patch packaging failed'}
Assert-AtomicFileDestination -Path $outputJar -Description 'Grim patch JAR output' -StopAt $root
Write-Output ('Grim Creative compatibility patch built SHA256='+((Get-FileHash -LiteralPath $outputJar -Algorithm SHA256).Hash.ToLowerInvariant()))
