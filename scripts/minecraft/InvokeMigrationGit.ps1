function Invoke-MigrationGit {
 [CmdletBinding()]
 param(
  [Parameter(Mandatory)][string]$RepositoryPath,
  [Parameter(Mandatory)][string]$HooksPath,
  [Parameter(Mandatory)][string[]]$GitArguments
 )
 $gitApplications=@($ExecutionContext.InvokeCommand.GetCommands('git.exe',[Management.Automation.CommandTypes]::Application,$true))
 if($gitApplications.Count -eq 0 -or -not [IO.Path]::IsPathRooted($gitApplications[0].Path) -or -not [IO.File]::Exists($gitApplications[0].Path)){
  throw 'A Git executable application could not be resolved safely'
 }
 $gitExecutable=$gitApplications[0].Path
 $hooks=Get-Item -LiteralPath $HooksPath -Force -ErrorAction Stop
 if(-not $hooks.PSIsContainer -or ($hooks.Attributes -band [IO.FileAttributes]::ReparsePoint)){
  throw 'Migration Git hooks path must be a regular directory'
 }
 if(@([IO.Directory]::GetFileSystemEntries($hooks.FullName)).Count -ne 0){
  throw 'Migration Git hooks path must remain empty'
 }
 $hooksForGit=$hooks.FullName.Replace('\','/')
 $gitEnvironmentNames=@(
  'GIT_CONFIG_NOSYSTEM','GIT_CONFIG_SYSTEM','GIT_CONFIG_GLOBAL','GIT_CONFIG_COUNT','GIT_CONFIG_PARAMETERS','GIT_CONFIG',
  'GIT_TEMPLATE_DIR','GIT_DIR','GIT_WORK_TREE','GIT_INDEX_FILE','GIT_COMMON_DIR','GIT_OBJECT_DIRECTORY',
  'GIT_ALTERNATE_OBJECT_DIRECTORIES','GIT_SHALLOW_FILE','GIT_DEFAULT_HASH','GIT_EXEC_PATH','GIT_NAMESPACE','GIT_PREFIX',
  'GIT_CEILING_DIRECTORIES','GIT_DISCOVERY_ACROSS_FILESYSTEM','GIT_REPLACE_REF_BASE','GIT_NO_REPLACE_OBJECTS',
  'GIT_SSH','GIT_SSH_COMMAND','GIT_SSH_VARIANT','GIT_ASKPASS','SSH_ASKPASS','GIT_PROXY_COMMAND',
  'GIT_SSL_NO_VERIFY','GIT_SSL_CAINFO','GIT_SSL_CAPATH','GIT_SSL_CERT','GIT_SSL_KEY',
  'GIT_PROXY_SSL_CAINFO','GIT_PROXY_SSL_CERT','GIT_PROXY_SSL_KEY','GIT_TERMINAL_PROMPT'
 )
 $previousGitEnvironment=@{}
 foreach($name in $gitEnvironmentNames){$previousGitEnvironment[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
 $isolatedGlobal=[IO.Path]::GetTempFileName()
 try {
  foreach($name in $gitEnvironmentNames){Remove-Item -LiteralPath ('Env:'+$name) -ErrorAction SilentlyContinue}
  $env:GIT_CONFIG_NOSYSTEM='1'
  $env:GIT_CONFIG_GLOBAL=$isolatedGlobal
  $env:GIT_CONFIG_COUNT='0'
  $env:GIT_CONFIG_PARAMETERS=$null
  $env:GIT_CONFIG=$null
  $env:GIT_TEMPLATE_DIR=$null
  $env:GIT_NO_REPLACE_OBJECTS='1'
  $configuredHookKeys=@(& $gitExecutable -c ('core.hooksPath='+$hooksForGit) -C $RepositoryPath config --includes --name-only --get-regexp '^hook\..*\.event$' 2>$null)
  $configExitCode=$LASTEXITCODE
  if($configExitCode -gt 1){throw 'Cannot inspect cached Git hook configuration safely'}
  $friendlyNames=@()
  foreach($key in $configuredHookKeys){
   $keyText=([string]$key).Trim()
   if($keyText.Length -le 11 -or -not $keyText.StartsWith('hook.',[StringComparison]::OrdinalIgnoreCase) -or -not $keyText.EndsWith('.event',[StringComparison]::OrdinalIgnoreCase)){
    throw 'Cached Git hook configuration contains an unreadable name'
   }
   $friendlyName=$keyText.Substring(5,$keyText.Length-11)
   if($friendlyName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$'){
    throw 'Cached Git hook configuration contains an unsupported friendly name'
   }
   $friendlyNames+= $friendlyName
  }
  $gitCommandArguments=@('-c',('core.hooksPath='+$hooksForGit),'-c','core.fsmonitor=false','-c',('init.templateDir='+$hooksForGit))
  foreach($friendlyName in $friendlyNames){$gitCommandArguments+=@('-c',('hook.'+$friendlyName+'.enabled=false'))}
  $gitCommandArguments+=@('-C',$RepositoryPath)
  $gitCommandArguments+=$GitArguments
  & $gitExecutable @gitCommandArguments
 } finally {
  foreach($name in $gitEnvironmentNames){
   $environmentPath='Env:'+$name
   if($null -eq $previousGitEnvironment[$name]){Remove-Item -LiteralPath $environmentPath -ErrorAction SilentlyContinue}
   else{Set-Item -LiteralPath $environmentPath -Value $previousGitEnvironment[$name]}
  }
  [IO.File]::Delete($isolatedGlobal)
 }
}

function Assert-MigrationGitCacheSafe {
 [CmdletBinding()]
 param(
  [Parameter(Mandatory)][string]$RepositoryPath,
  [Parameter(Mandatory)][string]$HooksPath,
  [Parameter(Mandatory)][string]$ExpectedRemote
 )
 $gitMetadataPath=Join-Path $RepositoryPath '.git'
 $gitMetadata=Get-Item -LiteralPath $gitMetadataPath -Force -ErrorAction Stop
 if(-not $gitMetadata.PSIsContainer -or ($gitMetadata.Attributes -band [IO.FileAttributes]::ReparsePoint)){
  throw 'CoreProtect Git metadata must be a regular directory'
 }
 $infoAttributesPath=Join-Path $gitMetadataPath 'info/attributes'
 if(Test-Path -LiteralPath $infoAttributesPath){
  throw 'CoreProtect Git cache with repository-local info attributes is refused'
 }
 $infoExcludePath=Join-Path $gitMetadataPath 'info/exclude'
 $infoExclude=Get-Item -LiteralPath $infoExcludePath -Force -ErrorAction SilentlyContinue
 if($null -ne $infoExclude){
  if($infoExclude.PSIsContainer -or ($infoExclude.Attributes -band [IO.FileAttributes]::ReparsePoint)){
   throw 'CoreProtect Git cache with non-regular repository-local info excludes is refused'
  }
  $activeExcludeRules=@([IO.File]::ReadAllLines($infoExclude.FullName) | Where-Object {
   $line=[string]$_
   $line.Trim().Length -gt 0 -and -not $line.StartsWith('#',[StringComparison]::Ordinal)
  })
  if($activeExcludeRules.Count -gt 0){
   throw 'CoreProtect Git cache with active repository-local info excludes is refused'
  }
 }

 $indexEntries=@(Invoke-MigrationGit -RepositoryPath $RepositoryPath -HooksPath $HooksPath `
  -GitArguments @('ls-files','-v'))
 $indexExitCode=$LASTEXITCODE
 if($indexExitCode -ne 0){throw 'Cannot inspect CoreProtect Git index flags safely'}
 foreach($entry in $indexEntries){
  $entryText=[string]$entry
  if($entryText.Length -lt 2 -or $entryText[1] -ne ' '){
   throw 'CoreProtect Git index contains an unreadable entry'
  }
  $tag=$entryText[0]
  if($tag -cmatch '^[a-z]$' -or $tag -ceq 'S'){
   throw 'CoreProtect Git cache with assume-unchanged or skip-worktree index flags is refused'
  }
 }

 $configKeys=@(Invoke-MigrationGit -RepositoryPath $RepositoryPath -HooksPath $HooksPath `
  -GitArguments @('config','--local','--includes','--name-only','--list'))
 $configExitCode=$LASTEXITCODE
 if($configExitCode -ne 0){throw 'Cannot inspect CoreProtect Git cache configuration safely'}
 $normalizedKeys=@($configKeys | ForEach-Object { ([string]$_).Trim().ToLowerInvariant() })
 $allowedValues=@{
  'core.repositoryformatversion'=@('0')
  'core.filemode'=@('false','true')
  'core.bare'=@('false')
  'core.logallrefupdates'=@('true')
  'core.symlinks'=@('false','true')
  'core.ignorecase'=@('false','true')
  'remote.origin.url'=@($ExpectedRemote)
  'remote.origin.fetch'=@('+refs/heads/*:refs/remotes/origin/*')
 }
 if(@($normalizedKeys | Where-Object { -not $allowedValues.ContainsKey($_) }).Count -gt 0){
  throw 'CoreProtect Git cache contains an unapproved local configuration key'
 }
 if(@($normalizedKeys | Group-Object | Where-Object { $_.Count -ne 1 }).Count -gt 0){
  throw 'CoreProtect Git cache contains duplicate local configuration keys'
 }
 foreach($requiredKey in @('core.repositoryformatversion','core.bare','remote.origin.url','remote.origin.fetch')){
  if($requiredKey -notin $normalizedKeys){throw 'CoreProtect Git cache is missing required default or pinned remote configuration'}
 }
 foreach($key in $normalizedKeys){
  $values=@(Invoke-MigrationGit -RepositoryPath $RepositoryPath -HooksPath $HooksPath `
   -GitArguments @('config','--local','--includes','--get-all',$key))
  $valueExitCode=$LASTEXITCODE
  if($valueExitCode -ne 0 -or $values.Count -ne 1){
   throw 'CoreProtect Git cache configuration has an unexpected value count'
  }
  $value=([string]$values[0]).Trim()
  if($key.StartsWith('core.',[StringComparison]::Ordinal)){$value=$value.ToLowerInvariant()}
  if($value -cnotin $allowedValues[$key]){throw 'CoreProtect Git cache configuration differs from expected defaults or pinned remote'}
 }
}

function Assert-MigrationGitCacheReadyForInitialization {
 [CmdletBinding()]
 param([Parameter(Mandatory)][string]$RepositoryPath)

 if(-not (Test-Path -LiteralPath $RepositoryPath)){return}
 $cache=Get-Item -LiteralPath $RepositoryPath -Force -ErrorAction Stop
 if(-not $cache.PSIsContainer -or ($cache.Attributes -band [IO.FileAttributes]::ReparsePoint)){
  throw 'CoreProtect source cache must be a regular directory before Git initialization'
 }

 $gitMetadataPath=Join-Path $cache.FullName '.git'
 if(Test-Path -LiteralPath $gitMetadataPath){
  $gitMetadata=Get-Item -LiteralPath $gitMetadataPath -Force -ErrorAction Stop
  if(-not $gitMetadata.PSIsContainer -or ($gitMetadata.Attributes -band [IO.FileAttributes]::ReparsePoint)){
   throw 'CoreProtect Git metadata must be a regular directory before cache reuse'
  }
  return
 }

 if(@([IO.Directory]::GetFileSystemEntries($cache.FullName)).Count -gt 0){
  throw "CoreProtect source cache exists without Git metadata; clear this directory before retrying: $($cache.FullName)"
 }
}

function Add-MigrationGitOrigin {
 [CmdletBinding()]
 param(
  [Parameter(Mandatory)][string]$RepositoryPath,
  [Parameter(Mandatory)][string]$HooksPath,
  [Parameter(Mandatory)][string]$ExpectedRemote
 )

 Invoke-MigrationGit -RepositoryPath $RepositoryPath -HooksPath $HooksPath `
  -GitArguments @('remote','add','origin',$ExpectedRemote)
 if($LASTEXITCODE -ne 0){
  throw "CoreProtect origin configuration failed; remove the incomplete source cache before retrying: $RepositoryPath"
 }
}
