[CmdletBinding()]
param([Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
. (Join-Path $PSScriptRoot 'DownloadPinnedArtifact.ps1')
. (Join-Path $PSScriptRoot 'InvokeMigrationGit.ps1')
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$lock=Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/coreprotect-build.lock.json') -Raw | ConvertFrom-Json
$profile=Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$pluginLock=Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/server-plugins.lock.json') -Raw | ConvertFrom-Json
$candidateLock=$pluginLock.modules | Where-Object { $_.pluginName -eq 'CoreProtect' } | Select-Object -First 1
if(-not $candidateLock -or $candidateLock.buildSize -le 0 -or
   $candidateLock.buildSha256 -notmatch '^[0-9a-f]{64}$' -or
   $candidateLock.buildSha512 -notmatch '^[0-9a-f]{128}$'){
 throw 'CoreProtect migration output is not pinned in the server plugin lock'
}
$java=Join-Path $JavaHome 'bin/java.exe'
$version=(& $java --version | Out-String)
if($LASTEXITCODE -ne 0 -or $version -notmatch '(?m)^(?:openjdk|java)(?: version)?[ "]+(?<major>\d+)' -or
   [int]$Matches.major -lt $lock.minimumJava -or [int]$Matches.major -ne $profile.buildJavaMajor){
 throw "CoreProtect migration requires reproducible Java $($profile.buildJavaMajor) build tooling"
}
$cache=Join-Path $root 'build/minecraft-26.3'
Assert-ProfileDirectoryDestination -Path $cache -Description 'CoreProtect migration cache' -StopAt $root
$sourceParent=Join-Path $cache 'upstream'
$source=Join-Path $cache 'upstream/CoreProtect'
Assert-ProfileDirectoryDestination -Path $sourceParent -Description 'CoreProtect upstream cache parent' -StopAt $root
Assert-ProfileDirectoryDestination -Path $source -Description 'CoreProtect source cache' -StopAt $root
Assert-DirectoryTreeNoReparse -Path $source -Description 'CoreProtect source cache' -StopAt $root
$gitMetadataPath=Join-Path $source '.git'
Assert-MigrationGitCacheReadyForInitialization -RepositoryPath $source
$gitHooksPath=Join-Path ([IO.Path]::GetTempPath()) ('copimine-migration-no-hooks-'+[guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($gitHooksPath) | Out-Null
try {
 if(-not (Test-Path -LiteralPath $gitMetadataPath)){
  New-Item -ItemType Directory -Path $sourceParent -Force | Out-Null
  Assert-ProfileDirectoryDestination -Path $sourceParent -Description 'CoreProtect upstream cache parent' -StopAt $root
  New-Item -ItemType Directory -Path $source -Force | Out-Null
  Assert-ProfileDirectoryDestination -Path $source -Description 'CoreProtect source cache' -StopAt $root
  Assert-DirectoryTreeNoReparse -Path $source -Description 'CoreProtect source cache' -StopAt $root
  Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('init')
  if($LASTEXITCODE -ne 0){throw 'CoreProtect source repository initialization failed'}
  Assert-ProfileDirectoryDestination -Path $gitMetadataPath -Description 'CoreProtect Git metadata' -StopAt $root
  Add-MigrationGitOrigin -RepositoryPath $source -HooksPath $gitHooksPath -ExpectedRemote $lock.repository
 }
 Assert-MigrationGitCacheSafe -RepositoryPath $source -HooksPath $gitHooksPath -ExpectedRemote $lock.repository
 Assert-ProfileDirectoryDestination -Path $source -Description 'CoreProtect source cache' -StopAt $root
 Assert-DirectoryTreeNoReparse -Path $source -Description 'CoreProtect source cache' -StopAt $root
 Assert-ProfileDirectoryDestination -Path (Join-Path $source '.git') -Description 'CoreProtect Git metadata' -StopAt $root
 $origin=(Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('remote','get-url','origin') | Out-String).Trim()
 if($LASTEXITCODE -ne 0 -or $origin -ne $lock.repository){throw 'CoreProtect upstream remote does not match the pinned repository'}
 Assert-MigrationGitCacheSafe -RepositoryPath $source -HooksPath $gitHooksPath -ExpectedRemote $lock.repository
 $dirty=(Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('status','--porcelain','--untracked-files=all') | Out-String).Trim()
 if($LASTEXITCODE -ne 0 -or $dirty){throw 'Modified CoreProtect source checkout refused'}
 Assert-MigrationGitCacheSafe -RepositoryPath $source -HooksPath $gitHooksPath -ExpectedRemote $lock.repository
 $head=(Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('rev-parse','--verify','--quiet','HEAD') | Out-String).Trim()
 if($LASTEXITCODE -ne 0 -or $head -ne $lock.commit){
  Assert-ProfileDirectoryDestination -Path $source -Description 'CoreProtect source cache' -StopAt $root
  Assert-DirectoryTreeNoReparse -Path $source -Description 'CoreProtect source cache' -StopAt $root
  Assert-ProfileDirectoryDestination -Path (Join-Path $source '.git') -Description 'CoreProtect Git metadata' -StopAt $root
  Assert-MigrationGitCacheSafe -RepositoryPath $source -HooksPath $gitHooksPath -ExpectedRemote $lock.repository
  Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('fetch','--depth=1','origin',$lock.commit)
  if($LASTEXITCODE -ne 0){throw 'Pinned CoreProtect commit fetch failed'}
  Assert-ProfileDirectoryDestination -Path $source -Description 'CoreProtect source cache' -StopAt $root
  Assert-DirectoryTreeNoReparse -Path $source -Description 'CoreProtect source cache' -StopAt $root
  Assert-ProfileDirectoryDestination -Path (Join-Path $source '.git') -Description 'CoreProtect Git metadata' -StopAt $root
  Assert-MigrationGitCacheSafe -RepositoryPath $source -HooksPath $gitHooksPath -ExpectedRemote $lock.repository
  Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('checkout','--detach','FETCH_HEAD')
  if($LASTEXITCODE -ne 0){throw 'Pinned CoreProtect commit checkout failed'}
 }
 Assert-MigrationGitCacheSafe -RepositoryPath $source -HooksPath $gitHooksPath -ExpectedRemote $lock.repository
 $head=(Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('rev-parse','HEAD') | Out-String).Trim()
 if($LASTEXITCODE -ne 0 -or $head -ne $lock.commit){throw 'CoreProtect upstream source is not the pinned commit'}
 Assert-MigrationGitCacheSafe -RepositoryPath $source -HooksPath $gitHooksPath -ExpectedRemote $lock.repository
 $dirty=(Invoke-MigrationGit -RepositoryPath $source -HooksPath $gitHooksPath -GitArguments @('status','--porcelain','--untracked-files=all') | Out-String).Trim()
 if($LASTEXITCODE -ne 0 -or $dirty){throw 'Modified CoreProtect source refused'}
} finally {
 if([IO.Directory]::Exists($gitHooksPath) -and @([IO.Directory]::GetFileSystemEntries($gitHooksPath)).Count -eq 0){
  [IO.Directory]::Delete($gitHooksPath,$false)
 }
}
$toolchain=Join-Path $cache 'toolchain'
Assert-ProfileDirectoryDestination -Path $toolchain -Description 'CoreProtect toolchain' -StopAt $root
New-Item -ItemType Directory -Path $toolchain -Force | Out-Null
Assert-ProfileDirectoryDestination -Path $toolchain -Description 'CoreProtect toolchain' -StopAt $root
$archive=Join-Path $toolchain ('apache-maven-'+$lock.maven.version+'-bin.zip')
Assert-AtomicFileDestination -Path $archive -Description 'CoreProtect Maven archive' -StopAt $root
$mavenCacheValid=$false
if(Test-Path -LiteralPath $archive -PathType Leaf){
 if((Get-Item -LiteralPath $archive).Length -eq [long]$lock.maven.size){
  $mavenCacheValid=(Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash.ToLowerInvariant() -eq $lock.maven.sha512
 }
}
if(-not $mavenCacheValid){
 Save-PinnedArtifact -Uri $lock.maven.url -Destination $archive -Algorithm SHA512 `
  -ExpectedHash $lock.maven.sha512 -ExpectedSize ([long]$lock.maven.size) -MaximumBytes ([long]$lock.maven.size) -StopAt $root
}
$maven=Join-Path $toolchain ('apache-maven-'+$lock.maven.version+'/bin/mvn.cmd')
Install-PinnedToolArchive -Archive $archive -Algorithm SHA512 -ExpectedHash $lock.maven.sha512 `
 -ExpectedSize ([long]$lock.maven.size) -DestinationDirectory (Join-Path $toolchain ('apache-maven-'+$lock.maven.version)) `
 -ExecutableRelativePath 'bin/mvn.cmd' -StopAt $root
$buildOutput=Join-Path $source 'target'
Assert-DirectoryTreeNoReparse -Path $buildOutput -Description 'CoreProtect build output' -StopAt $root
Reset-GeneratedDirectory -Path $buildOutput -Description 'CoreProtect build output' -StopAt $root
$previousJava=$env:JAVA_HOME
try {
 $env:JAVA_HOME=(Resolve-Path -LiteralPath $JavaHome).Path
 & $maven --batch-mode --no-transfer-progress --file (Join-Path $source 'pom.xml') ('-Dproject.branch='+$lock.branchLabel) '-DskipTests=false' verify
 if($LASTEXITCODE -ne 0){throw 'Pinned CoreProtect source build failed'}
 Assert-DirectoryTreeNoReparse -Path $buildOutput -Description 'CoreProtect build output' -StopAt $root
} finally {$env:JAVA_HOME=$previousJava}
$jar=Join-Path $source 'target/CoreProtect-24.1.jar'
Assert-AtomicFileDestination -Path $jar -Description 'CoreProtect build artifact' -StopAt $root
if(-not (Test-Path -LiteralPath $jar)){throw 'CoreProtect build artifact missing'}
$destination=Join-Path $cache 'server-plugins/CoreProtect-CE-24.1-26.3-upstream.jar'
Assert-ProfileDirectoryDestination -Path (Split-Path -Parent $destination) -Description 'CoreProtect candidate directory' -StopAt $root
Assert-AtomicFileDestination -Path $destination -Description 'CoreProtect candidate' -StopAt $root
New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
Assert-ProfileDirectoryDestination -Path (Split-Path -Parent $destination) -Description 'CoreProtect candidate directory' -StopAt $root
Assert-AtomicFileDestination -Path $destination -Description 'CoreProtect candidate' -StopAt $root
$artifactBackup=Copy-PinnedArtifactAtomically -Source $jar -Destination $destination -Algorithm SHA512 `
 -ExpectedHash $candidateLock.buildSha512 -ExpectedSize ([long]$candidateLock.buildSize) -StopAt $root
$receiptPath=$destination+'.receipt.json'
Assert-AtomicFileDestination -Path $receiptPath -Description 'CoreProtect build receipt' -StopAt $root
$receipt=@{sourceCommit=$lock.commit;source=$lock.source;license=$lock.license;sha512=$candidateLock.buildSha512;size=[long]$candidateLock.buildSize;nativeVerified=$false} | ConvertTo-Json
try {
 Save-TextFileAtomically -Path $receiptPath -Content $receipt -Encoding ([Text.UTF8Encoding]::new($true)) -StopAt $root
} catch {
 if($artifactBackup -and (Test-Path -LiteralPath $artifactBackup -PathType Leaf)){
  [IO.File]::Replace($artifactBackup,$destination,[NullString]::Value)
 } elseif(Test-Path -LiteralPath $destination -PathType Leaf){
  Remove-Item -LiteralPath $destination -Force -ErrorAction Stop
 }
 throw
}
if($artifactBackup -and (Test-Path -LiteralPath $artifactBackup -PathType Leaf)){
 Remove-Item -LiteralPath $artifactBackup -Force -ErrorAction Stop
}
Write-Output ('Built unmodified CoreProtect upstream '+$head+'; server acceptance still required')
