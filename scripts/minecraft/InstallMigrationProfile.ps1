[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ProfileDirectory,
    [switch]$DependenciesOnly,
    [switch]$ValidateOnly,
    [switch]$ValidateDefinitionOnly
)
$ErrorActionPreference = 'Stop'
if ($ValidateOnly -and $ValidateDefinitionOnly) {
    throw 'ValidateOnly and ValidateDefinitionOnly are separate validation modes and cannot be combined'
}
. (Join-Path $PSScriptRoot 'DownloadPinnedArtifact.ps1')
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$profile = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$lock = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/client-mods.lock.json') -Raw | ConvertFrom-Json
$target = (Resolve-Path -LiteralPath $ProfileDirectory).Path
$id = Split-Path -Leaf $target
$migrationReceiptPath = Join-Path $target 'copimine-migration-receipt.json'
$optionsPath = Join-Path $target 'options.txt'
$mods = Join-Path $target 'mods'
$packs = Join-Path $target 'resourcepacks'
$backupRoot = Join-Path $target 'migration-backups'
$backupClientModsRoot = Join-Path $backupRoot 'client-mods'
$backupOptionsRoot = Join-Path $backupRoot 'options'
$definition = Get-Content -LiteralPath (Join-Path $target ($id + '.json')) -Raw | ConvertFrom-Json
if ($definition.downloads.client.sha1 -ne $profile.minecraftClient.sha1) { throw 'Profile is not the pinned Minecraft 26.3 client' }
if ($definition.mainClass -ne 'net.fabricmc.loader.impl.launch.knot.KnotClient' -or
    @($definition.libraries.name) -notcontains ('net.fabricmc:fabric-loader:' + $profile.fabric.loader)) {
    throw 'Profile must use the pinned Fabric Loader'
}
if ($definition.javaVersion.majorVersion -lt $profile.minimumJava) { throw 'Profile requires Java 25 or later' }
if (-not $DependenciesOnly -and -not $ValidateDefinitionOnly -and -not $profile.fabric.clientPortComplete) {
    throw 'CopiMineClient port is incomplete; use DependenciesOnly to prepare external mods and the resource pack'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Get-FileDigest([string]$path, [string]$algorithm) {
    switch ($algorithm) {
        'SHA256' { $hasher = [Security.Cryptography.SHA256]::Create() }
        'SHA512' { $hasher = [Security.Cryptography.SHA512]::Create() }
        default { throw ('Unsupported digest algorithm: ' + $algorithm) }
    }
    $stream = [IO.File]::OpenRead($path)
    try {
        return ([BitConverter]::ToString($hasher.ComputeHash($stream)) -replace '-', '').ToLowerInvariant()
    } finally {
        $stream.Dispose()
        $hasher.Dispose()
    }
}
function Copy-VerifiedFileAtomically([string]$source, [string]$destination, [string]$algorithm, [string]$expectedHash) {
    Assert-AtomicFileDestination -Path $destination -Description 'profile artifact' -StopAt $target
    if (-not (Test-Path -LiteralPath $source -PathType Leaf) -or
        (Get-FileDigest $source $algorithm) -ne $expectedHash) {
        throw 'Staged file verification failed before profile replacement'
    }
    if (Test-Path -LiteralPath $destination) {
        if (Test-Path -LiteralPath $destination -PathType Container) { throw 'Refusing to replace a directory with a migration artifact' }
        if ((Get-FileDigest $destination $algorithm) -eq $expectedHash) { return }
    }
    $temporary = $destination + '.candidate.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    try {
        Copy-Item -LiteralPath $source -Destination $temporary
        if (-not (Test-Path -LiteralPath $temporary -PathType Leaf) -or
            (Get-FileDigest $temporary $algorithm) -ne $expectedHash) {
            throw 'Staged file verification failed before profile replacement'
        }
        if (Test-Path -LiteralPath $destination) {
            if (Test-Path -LiteralPath $destination -PathType Container) { throw 'Refusing to replace a directory with a migration artifact' }
            [IO.File]::Replace($temporary, $destination, [NullString]::Value)
        } else {
            [IO.File]::Move($temporary, $destination)
        }
        if ((Get-FileDigest $destination $algorithm) -ne $expectedHash) {
            throw 'Installed file verification failed after atomic profile replacement'
        }
    } finally {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) { Remove-Item -LiteralPath $temporary -Force }
    }
}
function Add-ProfileTransactionSnapshot([Collections.Generic.List[object]]$journal,
    [string]$path, [string]$transactionRoot) {
    Assert-AtomicFileDestination -Path $path -Description 'profile transaction target' -StopAt $target
    $existed = Test-Path -LiteralPath $path
    $backupPath = $null
    if ($existed) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw 'Profile transaction target must be a regular file'
        }
        $backupPath = Join-Path $transactionRoot ('snapshot-' + $journal.Count.ToString('D3') + '.bin')
        [IO.File]::Copy($path, $backupPath, $false)
    }
    $journal.Add([pscustomobject]@{Path=$path;Existed=$existed;BackupPath=$backupPath})
}
function Restore-ProfileTransactionSnapshot([object]$snapshot) {
    Assert-AtomicFileDestination -Path $snapshot.Path -Description 'profile transaction target' -StopAt $target
    if ($snapshot.Existed) {
        if (-not (Test-Path -LiteralPath $snapshot.BackupPath -PathType Leaf)) {
            throw ('Profile transaction snapshot is missing: ' + $snapshot.BackupPath)
        }
        $temporary = $snapshot.Path + '.rollback.' + [Guid]::NewGuid().ToString('N') + '.tmp'
        try {
            [IO.File]::Copy($snapshot.BackupPath, $temporary, $false)
            if (Test-Path -LiteralPath $snapshot.Path) {
                if (-not (Test-Path -LiteralPath $snapshot.Path -PathType Leaf)) {
                    throw 'Cannot restore a profile file over a non-file target'
                }
                [IO.File]::Replace($temporary, $snapshot.Path, [NullString]::Value)
            } else {
                [IO.File]::Move($temporary, $snapshot.Path)
            }
            if ((Get-FileDigest $snapshot.Path 'SHA256') -ne (Get-FileDigest $snapshot.BackupPath 'SHA256')) {
                throw 'Restored profile file differs from its transaction snapshot'
            }
        } finally {
            if (Test-Path -LiteralPath $temporary -PathType Leaf) { Remove-Item -LiteralPath $temporary -Force }
        }
    } elseif (Test-Path -LiteralPath $snapshot.Path) {
        if (-not (Test-Path -LiteralPath $snapshot.Path -PathType Leaf)) {
            throw 'Cannot remove a profile transaction output that is not a regular file'
        }
        Remove-Item -LiteralPath $snapshot.Path -Force
    }
}
function Read-ZipJson($archive, [string]$entryName) {
    $entry = $archive.GetEntry($entryName)
    if (-not $entry) { throw ('Missing archive entry: ' + $entryName) }
    $maximumBytes = 256KB
    if ($entry.Length -gt $maximumBytes) { throw ('Archive JSON entry exceeds limit: ' + $entryName) }
    $stream = $entry.Open()
    $buffer = [IO.MemoryStream]::new()
    $chunk = New-Object byte[] 8192
    try {
        while (($read = $stream.Read($chunk, 0, $chunk.Length)) -gt 0) {
            if ($buffer.Length + $read -gt $maximumBytes) { throw ('Archive JSON entry exceeds limit: ' + $entryName) }
            $buffer.Write($chunk, 0, $read)
        }
        $encoding = [Text.UTF8Encoding]::new($false, $true)
        $json = $encoding.GetString($buffer.ToArray())
        return (ConvertFrom-Json -InputObject $json -ErrorAction Stop)
    } finally { $stream.Dispose(); $buffer.Dispose() }
}
function Get-OptionStringArray([string[]]$optionLines, [string]$key) {
    $pattern = '^' + [regex]::Escape($key) + ':(.*)$'
    $matchingLines = @($optionLines | Where-Object { [regex]::IsMatch($_, $pattern) })
    if ($matchingLines.Count -gt 1) { throw ('Minecraft option must occur only once: ' + $key) }
    if ($matchingLines.Count -eq 0) { return ,([string[]]@()) }
    $match = [regex]::Match($matchingLines[0], $pattern)
    $encoded = $match.Groups[1].Value.Trim()
    if ($encoded.Length -lt 2 -or $encoded[0] -ne '[' -or $encoded[$encoded.Length - 1] -ne ']') {
        throw ('Minecraft option must be a JSON string array: ' + $key)
    }
    # Parse inside an object so PowerShell 7 does not unwrap [] to $null or
    # ["one"] to a scalar while assigning the JSON result.
    $wrapper = ConvertFrom-Json -InputObject ('{"values":' + $encoded + '}') -ErrorAction Stop
    $decoded = @($wrapper.values)
    foreach ($value in $decoded) {
        if ($value -isnot [string]) { throw ('Minecraft option contains a non-string value: ' + $key) }
    }
    return ,([string[]]$decoded)
}
function Set-OptionStringArray([System.Collections.Generic.List[string]]$optionLines, [string]$key, [string[]]$values) {
    $serialized = ConvertTo-Json -InputObject ([object[]]$values) -Compress
    $replacement = $key + ':' + $serialized
    $pattern = '^' + [regex]::Escape($key) + ':'
    for ($index = 0; $index -lt $optionLines.Count; $index++) {
        if ($optionLines[$index] -match $pattern) {
            $optionLines[$index] = $replacement
            return
        }
    }
    $optionLines.Add($replacement)
}
function Test-PinnedModrinthUrl([object]$module) {
    if ([string]$module.projectId -notmatch '^[A-Za-z0-9]{8}$' -or
        [string]$module.versionId -notmatch '^[A-Za-z0-9]{8}$') { return $false }
    $uri = $null
    if (-not [Uri]::TryCreate([string]$module.url, [UriKind]::Absolute, [ref]$uri)) { return $false }
    if (-not $uri.Scheme.Equals('https', [StringComparison]::OrdinalIgnoreCase) -or
        -not $uri.Host.Equals('cdn.modrinth.com', [StringComparison]::OrdinalIgnoreCase) -or
        $uri.Port -ne 443 -or $uri.UserInfo.Length -ne 0 -or $uri.Query.Length -ne 0 -or
        $uri.Fragment.Length -ne 0) { return $false }
    $pathPattern = '^/data/' + [regex]::Escape([string]$module.projectId) +
        '/versions/' + [regex]::Escape([string]$module.versionId) + '/[^/]+$'
    return $uri.AbsolutePath -cmatch $pathPattern
}
function Assert-InstalledMigrationProfile([object[]]$modules, [string]$expectedProfileId,
    [string]$clientDestination, [object]$clientArtifactPin, [string]$clientVersion,
    [string]$expectedPackHash, [switch]$dependenciesOnly) {
    Assert-ProfileDirectoryDestination -Path $mods -Description 'installed mods directory' -StopAt $target
    Assert-ProfileDirectoryDestination -Path $packs -Description 'installed resource-pack directory' -StopAt $target
    if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw 'Installed profile is missing its mods directory' }
    if (-not (Test-Path -LiteralPath $packs -PathType Container)) { throw 'Installed profile is missing its resource-pack directory' }

    foreach ($module in $modules) {
        $installedModulePath = Join-Path $mods $module.filename
        Assert-AtomicFileDestination -Path $installedModulePath -Description 'installed migration mod' -StopAt $target
        if (-not (Test-Path -LiteralPath $installedModulePath -PathType Leaf)) {
            throw ('Installed profile is missing locked migration mod: ' + $module.filename)
        }
        $installedModule = Get-Item -LiteralPath $installedModulePath
        if (($installedModule.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
            $installedModule.Length -ne [long]$module.size -or
            (Get-FileDigest $installedModulePath 'SHA512') -cne [string]$module.sha512) {
            throw ('Installed mod differs from its pinned migration artifact: ' + $module.filename)
        }
        if ($module.modId) {
            $installedArchive = [IO.Compression.ZipFile]::OpenRead($installedModulePath)
            try {
                $installedMetadata = Read-ZipJson $installedArchive 'fabric.mod.json'
                $lockedFabricVersion = if ($module.fabricMetadataVersion) {
                    [string]$module.fabricMetadataVersion
                } else { [string]$module.version }
                if ($installedMetadata.id -cne [string]$module.modId -or
                    $installedMetadata.version -cne $lockedFabricVersion) {
                    throw ('Installed mod metadata differs from its lock: ' + $module.filename)
                }
            } finally { $installedArchive.Dispose() }
        }
    }

    if (-not $dependenciesOnly) {
        Assert-AtomicFileDestination -Path $clientDestination -Description 'installed CopiMineClient' -StopAt $target
        if (-not (Test-Path -LiteralPath $clientDestination -PathType Leaf)) {
            throw 'Installed profile is missing CopiMineClient'
        }
        $installedClient = Get-Item -LiteralPath $clientDestination
        if (($installedClient.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
            $installedClient.Length -ne [long]$clientArtifactPin.size -or
            (Get-FileDigest $clientDestination 'SHA512') -cne [string]$clientArtifactPin.sha512) {
            throw 'Installed CopiMineClient differs from its pinned artifact'
        }
    }

    $installedPackPath = Join-Path $packs 'CopiMineResourcePack-26.3.zip'
    Assert-AtomicFileDestination -Path $installedPackPath -Description 'installed migration resource pack' -StopAt $target
    if (-not (Test-Path -LiteralPath $installedPackPath -PathType Leaf) -or
        (Get-FileDigest $installedPackPath 'SHA256') -cne $expectedPackHash) {
        throw 'Installed resource pack differs from its pinned candidate'
    }

    Assert-AtomicFileDestination -Path $optionsPath -Description 'installed Minecraft options' -StopAt $target
    if (-not (Test-Path -LiteralPath $optionsPath -PathType Leaf)) { throw 'Installed profile is missing options.txt' }
    $installedOptions = [regex]::Split([IO.File]::ReadAllText($optionsPath), '\r\n|\n|\r')
    $packOption = 'file/CopiMineResourcePack-26.3.zip'
    $activePacks = @(Get-OptionStringArray $installedOptions 'resourcePacks')
    $incompatiblePacks = @(Get-OptionStringArray $installedOptions 'incompatibleResourcePacks')
    if (@($activePacks | Where-Object { $_ -ceq $packOption }).Count -ne 1) {
        throw 'Migration resource pack is not enabled in options.txt'
    }
    if (@($incompatiblePacks | Where-Object { $_ -ceq $packOption }).Count -ne 0) {
        throw 'Migration resource pack is marked incompatible in options.txt'
    }

    Assert-AtomicFileDestination -Path $migrationReceiptPath -Description 'migration receipt' -StopAt $target
    if (-not (Test-Path -LiteralPath $migrationReceiptPath -PathType Leaf)) {
        throw 'Installed profile is missing its migration receipt'
    }
    $receipt = $null
    try {
        $receipt = Get-Content -LiteralPath $migrationReceiptPath -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw 'Migration receipt does not match the installed profile'
    }
    $receiptMatches = $receipt.profile -ceq $expectedProfileId -and
        $receipt.minecraftVersion -ceq '26.3' -and
        $receipt.resourcePackSha256 -ceq $expectedPackHash -and
        $receipt.nativeVerified -is [bool]
    $receiptMods = @($receipt.mods)
    if ($receiptMods.Count -ne $modules.Count) { $receiptMatches = $false }
    foreach ($module in $modules) {
        $matchingReceiptMods = @($receiptMods | Where-Object { $_.filename -ceq $module.filename })
        $expectedReceiptVersion = $null
        if ($module.PSObject.Properties['version']) { $expectedReceiptVersion = $module.version }
        if ($matchingReceiptMods.Count -ne 1 -or
            $matchingReceiptMods[0].sha512 -cne [string]$module.sha512 -or
            $matchingReceiptMods[0].version -cne $expectedReceiptVersion) {
            $receiptMatches = $false
        }
    }
    if ($dependenciesOnly) {
        if ($receipt.copiMineClientReady -ne $false -or $null -ne $receipt.copiMineClient) { $receiptMatches = $false }
    } else {
        if ($receipt.copiMineClientReady -ne $true -or
            $receipt.copiMineClient.filename -cne [IO.Path]::GetFileName($clientDestination) -or
            $receipt.copiMineClient.sha512 -cne [string]$clientArtifactPin.sha512 -or
            $receipt.copiMineClient.version -cne $clientVersion) { $receiptMatches = $false }
    }
    if (-not $receiptMatches) { throw 'Migration receipt does not match the installed profile' }
}
$clientJar = Join-Path $root 'build/minecraft-26.3/client/libs/CopiMineClient-0.1.1+26.3.jar'
$clientMetadata = $null
$clientMixinsName = $null
$clientSha512 = $null
if (-not $DependenciesOnly -and -not $ValidateDefinitionOnly) {
    Assert-AtomicFileDestination -Path $clientJar -Description 'CopiMineClient build artifact' -StopAt $root
    if (-not (Test-Path -LiteralPath $clientJar -PathType Leaf)) { throw 'Build the 26.3 CopiMineClient jar before installing the profile' }
    $clientArtifactPin = $profile.clientArtifact
    if (-not $clientArtifactPin -or $clientArtifactPin.filename -cne [IO.Path]::GetFileName($clientJar) -or
        $clientArtifactPin.sha512 -notmatch '^[0-9a-f]{128}$' -or
        [long]$clientArtifactPin.size -le 0 -or [long]$clientArtifactPin.size -gt 100000000) {
        throw 'CopiMineClient build provenance lock is missing or invalid'
    }
    if ((Get-Item -LiteralPath $clientJar).Length -ne [long]$clientArtifactPin.size -or
        (Get-FileDigest $clientJar 'SHA512') -ne $clientArtifactPin.sha512) {
        throw 'CopiMineClient jar differs from the pinned build artifact'
    }
    $clientArchive = [IO.Compression.ZipFile]::OpenRead($clientJar)
    try {
        $clientMetadata = Read-ZipJson $clientArchive 'fabric.mod.json'
        if ($clientMetadata.id -ne 'copimineclient' -or $clientMetadata.version -ne '0.1.1+26.3' -or
            $clientMetadata.depends.minecraft -ne '26.3') {
            throw 'CopiMineClient jar metadata does not match the pinned 26.3 client'
        }
        if (@($clientMetadata.entrypoints.client) -cnotcontains 'me.copimine.client.CopiMineClient') {
            throw 'CopiMineClient jar is missing its pinned client entrypoint'
        }
        foreach ($requiredClass in @(
            'me/copimine/client/CopiMineClient.class',
            'me/copimine/client/ClientBridgeProtocol.class',
            'me/copimine/client/BridgePayload.class'
        )) {
            if (-not $clientArchive.GetEntry($requiredClass)) { throw ('Missing required CopiMineClient class: ' + $requiredClass) }
        }
        $clientMixinsName = @($clientMetadata.mixins)[0]
        if (@($clientMetadata.mixins).Count -ne 1 -or $clientMixinsName -cne 'copimineclient-26.3.mixins.json') {
            throw 'CopiMineClient jar does not declare the pinned 26.3 mixin config'
        }
        $mixinConfig = Read-ZipJson $clientArchive $clientMixinsName
        $packagePath = $mixinConfig.package.Replace('.', '/')
        foreach ($mixinClass in $mixinConfig.client) {
            $classEntry = $packagePath + '/' + $mixinClass.Replace('.', '/') + '.class'
            if (-not $clientArchive.GetEntry($classEntry)) { throw ('Missing declared mixin class: ' + $classEntry) }
        }
        $clientSha512 = $clientArtifactPin.sha512
    } finally { $clientArchive.Dispose() }
}
$lockedModuleFilenamesById = @{}
$lockedModuleConflicts = [Collections.Generic.List[IO.FileInfo]]::new()
$lockedModuleConflictIds = @{}
foreach ($module in $lock.modules) {
    if ([IO.Path]::GetFileName($module.filename) -ne $module.filename -or $module.filename -match '[\\/:]') { throw 'Unsafe mod filename' }
    if (($lock.schemaVersion -eq 1 -and [string]::IsNullOrWhiteSpace([string]$module.modId)) -or
        ($module.modId -and [string]$module.modId -notmatch '^[a-z0-9_.-]{1,64}$')) {
        throw ('Client mod lock has a missing or invalid Fabric mod id: ' + $module.filename)
    }
    if ($module.modId) {
        if ($lockedModuleFilenamesById.ContainsKey([string]$module.modId)) {
            throw ('Client mod lock contains the same Fabric mod id more than once: ' + $module.modId)
        }
        $lockedModuleFilenamesById[[string]$module.modId] = [string]$module.filename
    }
    $trustedModrinthSource = Test-PinnedModrinthUrl $module
    $trustedCustomSkinLoaderSource = $module.filename -eq 'CustomSkinLoader_Universal-15.1.jar' -and
        $module.projectId -ceq '286924' -and $module.fileId -ceq '9023814' -and
        $module.url -ceq 'https://edge.forgecdn.net/files/9023/814/CustomSkinLoader_Universal-15.1.jar'
    $trustedArmorHudSource = $module.filename -eq 'mc-armor-hud-8.1.1.6-26.3-fabric.jar' -and
        $module.projectId -ceq '1155218' -and $module.versionId -ceq '9021707' -and
        $module.url -ceq 'https://edge.forgecdn.net/files/9021/707/mc-armor-hud-8.1.1.6-26.3-fabric.jar'
    if ((-not $trustedModrinthSource -and -not $trustedCustomSkinLoaderSource -and -not $trustedArmorHudSource) -or
        $module.sha512 -notmatch '^[0-9a-f]{128}$' -or
        [long]$module.size -le 0 -or [long]$module.size -gt 100000000) { throw 'Mod source, digest, or size is invalid' }
}
if ($ValidateDefinitionOnly) {
    Write-Output 'Pinned Minecraft 26.3 profile definition and external mod sources validated without mutation'
    return
}
$pack = Join-Path $root 'build/minecraft-26.3/CopiMineResourcePack-26.3.zip'
if (-not (Test-Path -LiteralPath $pack -PathType Leaf)) { throw 'Build the 26.3 resource pack before installing' }
$migration = $profile.resourcePackMigration
if (-not $migration -or $migration.legacyPackSha256 -notmatch '^[0-9a-f]{64}$' -or
    $migration.candidateSha256 -notmatch '^[0-9a-f]{64}$') {
    throw 'The 26.3 resource-pack provenance lock is missing or invalid'
}
Assert-AtomicFileDestination -Path $pack -Description 'migration resource pack source' -StopAt $root
$packHash = Get-FileDigest $pack 'SHA256'
if ($packHash -ne $migration.candidateSha256) { throw 'Resource-pack SHA-256 differs from the pinned 26.3 candidate' }
$archive = [IO.Compression.ZipFile]::OpenRead($pack)
try {
    $metadata = Read-ZipJson $archive 'pack.mcmeta'
    if (($metadata.pack.min_format -join '.') -ne '97.1' -or ($metadata.pack.max_format -join '.') -ne '97.1') {
        throw 'Resource pack targets a different Minecraft format'
    }
    $receipt = Read-ZipJson $archive 'assets/copimine/manifests/minecraft_26_3_migration.json'
    if ($receipt.minecraftVersion -ne $profile.minecraftVersion -or
        $receipt.legacyPackSha256 -ne $migration.legacyPackSha256 -or
        $receipt.vanillaClientSha1 -ne $profile.minecraftClient.sha1 -or
        ($receipt.resourceFormat -join '.') -ne '97.1') {
        throw 'Resource-pack migration receipt does not match the pinned source and client'
    }
} finally { $archive.Dispose() }
$clientDestination = Join-Path $mods ([IO.Path]::GetFileName($clientJar))
if (Test-Path -LiteralPath $mods -PathType Container) {
    foreach ($existingJar in Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File) {
        $existingArchive = $null
        try {
            $existingArchive = [IO.Compression.ZipFile]::OpenRead($existingJar.FullName)
            if ($existingArchive.GetEntry('fabric.mod.json')) {
                $existingMetadata = $null
                try {
                    $existingMetadata = Read-ZipJson $existingArchive 'fabric.mod.json'
                } catch {
                    # Malformed third-party metadata cannot identify a locked mod conflict.
                }
                if ($existingMetadata) {
                    $existingModId = [string]$existingMetadata.id
                    if (-not $DependenciesOnly -and $existingModId -eq 'copimineclient' -and
                        $existingJar.FullName -ne $clientDestination) {
                        throw ('Another CopiMineClient mod is already present: ' + $existingJar.Name)
                    }
                    if ($lockedModuleFilenamesById.ContainsKey($existingModId)) {
                        $lockedFilename = [string]$lockedModuleFilenamesById[$existingModId]
                        if ($existingJar.Name -cne $lockedFilename) {
                            if ($ValidateOnly) {
                                throw ("Existing Fabric mod '" + $existingJar.Name + "' (id '" + $existingModId +
                                    "') conflicts with locked migration mod '" + $lockedFilename +
                                    "'. Run the installer to preserve a backup and replace it with the pinned JAR.")
                            }
                            if ($existingJar.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                                throw ('Existing conflicting mod is a link and cannot be backed up safely: ' + $existingJar.Name)
                            }
                            if ($lockedModuleConflictIds.ContainsKey($existingModId)) {
                                throw ('Multiple existing JARs conflict with locked migration mod id: ' + $existingModId)
                            }
                            $lockedModuleConflictIds[$existingModId] = $existingJar.Name
                            $lockedModuleConflicts.Add($existingJar)
                        } else {
                            $lockedModule = $lock.modules | Where-Object { $_.modId -ceq $existingModId } | Select-Object -First 1
                            if ((Get-FileDigest $existingJar.FullName 'SHA512') -ne $lockedModule.sha512) {
                                throw ('Existing mod differs from its pinned migration artifact: ' + $existingJar.Name)
                            }
                        }
                    }
                    $lockedModuleForFilename = $lock.modules | Where-Object { $_.filename -ceq $existingJar.Name } | Select-Object -First 1
                    if ($lockedModuleForFilename -and $lockedModuleForFilename.modId -and
                        $existingModId -cne $lockedModuleForFilename.modId) {
                        throw ('Existing mod does not contain its pinned Fabric mod id: ' + $existingJar.Name)
                    }
                }
            }
        } catch [System.IO.InvalidDataException] {
            # Non-ZIP files are not Fabric mods and cannot duplicate a locked mod id.
        } finally { if ($existingArchive) { $existingArchive.Dispose() } }
    }
}
if ($ValidateOnly) {
    Assert-ProfileDirectoryDestination -Path $mods -Description 'installed mods directory' -StopAt $target
    Assert-ProfileDirectoryDestination -Path $packs -Description 'installed resource-pack directory' -StopAt $target
    Assert-AtomicFileDestination -Path $optionsPath -Description 'installed Minecraft options' -StopAt $target
    Assert-AtomicFileDestination -Path $migrationReceiptPath -Description 'migration receipt' -StopAt $target
    Assert-InstalledMigrationProfile -Modules @($lock.modules) -ExpectedProfileId $id `
        -ClientDestination $clientDestination -ClientArtifactPin $profile.clientArtifact `
        -ClientVersion ([string]$clientMetadata.version) -ExpectedPackHash $packHash -DependenciesOnly:$DependenciesOnly
    if (-not $DependenciesOnly) { Write-Output ('Validated CopiMineClient ' + $clientMetadata.version + ' and ' + $clientMixinsName) }
    Write-Output 'Installed 26.3 Fabric profile validated without mutation'
    return
}
Assert-AtomicFileDestination -Path $migrationReceiptPath -Description 'migration receipt' -StopAt $target
Assert-AtomicFileDestination -Path $optionsPath -Description 'text file' -StopAt $target
Assert-ProfileDirectoryDestination -Path $mods -Description 'profile directory' -StopAt $target
Assert-ProfileDirectoryDestination -Path $packs -Description 'profile directory' -StopAt $target
Assert-ProfileDirectoryDestination -Path $backupRoot -Description 'profile directory' -StopAt $target
Assert-ProfileDirectoryDestination -Path $backupClientModsRoot -Description 'profile directory' -StopAt $target
Assert-ProfileDirectoryDestination -Path $backupOptionsRoot -Description 'profile directory' -StopAt $target
if (-not $DependenciesOnly) {
    Assert-AtomicFileDestination -Path $clientDestination -Description 'profile artifact' -StopAt $target
}
foreach ($module in $lock.modules) {
    Assert-AtomicFileDestination -Path (Join-Path $mods $module.filename) -Description 'profile artifact' -StopAt $target
}
$packTarget = Join-Path $packs 'CopiMineResourcePack-26.3.zip'
Assert-AtomicFileDestination -Path $packTarget -Description 'profile artifact' -StopAt $target
$optionsExisted = Test-Path -LiteralPath $optionsPath -PathType Leaf
$originalOptions = if ($optionsExisted) { [IO.File]::ReadAllText($optionsPath) } else { '' }
$optionBytes = if ($optionsExisted) { [IO.File]::ReadAllBytes($optionsPath) } else { [byte[]]@() }
$hasUtf8Bom = $optionBytes.Length -ge 3 -and $optionBytes[0] -eq 0xEF -and
    $optionBytes[1] -eq 0xBB -and $optionBytes[2] -eq 0xBF
$lineEnding = if ($originalOptions.Contains("`r`n")) { "`r`n" } elseif ($originalOptions.Contains("`r")) { "`r" } else { "`n" }
$hasTrailingNewline = -not $optionsExisted -or $originalOptions -match '(?:\r\n|\n|\r)$'
$optionLines = [System.Collections.Generic.List[string]]::new()
if ($originalOptions.Length -gt 0) {
    $splitOptions = [regex]::Split($originalOptions, '\r\n|\n|\r')
    $lineCount = $splitOptions.Length
    if ($hasTrailingNewline) { $lineCount-- }
    for ($index = 0; $index -lt $lineCount; $index++) { $optionLines.Add($splitOptions[$index]) }
}
$packOption = 'file/CopiMineResourcePack-26.3.zip'
$activePacks = [System.Collections.Generic.List[string]]::new()
foreach ($value in (Get-OptionStringArray $optionLines.ToArray() 'resourcePacks')) {
    if ($value -ne $packOption) { $activePacks.Add($value) }
}
$vanillaIndex = $activePacks.IndexOf('vanilla')
if ($vanillaIndex -lt 0) {
    $activePacks.Insert(0, 'vanilla')
    $vanillaIndex = 0
}
$activePacks.Insert($vanillaIndex + 1, $packOption)
$incompatiblePacks = [System.Collections.Generic.List[string]]::new()
foreach ($value in (Get-OptionStringArray $optionLines.ToArray() 'incompatibleResourcePacks')) {
    if ($value -ne $packOption) { $incompatiblePacks.Add($value) }
}
Set-OptionStringArray $optionLines 'resourcePacks' $activePacks.ToArray()
Set-OptionStringArray $optionLines 'incompatibleResourcePacks' $incompatiblePacks.ToArray()
$updatedOptions = [string]::Join($lineEnding, $optionLines.ToArray())
if ($hasTrailingNewline -and $optionLines.Count -gt 0) { $updatedOptions += $lineEnding }
$stage = Join-Path $root 'build/minecraft-26.3/client-mods'
Assert-ProfileDirectoryDestination -Path $stage -Description 'migration stage directory' -StopAt $root
New-Item -ItemType Directory -Path $stage -Force | Out-Null
foreach ($module in $lock.modules) {
    $path = Join-Path $stage $module.filename
    Assert-AtomicFileDestination -Path $path -Description 'migration mod cache' -StopAt $root
    $validCache = $false
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        if ((Get-Item -LiteralPath $path).Length -eq [long]$module.size) {
            $validCache = (Get-FileDigest $path 'SHA512') -eq $module.sha512
        }
    }
    if (-not $validCache) {
        Save-PinnedArtifact -Uri $module.url -Destination $path -Algorithm SHA512 `
            -ExpectedHash $module.sha512 -ExpectedSize ([long]$module.size) -MaximumBytes ([long]$module.size) -StopAt $root
    }
    if ($module.modId) {
        $modArchive = [IO.Compression.ZipFile]::OpenRead($path)
        try {
            $modMetadata = Read-ZipJson $modArchive 'fabric.mod.json'
            $lockedFabricVersion = if ($module.fabricMetadataVersion) {
                [string]$module.fabricMetadataVersion
            } else {
                [string]$module.version
            }
            if ($modMetadata.id -cne $module.modId -or $modMetadata.version -cne $lockedFabricVersion) {
                throw ('Pinned mod metadata differs from its lock id or version: ' + $module.filename)
            }
        } finally { $modArchive.Dispose() }
    }
}
foreach ($module in $lock.modules) {
    $destination = Join-Path $mods $module.filename
    if ((Test-Path -LiteralPath $destination -PathType Leaf) -and
        (Get-FileDigest $destination 'SHA512') -ne $module.sha512) {
        throw ('Refusing to replace a different existing mod: ' + $module.filename)
    }
}
$installed = @($lock.modules | ForEach-Object { @{filename=$_.filename;sha512=$_.sha512;version=$_.version} })
$clientReceipt = $null
if (-not $DependenciesOnly) {
    $clientReceipt = @{filename=[IO.Path]::GetFileName($clientJar);sha512=$clientSha512;version=$clientMetadata.version}
}
$receiptContent = @{profile=$id;minecraftVersion='26.3';mods=$installed;copimineClient=$clientReceipt;resourcePackSha256=$packHash;copimineClientReady=([bool]$clientReceipt);nativeVerified=$false} |
    ConvertTo-Json -Depth 6
$modsExisted = Test-Path -LiteralPath $mods -PathType Container
$packsExisted = Test-Path -LiteralPath $packs -PathType Container
$backupRootExisted = Test-Path -LiteralPath $backupRoot -PathType Container
$transactionRoot = Join-Path $target ('.copimine-migration-transaction-' + [Guid]::NewGuid().ToString('N'))
$journal = [Collections.Generic.List[object]]::new()
$createdBackupFiles = [Collections.Generic.List[string]]::new()
$createdBackupDirectories = [Collections.Generic.List[string]]::new()
try {
    Assert-ProfileDirectoryDestination -Path $transactionRoot -Description 'profile transaction directory' -StopAt $target
    New-Item -ItemType Directory -Path $transactionRoot -ErrorAction Stop | Out-Null
    foreach ($module in $lock.modules) {
        Add-ProfileTransactionSnapshot $journal (Join-Path $mods $module.filename) $transactionRoot
    }
    foreach ($conflict in $lockedModuleConflicts) {
        Add-ProfileTransactionSnapshot $journal $conflict.FullName $transactionRoot
    }
    if (-not $DependenciesOnly) { Add-ProfileTransactionSnapshot $journal $clientDestination $transactionRoot }
    Add-ProfileTransactionSnapshot $journal $packTarget $transactionRoot
    if (-not $optionsExisted -or $updatedOptions -cne $originalOptions) {
        Add-ProfileTransactionSnapshot $journal $optionsPath $transactionRoot
    }
    Add-ProfileTransactionSnapshot $journal $migrationReceiptPath $transactionRoot

    New-Item -ItemType Directory -Path $mods,$packs -Force -ErrorAction Stop | Out-Null
    if ($lockedModuleConflicts.Count -gt 0) {
        Assert-ProfileDirectoryDestination -Path $backupClientModsRoot -Description 'profile directory' -StopAt $target
        if (-not (Test-Path -LiteralPath $backupRoot -PathType Container)) {
            New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
            $createdBackupDirectories.Add($backupRoot)
        }
        if (-not (Test-Path -LiteralPath $backupClientModsRoot -PathType Container)) {
            New-Item -ItemType Directory -Path $backupClientModsRoot -Force | Out-Null
            $createdBackupDirectories.Add($backupClientModsRoot)
        }
        $modBackupDirectory = Join-Path $backupClientModsRoot ((Get-Date -Format 'yyyyMMddHHmmssfff') + '-' + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $modBackupDirectory -ErrorAction Stop | Out-Null
        $createdBackupDirectories.Add($modBackupDirectory)
        foreach ($conflict in $lockedModuleConflicts) {
            $conflictBackup = Join-Path $modBackupDirectory $conflict.Name
            Assert-AtomicFileDestination -Path $conflictBackup -Description 'client mod backup' -StopAt $target
            [IO.File]::Copy($conflict.FullName, $conflictBackup, $false)
            $createdBackupFiles.Add($conflictBackup)
            Remove-Item -LiteralPath $conflict.FullName -Force -ErrorAction Stop
        }
    }
    foreach ($module in $lock.modules) {
        $destination = Join-Path $mods $module.filename
        if ((Test-Path -LiteralPath $destination -PathType Leaf) -and
            (Get-FileDigest $destination 'SHA512') -ne $module.sha512) {
            throw ('Refusing to replace a different existing mod: ' + $module.filename)
        }
        Copy-VerifiedFileAtomically (Join-Path $stage $module.filename) $destination 'SHA512' $module.sha512
    }
    if (-not $DependenciesOnly) {
        if (Test-Path -LiteralPath $clientDestination -PathType Leaf) {
            $existingClientHash = Get-FileDigest $clientDestination 'SHA512'
            if ($existingClientHash -ne $clientSha512) {
                Assert-ProfileDirectoryDestination -Path $backupClientModsRoot -Description 'profile directory' -StopAt $target
                if (-not (Test-Path -LiteralPath $backupRoot -PathType Container)) {
                    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
                    $createdBackupDirectories.Add($backupRoot)
                }
                if (-not (Test-Path -LiteralPath $backupClientModsRoot -PathType Container)) {
                    New-Item -ItemType Directory -Path $backupClientModsRoot -Force | Out-Null
                    $createdBackupDirectories.Add($backupClientModsRoot)
                }
                $backupDir = Join-Path $backupClientModsRoot ((Get-Date -Format 'yyyyMMddHHmmssfff') + '-' + [Guid]::NewGuid().ToString('N'))
                New-Item -ItemType Directory -Path $backupDir -ErrorAction Stop | Out-Null
                $createdBackupDirectories.Add($backupDir)
                $clientBackup = Join-Path $backupDir ([IO.Path]::GetFileName($clientDestination))
                Copy-Item -LiteralPath $clientDestination -Destination $clientBackup
                $createdBackupFiles.Add($clientBackup)
            }
        }
        Copy-VerifiedFileAtomically $clientJar $clientDestination 'SHA512' $clientSha512
    }
    if (Test-Path -LiteralPath $packTarget -PathType Leaf) {
        $existingPackHash = Get-FileDigest $packTarget 'SHA256'
        if ($existingPackHash -ne $packHash) {
            $packBackup = $packTarget + '.backup-' + [Guid]::NewGuid().ToString('N')
            [IO.File]::Copy($packTarget, $packBackup, $false)
            $createdBackupFiles.Add($packBackup)
            Copy-VerifiedFileAtomically $pack $packTarget 'SHA256' $packHash
        }
    } else { Copy-VerifiedFileAtomically $pack $packTarget 'SHA256' $packHash }
    if ((Get-FileDigest $packTarget 'SHA256') -ne $packHash) { throw 'Installed resource pack verification failed' }
    if (-not $optionsExisted -or $updatedOptions -cne $originalOptions) {
        if ($optionsExisted) {
            Assert-ProfileDirectoryDestination -Path $backupOptionsRoot -Description 'profile directory' -StopAt $target
            if (-not (Test-Path -LiteralPath $backupRoot -PathType Container)) {
                New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
                $createdBackupDirectories.Add($backupRoot)
            }
            if (-not (Test-Path -LiteralPath $backupOptionsRoot -PathType Container)) {
                New-Item -ItemType Directory -Path $backupOptionsRoot -Force | Out-Null
                $createdBackupDirectories.Add($backupOptionsRoot)
            }
            $backupDir = Join-Path $backupOptionsRoot ((Get-Date -Format 'yyyyMMddHHmmssfff') + '-' + [Guid]::NewGuid().ToString('N'))
            New-Item -ItemType Directory -Path $backupDir -ErrorAction Stop | Out-Null
            $createdBackupDirectories.Add($backupDir)
            $optionsBackup = Join-Path $backupDir 'options.txt'
            Copy-Item -LiteralPath $optionsPath -Destination $optionsBackup
            $createdBackupFiles.Add($optionsBackup)
        }
        Save-TextFileAtomically -Path $optionsPath -Content $updatedOptions `
            -Encoding ([Text.UTF8Encoding]::new($hasUtf8Bom)) -StopAt $target
    }
    Save-TextFileAtomically -Path $migrationReceiptPath -Content $receiptContent `
        -Encoding ([Text.UTF8Encoding]::new($true)) -StopAt $target
} catch {
    $originalError = $_.Exception
    $rollbackErrors = [Collections.Generic.List[string]]::new()
    for ($index = $journal.Count - 1; $index -ge 0; $index--) {
        try { Restore-ProfileTransactionSnapshot $journal[$index] }
        catch { $rollbackErrors.Add($_.Exception.Message) }
    }
    if ($rollbackErrors.Count -eq 0) {
        foreach ($path in $createdBackupFiles) {
            if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue }
        }
        for ($index = $createdBackupDirectories.Count - 1; $index -ge 0; $index--) {
            $path = $createdBackupDirectories[$index]
            if ((Test-Path -LiteralPath $path -PathType Container) -and
                @(Get-ChildItem -LiteralPath $path -Force -ErrorAction SilentlyContinue).Count -eq 0) {
                Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
            }
        }
        if (Test-Path -LiteralPath $transactionRoot -PathType Container) {
            foreach ($item in Get-ChildItem -LiteralPath $transactionRoot -Force -ErrorAction SilentlyContinue) {
                if (-not $item.PSIsContainer) { Remove-Item -LiteralPath $item.FullName -Force -ErrorAction SilentlyContinue }
            }
            if (@(Get-ChildItem -LiteralPath $transactionRoot -Force -ErrorAction SilentlyContinue).Count -eq 0) {
                Remove-Item -LiteralPath $transactionRoot -Force -ErrorAction SilentlyContinue
            }
        }
        foreach ($path in @($mods,$packs)) {
            $wasPresent = if ($path -eq $mods) { $modsExisted } else { $packsExisted }
            if (-not $wasPresent -and (Test-Path -LiteralPath $path -PathType Container) -and
                @(Get-ChildItem -LiteralPath $path -Force -ErrorAction SilentlyContinue).Count -eq 0) {
                Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
            }
        }
        if (-not $backupRootExisted -and (Test-Path -LiteralPath $backupRoot -PathType Container) -and
            @(Get-ChildItem -LiteralPath $backupRoot -Force -ErrorAction SilentlyContinue).Count -eq 0) {
            Remove-Item -LiteralPath $backupRoot -Force -ErrorAction SilentlyContinue
        }
    } else {
        throw ('Migration failed and profile rollback is incomplete. Recovery snapshots remain at ' +
            $transactionRoot + '. Original migration error: ' + $originalError.Message +
            '. Rollback errors: ' + ($rollbackErrors -join '; '))
    }
    throw $originalError
}
if (Test-Path -LiteralPath $transactionRoot -PathType Container) {
    foreach ($item in Get-ChildItem -LiteralPath $transactionRoot -Force -ErrorAction SilentlyContinue) {
        if (-not $item.PSIsContainer) { Remove-Item -LiteralPath $item.FullName -Force -ErrorAction SilentlyContinue }
    }
    if (@(Get-ChildItem -LiteralPath $transactionRoot -Force -ErrorAction SilentlyContinue).Count -eq 0) {
        Remove-Item -LiteralPath $transactionRoot -Force -ErrorAction SilentlyContinue
    }
}
if ($DependenciesOnly) {
    Write-Output ('Installed and verified ' + $installed.Count + ' external 26.3 mods and the resource pack in ' + $id)
    Write-Output 'CopiMineClient was not installed; the profile is not ready for wave testing'
} else {
    Write-Output ('Installed and verified ' + $installed.Count + ' external 26.3 mods, CopiMineClient, and the resource pack in ' + $id)
}
