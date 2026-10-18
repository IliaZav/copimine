[CmdletBinding()]
param(
    [string]$JavaHome,
    [string]$InputJarPath,
    [string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
. (Join-Path $root 'scripts/minecraft/DownloadPinnedArtifact.ps1')
$profile=Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$lock=Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/server-plugins.lock.json') -Raw | ConvertFrom-Json
$record=@($lock.modules | Where-Object { $_.pluginName -ceq 'GrimAC' })
if($record.Count -ne 1){throw 'The 26.3 plugin lock must contain exactly one GrimAC candidate'}
$record=$record[0]
$defaultOutputDirectory=Join-Path $root 'build/minecraft-26.3/server-plugins'
$outputDirectory=if([string]::IsNullOrWhiteSpace($OutputDirectory)){$defaultOutputDirectory}else{$OutputDirectory}
if(-not [IO.Path]::IsPathRooted($outputDirectory)){$outputDirectory=Join-Path $root $outputDirectory}
$rootPrefix=[IO.Path]::GetFullPath($root).TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
$outputFullPath=[IO.Path]::GetFullPath($outputDirectory)
if(-not $outputFullPath.StartsWith($rootPrefix,[StringComparison]::OrdinalIgnoreCase)){
    throw 'Grim patch output directory must be inside the repository build tree'
}
if(-not (Test-Path -LiteralPath $outputFullPath -PathType Container)){
    New-Item -ItemType Directory -Path $outputFullPath -Force | Out-Null
}
Assert-DirectoryTreeNoReparse -Path $outputFullPath -Description 'Grim patch output directory' -StopAt $root
$inputJar=if([string]::IsNullOrWhiteSpace($InputJarPath)){
    Join-Path $defaultOutputDirectory $record.upstreamFilename
}else{$InputJarPath}
Assert-AtomicFileDestination -Path $inputJar -Description 'Grim upstream input JAR' -StopAt $root
if(-not (Test-Path -LiteralPath $inputJar -PathType Leaf)){throw 'Locked upstream Grim artifact is missing'}
$expected=$record.upstreamSha256
if((Get-FileHash -LiteralPath $inputJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected){throw 'Unexpected upstream Grim artifact refused'}
$bin=if($JavaHome){Join-Path $JavaHome 'bin'}else{Split-Path -Parent (Get-Command javac).Source}
$java=Join-Path $bin 'java.exe'
$version=(& $java --version | Out-String)
if($LASTEXITCODE -ne 0 -or $version -notmatch '(?m)^(?:openjdk|java)(?: version)?[ " ]+(?<major>\d+)' -or
   [int]$Matches.major -ne $profile.grimPatchJavaMajor){throw "Grim patch reproducibility requires Java $($profile.grimPatchJavaMajor)"}
$source=Join-Path $PSScriptRoot 'src/ac/grim/grimac/utils/lists/CorrectingPlayerInventoryStorage.java'
$classes=Join-Path $outputFullPath 'grim-patch-classes'
$outputJar=Join-Path $outputFullPath $record.filename
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
