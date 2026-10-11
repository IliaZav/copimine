function Resolve-MigrationOutputPath {
    param(
        [Parameter(Mandatory = $true)][string]$WorkspaceRoot,
        [Parameter(Mandatory = $true)][string]$RelativePath
    )

    if ([IO.Path]::IsPathRooted($RelativePath)) {
        throw "Migration output path must be relative to the workspace: $RelativePath"
    }
    $root = [IO.Path]::GetFullPath($WorkspaceRoot).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $resolved = [IO.Path]::GetFullPath((Join-Path $root $RelativePath))
    if (-not $resolved.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Migration output path escapes the workspace: $RelativePath"
    }
    Assert-MigrationOutputPathNoReparse -Path $resolved -BoundaryRoot $root.TrimEnd([IO.Path]::DirectorySeparatorChar)
    return $resolved
}

function Resolve-MigrationOutputTreePath {
    param(
        [Parameter(Mandatory = $true)][string]$RootPath,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$RelativePath
    )

    if ([IO.Path]::IsPathRooted($RelativePath)) {
        throw "Migration output tree path must be relative: $RelativePath"
    }
    $root = [IO.Path]::GetFullPath($RootPath).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ([string]::IsNullOrEmpty($RelativePath)) { return $root }
    $prefix = $root + [IO.Path]::DirectorySeparatorChar
    $resolved = [IO.Path]::GetFullPath((Join-Path $root $RelativePath))
    if (-not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Snapshot entry path escapes the migration output directory: $RelativePath"
    }
    Assert-MigrationOutputPathNoReparse -Path $resolved -BoundaryRoot $root
    return $resolved
}

function Resolve-MigrationSnapshotPayloadPath {
    param(
        [Parameter(Mandatory = $true)][string]$EntryRoot,
        [Parameter(Mandatory = $true)][string]$PayloadName
    )

    if ($PayloadName -notmatch '^p[0-9]{8}$') {
        throw "Migration output snapshot payload name is invalid: $PayloadName"
    }
    return Resolve-MigrationOutputTreePath -RootPath $EntryRoot -RelativePath $PayloadName
}

function ConvertTo-MigrationExtendedPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = [IO.Path]::GetFullPath($Path)
    if ($fullPath.StartsWith('\\?\', [StringComparison]::Ordinal)) { return $fullPath }
    if ($fullPath.StartsWith('\\', [StringComparison]::Ordinal)) {
        return '\\?\UNC\' + $fullPath.TrimStart('\')
    }
    return '\\?\' + $fullPath
}

function Assert-MigrationOutputPayloadIntegrity {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )

    if ($ExpectedSha256 -notmatch '^[0-9a-f]{64}$') {
        throw "Migration output snapshot payload SHA-256 is invalid: $Path"
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (ConvertTo-MigrationExtendedPath -Path $Path) -ErrorAction Stop).Hash
    if (-not [string]::Equals($actualHash, $ExpectedSha256, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Migration output snapshot payload SHA-256 does not match: $Path"
    }
}

function Copy-MigrationOutputPayloadVerified {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$DestinationPath,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )

    if ($ExpectedSha256 -notmatch '^[0-9a-f]{64}$') {
        throw "Migration output snapshot payload SHA-256 is invalid: $SourcePath"
    }
    $sourceStream = $null
    $destinationStream = $null
    $sha256 = [Security.Cryptography.SHA256]::Create()
    $stagedPath = $DestinationPath + '.restore-' + [guid]::NewGuid().ToString('N') + '.tmp'
    $backupPath = $null
    $destinationInstalled = $false
    $installedVerified = $false
    try {
        $sourceStream = [IO.File]::Open((ConvertTo-MigrationExtendedPath -Path $SourcePath),
            [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        $destinationStream = [IO.File]::Open((ConvertTo-MigrationExtendedPath -Path $stagedPath),
            [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read -bor [IO.FileShare]::Delete)
        $buffer = [byte[]]::new(81920)
        $hashBuffer = [byte[]]::new(81920)
        while (($bytesRead = $sourceStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $null = $sha256.TransformBlock($buffer, 0, $bytesRead, $hashBuffer, 0)
            $destinationStream.Write($buffer, 0, $bytesRead)
        }
        $null = $sha256.TransformFinalBlock([byte[]]@(), 0, 0)
        $actualHash = [BitConverter]::ToString($sha256.Hash).Replace('-', '').ToLowerInvariant()
        if (-not [string]::Equals($actualHash, $ExpectedSha256, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Migration output snapshot payload SHA-256 does not match: $SourcePath"
        }
        $destinationStream.Flush($true)
        if ([IO.Directory]::Exists((ConvertTo-MigrationExtendedPath -Path $DestinationPath))) {
            throw "Migration output restore destination changed to a directory: $DestinationPath"
        }
        if ([IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $DestinationPath))) {
            $backupPath = $DestinationPath + '.restore-backup-' + [guid]::NewGuid().ToString('N') + '.tmp'
            [IO.File]::Move((ConvertTo-MigrationExtendedPath -Path $DestinationPath), (ConvertTo-MigrationExtendedPath -Path $backupPath))
        }
        try {
            [IO.File]::Move((ConvertTo-MigrationExtendedPath -Path $stagedPath), (ConvertTo-MigrationExtendedPath -Path $DestinationPath))
            $destinationInstalled = $true
            $destinationStream.Dispose()
            $destinationStream = $null
            $destinationParent = Split-Path -Parent $DestinationPath
            Assert-MigrationOutputPathNoReparse -Path $DestinationPath -BoundaryRoot $destinationParent
            Assert-MigrationOutputPayloadIntegrity -Path $DestinationPath -ExpectedSha256 $ExpectedSha256
            $installedVerified = $true
        } catch {
            $installFailure = $_.Exception
            $rollbackFailure = $null
            if ($destinationInstalled) {
                try { [IO.File]::Delete((ConvertTo-MigrationExtendedPath -Path $DestinationPath)) }
                catch { $rollbackFailure = $_.Exception }
                if (-not [IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $DestinationPath))) {
                    $destinationInstalled = $false
                } elseif (-not $rollbackFailure) {
                    $rollbackFailure = [IO.IOException]::new("Could not remove the unverified restore destination: $DestinationPath")
                }
            }
            if ($backupPath -and [IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $backupPath)) -and
                -not [IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $DestinationPath))) {
                try { [IO.File]::Move((ConvertTo-MigrationExtendedPath -Path $backupPath), (ConvertTo-MigrationExtendedPath -Path $DestinationPath)) }
                catch { $rollbackFailure = $_.Exception }
            }
            if ($rollbackFailure) {
                throw [AggregateException]::new("Verified output installation failed and rollback also failed. Preserve recovery data; destination='$DestinationPath', backup='$backupPath'.", `
                    [Exception[]]@($installFailure, $rollbackFailure))
            }
            throw $installFailure
        }
        if ($backupPath -and [IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $backupPath))) {
            $backupIoPath = ConvertTo-MigrationExtendedPath -Path $backupPath
            [IO.File]::SetAttributes($backupIoPath, [IO.FileAttributes]::Normal)
            [IO.File]::Delete($backupIoPath)
        }
    } catch {
        $copyFailure = $_.Exception
        if ($destinationStream) {
            try { $destinationStream.Dispose() } catch { }
            finally { $destinationStream = $null }
        }
        if ($destinationInstalled -and -not $installedVerified) {
            try { [IO.File]::Delete((ConvertTo-MigrationExtendedPath -Path $DestinationPath)) } catch { }
        }
        if ($backupPath -and [IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $backupPath)) -and
            -not [IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $DestinationPath))) {
            try { [IO.File]::Move((ConvertTo-MigrationExtendedPath -Path $backupPath), (ConvertTo-MigrationExtendedPath -Path $DestinationPath)) } catch { }
        }
        try {
            if ([IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $stagedPath))) {
                [IO.File]::Delete((ConvertTo-MigrationExtendedPath -Path $stagedPath))
            }
        } catch { }
        throw $copyFailure
    } finally {
        if ($destinationStream) { try { $destinationStream.Dispose() } catch { } }
        if ($sourceStream) { try { $sourceStream.Dispose() } catch { } }
        try { $sha256.Dispose() } catch { }
    }
}

function Assert-MigrationOutputPathNoReparse {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$BoundaryRoot
    )

    $current = [IO.Path]::GetFullPath($Path)
    $boundary = if ([string]::IsNullOrEmpty($BoundaryRoot)) { $null } else { [IO.Path]::GetFullPath($BoundaryRoot) }
    while ($current) {
        $item = Get-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $current) -Force -ErrorAction SilentlyContinue
        if ($item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Migration output snapshot does not accept symbolic links or reparse points: $current"
        }
        if ($boundary -and [string]::Equals($current.TrimEnd([IO.Path]::DirectorySeparatorChar),
                $boundary.TrimEnd([IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) { break }
        $parent = [IO.Directory]::GetParent($current)
        if (-not $parent) { break }
        $current = $parent.FullName
    }
}

function Assert-MigrationSnapshotDirectoryNoReparse {
    param([Parameter(Mandatory = $true)][string]$Path)

    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        $item = Get-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $current) -Force -ErrorAction SilentlyContinue
        if ($item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Migration output snapshot directory cannot pass through a reparse point: $current"
        }
        $parent = [IO.Directory]::GetParent($current)
        if (-not $parent) { break }
        $current = $parent.FullName
    }
}

function Get-MigrationOutputTreeEntries {
    param([Parameter(Mandatory = $true)][string]$RootPath)

    $entries = [Collections.Generic.List[object]]::new()
    $rootItem = Get-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $RootPath) -Force -ErrorAction Stop
    if ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Migration output snapshot does not accept symbolic links or reparse points: $RootPath"
    }
    $entries.Add([pscustomobject]@{
        RelativePath = ''
        IsDirectory = $true
        Attributes = [int]$rootItem.Attributes
        CreationTimeUtc = $rootItem.CreationTimeUtc
        LastWriteTimeUtc = $rootItem.LastWriteTimeUtc
        PayloadName = $null
        PayloadSha256 = $null
    })

    $pending = [Collections.Generic.Queue[object]]::new()
    $pending.Enqueue([pscustomobject]@{ FullPath = $RootPath; RelativePath = '' })
    while ($pending.Count -gt 0) {
        $directory = $pending.Dequeue()
        foreach ($child in Get-ChildItem -LiteralPath (ConvertTo-MigrationExtendedPath -Path $directory.FullPath) -Force -ErrorAction Stop) {
            if ($child.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Migration output snapshot does not accept symbolic links or reparse points: $($child.Name)"
            }
            $relativePath = if ([string]::IsNullOrEmpty($directory.RelativePath)) { $child.Name } else { Join-Path $directory.RelativePath $child.Name }
            $childPath = Join-Path $directory.FullPath $child.Name
            $isDirectory = [bool]($child.Attributes -band [IO.FileAttributes]::Directory)
            $entries.Add([pscustomobject]@{
                RelativePath = $relativePath
                IsDirectory = $isDirectory
                Attributes = [int]$child.Attributes
                CreationTimeUtc = $child.CreationTimeUtc
                LastWriteTimeUtc = $child.LastWriteTimeUtc
                PayloadName = $null
                PayloadSha256 = $null
            })
            if ($isDirectory) { $pending.Enqueue([pscustomobject]@{ FullPath = $childPath; RelativePath = $relativePath }) }
        }
    }
    return $entries.ToArray()
}

function New-MigrationOutputSnapshot {
    param(
        [Parameter(Mandatory = $true)][string]$WorkspaceRoot,
        [Parameter(Mandatory = $true)][string]$SnapshotDirectory,
        [Parameter(Mandatory = $true)][string[]]$RelativePaths
    )

    $root = (Resolve-Path -LiteralPath $WorkspaceRoot -ErrorAction Stop).Path
    $snapshotRoot = [IO.Path]::GetFullPath($SnapshotDirectory)
    $workspacePath = [IO.Path]::GetFullPath($root).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $workspacePrefix = $workspacePath + [IO.Path]::DirectorySeparatorChar
    if ([string]::Equals($snapshotRoot.TrimEnd([IO.Path]::DirectorySeparatorChar), $workspacePath, [StringComparison]::OrdinalIgnoreCase) -or
        $snapshotRoot.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Migration output snapshot data must be stored outside the workspace.'
    }
    Assert-MigrationSnapshotDirectoryNoReparse -Path $snapshotRoot
    New-Item -ItemType Directory -Path $snapshotRoot -Force -ErrorAction Stop | Out-Null
    $snapshotRoot = (Resolve-Path -LiteralPath $snapshotRoot -ErrorAction Stop).Path

    $items = [Collections.Generic.List[object]]::new()
    $index = 0
    foreach ($relativePath in $RelativePaths) {
        $target = Resolve-MigrationOutputPath -WorkspaceRoot $root -RelativePath $relativePath
        foreach ($prior in $items) {
            $separator = [IO.Path]::DirectorySeparatorChar
            if ([string]::Equals($prior.TargetPath, $target, [StringComparison]::OrdinalIgnoreCase) -or
                $target.StartsWith($prior.TargetPath.TrimEnd($separator) + $separator, [StringComparison]::OrdinalIgnoreCase) -or
                $prior.TargetPath.StartsWith($target.TrimEnd($separator) + $separator, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Migration output snapshot contains overlapping paths: $($prior.RelativePath) and $relativePath"
            }
        }
        $exists = Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target)
        $entryRoot = Join-Path $snapshotRoot ('{0:D4}' -f $index)
        $item = [pscustomobject]@{
            RelativePath = $relativePath
            TargetPath = $target
            Existed = $exists
            IsDirectory = $false
            Entries = @()
            PayloadPath = $null
        }
        if ($exists) {
            $source = Get-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target) -Force -ErrorAction Stop
            if ($source.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Migration output snapshot does not accept symbolic links or reparse points: $target"
            }
            $item.IsDirectory = [bool]($source.Attributes -band [IO.FileAttributes]::Directory)
            if ($item.IsDirectory) {
                $item.Entries = @(Get-MigrationOutputTreeEntries -RootPath $target)
                New-Item -ItemType Directory -Path $entryRoot -Force | Out-Null
                $payloadIndex = 0
                foreach ($treeEntry in $item.Entries | Where-Object { -not $_.IsDirectory }) {
                    $sourcePath = Join-Path $target $treeEntry.RelativePath
                    $treeEntry.PayloadName = 'p{0:D8}' -f $payloadIndex
                    $payloadPath = Resolve-MigrationSnapshotPayloadPath -EntryRoot $entryRoot -PayloadName $treeEntry.PayloadName
                    [IO.File]::Copy((ConvertTo-MigrationExtendedPath -Path $sourcePath), (ConvertTo-MigrationExtendedPath -Path $payloadPath), $true)
                    $treeEntry.PayloadSha256 = (Get-FileHash -LiteralPath (ConvertTo-MigrationExtendedPath -Path $payloadPath) -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
                    $payloadIndex++
                }
            } else {
                New-Item -ItemType Directory -Path $entryRoot -Force | Out-Null
                $item.PayloadPath = Join-Path $entryRoot 'payload'
                [IO.File]::Copy((ConvertTo-MigrationExtendedPath -Path $target), (ConvertTo-MigrationExtendedPath -Path $item.PayloadPath), $true)
                $item.Entries = @([pscustomobject]@{
                    RelativePath = ''
                    IsDirectory = $false
                    Attributes = [int]$source.Attributes
                    CreationTimeUtc = $source.CreationTimeUtc
                    LastWriteTimeUtc = $source.LastWriteTimeUtc
                    PayloadName = $null
                    PayloadSha256 = (Get-FileHash -LiteralPath (ConvertTo-MigrationExtendedPath -Path $item.PayloadPath) -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
                })
            }
        }
        $item | Add-Member -NotePropertyName SnapshotEntryRoot -NotePropertyValue $entryRoot
        $items.Add($item)
        $index++
    }

    $manifestPath = Join-Path $snapshotRoot 'manifest.json'
    $snapshot = [pscustomobject]@{
        SchemaVersion = 3
        WorkspaceRoot = $root
        SnapshotDirectory = $snapshotRoot
        ManifestPath = $manifestPath
        Items = $items.ToArray()
    }
    $manifest = [pscustomobject]@{
        SchemaVersion = 3
        WorkspaceRoot = $root
        SnapshotDirectory = $snapshotRoot
        Items = @($items.ToArray() | ForEach-Object {
            [pscustomobject]@{
                RelativePath = $_.RelativePath
                Existed = [bool]$_.Existed
                IsDirectory = [bool]$_.IsDirectory
                Entries = @($_.Entries | ForEach-Object {
                    [pscustomobject]@{
                        RelativePath = $_.RelativePath
                        IsDirectory = [bool]$_.IsDirectory
                        Attributes = [int]$_.Attributes
                        CreationTimeUtc = $_.CreationTimeUtc
                        LastWriteTimeUtc = $_.LastWriteTimeUtc
                        PayloadName = $_.PayloadName
                        PayloadSha256 = $_.PayloadSha256
                    }
                })
            }
        })
    }
    $temporaryManifestPath = $manifestPath + '.tmp'
    $manifestJson = ConvertTo-Json -InputObject $manifest -Depth 12
    [IO.File]::WriteAllText($temporaryManifestPath, $manifestJson, [Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporaryManifestPath, $manifestPath)
    return $snapshot
}

function Read-MigrationOutputSnapshot {
    param(
        [Parameter(Mandatory = $true)][string]$ManifestPath,
        [Parameter(Mandatory = $true)][string]$WorkspaceRoot,
        [string[]]$ExpectedRelativePaths,
        [string]$ExpectedManifestSha256
    )

    $manifestPathFull = [IO.Path]::GetFullPath($ManifestPath)
    $manifestFile = Get-Item -LiteralPath $manifestPathFull -Force -ErrorAction Stop
    if ($manifestFile.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Migration output snapshot manifest cannot be a reparse point: $manifestPathFull"
    }
    $snapshotRoot = [IO.Path]::GetDirectoryName($manifestPathFull)
    Assert-MigrationSnapshotDirectoryNoReparse -Path $snapshotRoot
    $manifestBytes = [IO.File]::ReadAllBytes((ConvertTo-MigrationExtendedPath -Path $manifestPathFull))
    if ($PSBoundParameters.ContainsKey('ExpectedManifestSha256')) {
        if ($ExpectedManifestSha256 -notmatch '^[0-9a-f]{64}$') {
            throw 'Migration recovery record manifest SHA-256 is invalid.'
        }
        $sha256 = [Security.Cryptography.SHA256]::Create()
        try {
            $manifestHash = [BitConverter]::ToString($sha256.ComputeHash($manifestBytes)).Replace('-', '').ToLowerInvariant()
        } finally {
            $sha256.Dispose()
        }
        if (-not [string]::Equals($manifestHash, $ExpectedManifestSha256, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Migration recovery snapshot manifest changed after the recovery record was written.'
        }
    }
    $manifestJson = [Text.UTF8Encoding]::new($false, $true).GetString($manifestBytes)
    if ($manifestJson.Length -gt 0 -and $manifestJson[0] -eq [char]0xFEFF) { $manifestJson = $manifestJson.Substring(1) }
    $manifest = ConvertFrom-Json -InputObject $manifestJson -ErrorAction Stop
    $schemaVersion = [int]$manifest.schemaVersion
    if ($schemaVersion -notin @(1, 2, 3) -or -not $manifest.workspaceRoot -or -not $manifest.snapshotDirectory -or -not $manifest.items) {
        throw "Invalid migration output snapshot manifest: $manifestPathFull"
    }
    $root = [IO.Path]::GetFullPath([string]$manifest.workspaceRoot)
    $expectedRoot = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $WorkspaceRoot -ErrorAction Stop).Path)
    if (-not [string]::Equals($root.TrimEnd([IO.Path]::DirectorySeparatorChar),
            $expectedRoot.TrimEnd([IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Migration output snapshot workspace root does not match the expected workspace.'
    }
    $recordedSnapshotRoot = [IO.Path]::GetFullPath([string]$manifest.snapshotDirectory)
    if (-not [string]::Equals($recordedSnapshotRoot, $snapshotRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Migration output snapshot manifest points to a different snapshot directory.'
    }
    $workspacePath = $root.TrimEnd([IO.Path]::DirectorySeparatorChar)
    $workspacePrefix = $workspacePath + [IO.Path]::DirectorySeparatorChar
    if ([string]::Equals($snapshotRoot.TrimEnd([IO.Path]::DirectorySeparatorChar), $workspacePath, [StringComparison]::OrdinalIgnoreCase) -or
        $snapshotRoot.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Migration output snapshot data must be stored outside the workspace.'
    }

    $expectedPathSet = $null
    $manifestPathSet = $null
    if ($PSBoundParameters.ContainsKey('ExpectedRelativePaths')) {
        $expectedPathSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($expectedPath in $ExpectedRelativePaths) {
            if (-not $expectedPathSet.Add([string]$expectedPath)) {
                throw "Expected migration output paths contain a duplicate entry: $expectedPath"
            }
        }
        $manifestPathSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    }

    $items = [Collections.Generic.List[object]]::new()
    $index = 0
    foreach ($record in @($manifest.items)) {
        $relativePath = [string]$record.relativePath
        if ($manifestPathSet) {
            if (-not $expectedPathSet.Contains($relativePath)) {
                throw "Migration output snapshot item is outside the expected output allowlist: $relativePath"
            }
            if (-not $manifestPathSet.Add($relativePath)) {
                throw "Migration output snapshot contains a duplicate output path: $relativePath"
            }
        }
        $target = Resolve-MigrationOutputPath -WorkspaceRoot $root -RelativePath $relativePath
        foreach ($prior in $items) {
            $separator = [IO.Path]::DirectorySeparatorChar
            if ([string]::Equals($prior.TargetPath, $target, [StringComparison]::OrdinalIgnoreCase) -or
                $target.StartsWith($prior.TargetPath.TrimEnd($separator) + $separator, [StringComparison]::OrdinalIgnoreCase) -or
                $prior.TargetPath.StartsWith($target.TrimEnd($separator) + $separator, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Migration output snapshot contains overlapping paths: $($prior.RelativePath) and $relativePath"
            }
        }
        $entryRoot = Join-Path $snapshotRoot ('{0:D4}' -f $index)
        Assert-MigrationSnapshotDirectoryNoReparse -Path $entryRoot
        $entries = @($record.entries | ForEach-Object {
            $payloadName = if ($schemaVersion -ge 2) { [string]$_.payloadName } else { $null }
            [pscustomobject]@{
                RelativePath = [string]$_.relativePath
                IsDirectory = [bool]$_.isDirectory
                Attributes = [int]$_.attributes
                CreationTimeUtc = [datetime]$_.creationTimeUtc
                LastWriteTimeUtc = [datetime]$_.lastWriteTimeUtc
                PayloadName = $payloadName
                PayloadSha256 = if ($schemaVersion -ge 3) { [string]$_.payloadSha256 } else { $null }
            }
        })
        $existed = [bool]$record.existed
        $isDirectory = [bool]$record.isDirectory
        $payloadPath = $null
        if (-not $isDirectory -and $existed) {
            $payloadPath = Join-Path $entryRoot 'payload'
        }
        if ($existed -and $isDirectory -and -not (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $entryRoot) -PathType Container)) {
            throw "Migration output snapshot directory payload is missing: $entryRoot"
        }
        if ($existed -and $isDirectory) {
            $seenTreePaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
            $seenPayloadNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
            $rootEntryCount = 0
            foreach ($entry in $entries) {
                if (([IO.FileAttributes][int]$entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                    throw "Migration output snapshot tree metadata cannot contain a reparse point: $($entry.RelativePath)"
                }
                $directoryMetadata = ([IO.FileAttributes][int]$entry.Attributes -band [IO.FileAttributes]::Directory) -ne 0
                if ($directoryMetadata -ne $entry.IsDirectory) {
                    throw "Migration output snapshot tree metadata type is inconsistent: $($entry.RelativePath)"
                }
                $targetEntry = Resolve-MigrationOutputTreePath -RootPath $target -RelativePath $entry.RelativePath
                if (-not $seenTreePaths.Add($targetEntry)) {
                    throw "Migration output snapshot contains duplicate tree entry: $($entry.RelativePath)"
                }
                if ([string]::IsNullOrEmpty($entry.RelativePath)) {
                    if (-not $entry.IsDirectory) { throw 'Migration output snapshot root entry must be a directory.' }
                    $rootEntryCount++
                }
                if ($entry.IsDirectory) {
                    if ($schemaVersion -ge 2) {
                        if (-not [string]::IsNullOrEmpty($entry.PayloadName)) {
                            throw "Migration output snapshot directory entry has a file payload name: $($entry.RelativePath)"
                        }
                        if ($schemaVersion -ge 3 -and -not [string]::IsNullOrEmpty($entry.PayloadSha256)) {
                            throw "Migration output snapshot directory entry has a file payload hash: $($entry.RelativePath)"
                        }
                    } else {
                        $snapshotEntry = Resolve-MigrationOutputTreePath -RootPath $entryRoot -RelativePath $entry.RelativePath
                        if (-not (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $snapshotEntry) -PathType Container)) {
                            throw "Migration output snapshot directory entry is missing: $snapshotEntry"
                        }
                    }
                } else {
                    if ($schemaVersion -ge 2) {
                        if ($entry.PayloadName -notmatch '^p[0-9]{8}$' -or -not $seenPayloadNames.Add($entry.PayloadName)) {
                            throw "Migration output snapshot file payload name is invalid or duplicated: $($entry.PayloadName)"
                        }
                        $snapshotEntry = Resolve-MigrationSnapshotPayloadPath -EntryRoot $entryRoot -PayloadName $entry.PayloadName
                    } else {
                        $snapshotEntry = Resolve-MigrationOutputTreePath -RootPath $entryRoot -RelativePath $entry.RelativePath
                    }
                    if (-not (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $snapshotEntry) -PathType Leaf)) {
                        throw "Migration output snapshot file entry is missing: $snapshotEntry"
                    }
                    Assert-MigrationOutputPathNoReparse -Path $snapshotEntry -BoundaryRoot $entryRoot
                    if ($schemaVersion -ge 3) {
                        if ($entry.PayloadSha256 -notmatch '^[0-9a-f]{64}$') {
                            throw "Migration output snapshot file payload SHA-256 is invalid: $($entry.RelativePath)"
                        }
                        Assert-MigrationOutputPayloadIntegrity -Path $snapshotEntry -ExpectedSha256 $entry.PayloadSha256
                    }
                }
            }
            if ($rootEntryCount -ne 1) { throw 'Migration output snapshot must contain exactly one tree root entry.' }
        } elseif ($existed -and -not $isDirectory) {
            if ($entries.Count -ne 1 -or $entries[0].IsDirectory -or -not [string]::IsNullOrEmpty($entries[0].RelativePath)) {
                throw 'Migration output snapshot file metadata is invalid.'
            }
        } elseif (-not $existed -and $entries.Count -ne 0) {
            throw 'Migration output snapshot contains metadata for an output that did not exist.'
        }
        if ($existed -and -not $isDirectory -and -not (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $payloadPath) -PathType Leaf)) {
            throw "Migration output snapshot file payload is missing: $payloadPath"
        }
        if ($existed -and -not $isDirectory) {
            Assert-MigrationOutputPathNoReparse -Path $payloadPath -BoundaryRoot $entryRoot
            if ($schemaVersion -ge 3) {
                if ($entries[0].PayloadSha256 -notmatch '^[0-9a-f]{64}$') {
                    throw 'Migration output snapshot file payload SHA-256 is invalid.'
                }
                Assert-MigrationOutputPayloadIntegrity -Path $payloadPath -ExpectedSha256 $entries[0].PayloadSha256
            }
            if (([IO.FileAttributes][int]$entries[0].Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Migration output snapshot file metadata cannot mark a payload as a reparse point.'
            }
            if (([IO.FileAttributes][int]$entries[0].Attributes -band [IO.FileAttributes]::Directory) -ne 0) {
                throw 'Migration output snapshot file metadata cannot mark a payload as a directory.'
            }
        }
        $items.Add([pscustomobject]@{
            RelativePath = $relativePath
            TargetPath = $target
            Existed = $existed
            IsDirectory = $isDirectory
            Entries = $entries
            PayloadPath = $payloadPath
            SnapshotEntryRoot = $entryRoot
            SchemaVersion = $schemaVersion
        })
        $index++
    }

    if ($manifestPathSet -and $manifestPathSet.Count -ne $expectedPathSet.Count) {
        $missingPaths = @($ExpectedRelativePaths | Where-Object { -not $manifestPathSet.Contains([string]$_) })
        throw "Migration output snapshot does not contain the exact expected output allowlist. Missing: $($missingPaths -join ', ')"
    }

    return [pscustomobject]@{
        SchemaVersion = $schemaVersion
        WorkspaceRoot = $root
        SnapshotDirectory = $snapshotRoot
        ManifestPath = $manifestPathFull
        Items = $items.ToArray()
    }
}

function Test-MigrationOutputTimestampEqual {
    param(
        [Parameter(Mandatory = $true)][datetime]$Actual,
        [Parameter(Mandatory = $true)][datetime]$Expected
    )

    return [math]::Abs(($Actual.ToUniversalTime() - $Expected.ToUniversalTime()).Ticks) -le [TimeSpan]::FromMilliseconds(5).Ticks
}

function Test-MigrationOutputSnapshotMatchesWorkspace {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)]$Snapshot)

    foreach ($item in @($Snapshot.Items)) {
        $target = Resolve-MigrationOutputPath -WorkspaceRoot $Snapshot.WorkspaceRoot -RelativePath $item.RelativePath
        $exists = Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target)
        if ([bool]$item.Existed -ne [bool]$exists) { Write-Verbose "Existence differs: $target"; return $false }
        if (-not $exists) { continue }

        $currentItem = Get-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target) -Force -ErrorAction Stop
        $currentIsDirectory = [bool]($currentItem.Attributes -band [IO.FileAttributes]::Directory)
        if ($currentIsDirectory -ne [bool]$item.IsDirectory) { Write-Verbose "Output type differs: $target"; return $false }

        if (-not $item.IsDirectory) {
            $metadata = $item.Entries[0]
            if ([int]$currentItem.Attributes -ne [int]$metadata.Attributes -or
                -not (Test-MigrationOutputTimestampEqual -Actual $currentItem.CreationTimeUtc -Expected ([datetime]$metadata.CreationTimeUtc)) -or
                -not (Test-MigrationOutputTimestampEqual -Actual $currentItem.LastWriteTimeUtc -Expected ([datetime]$metadata.LastWriteTimeUtc))) { Write-Verbose "File metadata differs: $target"; return $false }
            $currentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target) -ErrorAction Stop).Hash
            $snapshotHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (ConvertTo-MigrationExtendedPath -Path $item.PayloadPath) -ErrorAction Stop).Hash
            if (-not [string]::Equals($currentHash, $snapshotHash, [StringComparison]::OrdinalIgnoreCase)) { Write-Verbose "File contents differ: $target"; return $false }
            continue
        }

        $actualEntries = @(Get-MigrationOutputTreeEntries -RootPath $target)
        if ($actualEntries.Count -ne @($item.Entries).Count) { Write-Verbose "Tree entry count differs: $target"; return $false }
        $expectedEntries = @{}
        foreach ($entry in @($item.Entries)) { $expectedEntries[[string]$entry.RelativePath] = $entry }
        foreach ($actual in $actualEntries) {
            if (-not $expectedEntries.ContainsKey([string]$actual.RelativePath)) { Write-Verbose "Tree entry is new: $($actual.RelativePath)"; return $false }
            $expected = $expectedEntries[[string]$actual.RelativePath]
            if ([bool]$actual.IsDirectory -ne [bool]$expected.IsDirectory -or
                [int]$actual.Attributes -ne [int]$expected.Attributes -or
                -not (Test-MigrationOutputTimestampEqual -Actual $actual.CreationTimeUtc -Expected ([datetime]$expected.CreationTimeUtc)) -or
                -not (Test-MigrationOutputTimestampEqual -Actual $actual.LastWriteTimeUtc -Expected ([datetime]$expected.LastWriteTimeUtc))) {
                Write-Verbose "Tree entry metadata differs: $($actual.RelativePath) actualCreation=$($actual.CreationTimeUtc.ToString('o')) expectedCreation=$(([datetime]$expected.CreationTimeUtc).ToString('o')) actualWrite=$($actual.LastWriteTimeUtc.ToString('o')) expectedWrite=$(([datetime]$expected.LastWriteTimeUtc).ToString('o')) actualAttributes=$($actual.Attributes) expectedAttributes=$($expected.Attributes)"
                return $false
            }
            if (-not $actual.IsDirectory) {
                $currentPath = Join-Path $target $actual.RelativePath
                $snapshotPath = if ([string]::IsNullOrEmpty([string]$expected.PayloadName)) {
                    Resolve-MigrationOutputTreePath -RootPath $item.SnapshotEntryRoot -RelativePath $actual.RelativePath
                } else {
                    Resolve-MigrationSnapshotPayloadPath -EntryRoot $item.SnapshotEntryRoot -PayloadName $expected.PayloadName
                }
                $currentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (ConvertTo-MigrationExtendedPath -Path $currentPath) -ErrorAction Stop).Hash
                $snapshotHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (ConvertTo-MigrationExtendedPath -Path $snapshotPath) -ErrorAction Stop).Hash
                if (-not [string]::Equals($currentHash, $snapshotHash, [StringComparison]::OrdinalIgnoreCase)) { Write-Verbose "Tree entry contents differ: $($actual.RelativePath)"; return $false }
            }
        }
    }
    return $true
}

function Set-MigrationOutputMetadata {
    param(
        [Parameter(Mandatory = $true)]$Entry,
        [Parameter(Mandatory = $true)][string]$Path,
        [int[]]$RetryDelaysMilliseconds = @(100, 250, 500, 1000, 1500),
        [scriptblock]$MetadataAction
    )

    $ioPath = ConvertTo-MigrationExtendedPath -Path $Path
    $maxAttempts = $RetryDelaysMilliseconds.Count + 1
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        try {
            if ($MetadataAction) {
                & $MetadataAction $Entry $ioPath
            } elseif ($Entry.IsDirectory) {
                [IO.Directory]::SetCreationTimeUtc($ioPath, [datetime]$Entry.CreationTimeUtc)
                [IO.Directory]::SetLastWriteTimeUtc($ioPath, [datetime]$Entry.LastWriteTimeUtc)
                [IO.File]::SetAttributes($ioPath, [IO.FileAttributes][int]$Entry.Attributes)
            } else {
                [IO.File]::SetCreationTimeUtc($ioPath, [datetime]$Entry.CreationTimeUtc)
                [IO.File]::SetLastWriteTimeUtc($ioPath, [datetime]$Entry.LastWriteTimeUtc)
                [IO.File]::SetAttributes($ioPath, [IO.FileAttributes][int]$Entry.Attributes)
            }
            return
        } catch {
            $metadataError = $_.Exception
            while ($metadataError -is [System.Management.Automation.MethodInvocationException] -and $metadataError.InnerException) {
                $metadataError = $metadataError.InnerException
            }
            $win32ErrorCode = [int]($metadataError.HResult -band 0xFFFF)
            $isWin32Hresult = ($metadataError.HResult -band -65536) -eq -2147024896
            $retryableFileSystemError =
                ($metadataError -is [IO.IOException] -or $metadataError -is [UnauthorizedAccessException]) -and
                $isWin32Hresult -and
                $win32ErrorCode -in @(5, 32, 33)
            $pathExists = if ($Entry.IsDirectory) {
                [IO.Directory]::Exists($ioPath)
            } else {
                [IO.File]::Exists($ioPath)
            }

            if (-not $retryableFileSystemError -or -not $pathExists) {
                throw $metadataError
            }
            $hresultHex = '0x{0:X8}' -f $metadataError.HResult
            if ($attempt -ge $maxAttempts) {
                throw [IO.IOException]::new(
                    "Could not restore migration output metadata after $maxAttempts attempts (exceptionType=$($metadataError.GetType().FullName); HRESULT=$hresultHex; win32Error=$win32ErrorCode; pathExists=$pathExists): $($metadataError.Message)",
                    $metadataError
                )
            }
            Write-Warning "A transient filesystem error prevented migration output metadata restoration attempt $attempt/$maxAttempts (exceptionType=$($metadataError.GetType().FullName); HRESULT=$hresultHex; win32Error=$win32ErrorCode; pathExists=$pathExists); retrying $Path."
            Start-Sleep -Milliseconds $RetryDelaysMilliseconds[$attempt - 1]
        }
    }
}

function Move-MigrationOutputDirectoryWithRetry {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$DestinationPath,
        [int[]]$RetryDelaysMilliseconds = @(100, 250, 500, 1000, 1500, 2000, 2000),
        [scriptblock]$MoveAction
    )

    $sourceExtendedPath = ConvertTo-MigrationExtendedPath -Path $SourcePath
    $destinationExtendedPath = ConvertTo-MigrationExtendedPath -Path $DestinationPath
    $maxAttempts = $RetryDelaysMilliseconds.Count + 1
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        try {
            if ($MoveAction) {
                & $MoveAction $sourceExtendedPath $destinationExtendedPath
            } else {
                [IO.Directory]::Move($sourceExtendedPath, $destinationExtendedPath)
            }
            return
        } catch {
            $moveError = $_.Exception
            while ($moveError -is [System.Management.Automation.MethodInvocationException] -and $moveError.InnerException) {
                $moveError = $moveError.InnerException
            }
            $win32ErrorCode = [int]($moveError.HResult -band 0xFFFF)
            $isWin32Hresult = ($moveError.HResult -band -65536) -eq -2147024896
            $retryableFileSystemError =
                ($moveError -is [IO.IOException] -or $moveError -is [UnauthorizedAccessException]) -and
                $isWin32Hresult -and
                $win32ErrorCode -in @(5, 32, 33)
            $sourceExists = [IO.Directory]::Exists($sourceExtendedPath)
            $destinationExists = [IO.Directory]::Exists($destinationExtendedPath) -or
                [IO.File]::Exists($destinationExtendedPath)

            if (-not $retryableFileSystemError -or -not $sourceExists -or $destinationExists) {
                throw $moveError
            }
            if ($attempt -ge $maxAttempts) {
                $hresultHex = '0x{0:X8}' -f $moveError.HResult
                throw [IO.IOException]::new(
                    "Could not move migration output directory after $maxAttempts attempts (exceptionType=$($moveError.GetType().FullName); HRESULT=$hresultHex; win32Error=$win32ErrorCode; sourceExists=$sourceExists; destinationExists=$destinationExists): $($moveError.Message)",
                    $moveError
                )
            }
            $hresultHex = '0x{0:X8}' -f $moveError.HResult
            Write-Warning "A transient filesystem error prevented migration output directory move attempt $attempt/$maxAttempts (exceptionType=$($moveError.GetType().FullName); HRESULT=$hresultHex; win32Error=$win32ErrorCode; sourceExists=$sourceExists; destinationExists=$destinationExists); retrying $SourcePath -> $DestinationPath."
            Start-Sleep -Milliseconds $RetryDelaysMilliseconds[$attempt - 1]
        }
    }
}

function Restore-MigrationOutputSnapshotItemV3 {
    param(
        [Parameter(Mandatory = $true)]$Snapshot,
        [Parameter(Mandatory = $true)]$Item,
        [scriptblock]$MoveAction
    )

    $target = Resolve-MigrationOutputPath -WorkspaceRoot $Snapshot.WorkspaceRoot -RelativePath $Item.RelativePath
    Assert-MigrationOutputPathNoReparse -Path $target -BoundaryRoot $Snapshot.WorkspaceRoot
    $parent = Split-Path -Parent $target

    if (-not $Item.Existed) {
        if (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target)) {
            Remove-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target) -Recurse -Force -ErrorAction Stop
        }
        return
    }
    [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $parent)) | Out-Null

    if (-not $Item.IsDirectory) {
        Assert-MigrationOutputPathNoReparse -Path $Item.PayloadPath -BoundaryRoot $Item.SnapshotEntryRoot
        Copy-MigrationOutputPayloadVerified -SourcePath $Item.PayloadPath -DestinationPath $target `
            -ExpectedSha256 $Item.Entries[0].PayloadSha256
        Set-MigrationOutputMetadata -Entry $Item.Entries[0] -Path $target
        return
    }

    $stagedRoot = Join-Path $parent ('.migration-output-restore-' + [guid]::NewGuid().ToString('N'))
    $backupPath = $null
    [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $stagedRoot)) | Out-Null
    try {
        $directories = @($Item.Entries | Where-Object IsDirectory | Sort-Object { $_.RelativePath.Length })
        foreach ($entry in $directories) {
            $directoryPath = if ([string]::IsNullOrEmpty($entry.RelativePath)) { $stagedRoot } else { Join-Path $stagedRoot $entry.RelativePath }
            [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $directoryPath)) | Out-Null
        }
        foreach ($entry in @($Item.Entries | Where-Object { -not $_.IsDirectory })) {
            $sourcePath = if ([string]::IsNullOrEmpty([string]$entry.PayloadName)) {
                Resolve-MigrationOutputTreePath -RootPath $Item.SnapshotEntryRoot -RelativePath $entry.RelativePath
            } else {
                Resolve-MigrationSnapshotPayloadPath -EntryRoot $Item.SnapshotEntryRoot -PayloadName $entry.PayloadName
            }
            Assert-MigrationOutputPathNoReparse -Path $sourcePath -BoundaryRoot $Item.SnapshotEntryRoot
            $stagedPath = Resolve-MigrationOutputTreePath -RootPath $stagedRoot -RelativePath $entry.RelativePath
            $stagedParent = Split-Path -Parent $stagedPath
            [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $stagedParent)) | Out-Null
            Copy-MigrationOutputPayloadVerified -SourcePath $sourcePath -DestinationPath $stagedPath `
                -ExpectedSha256 $entry.PayloadSha256
            Set-MigrationOutputMetadata -Entry $entry -Path $stagedPath
        }
        foreach ($entry in @($directories | Sort-Object { $_.RelativePath.Length } -Descending)) {
            $directoryPath = if ([string]::IsNullOrEmpty($entry.RelativePath)) { $stagedRoot } else { Join-Path $stagedRoot $entry.RelativePath }
            Set-MigrationOutputMetadata -Entry $entry -Path $directoryPath
        }

        if (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target)) {
            $backupPath = Join-Path $parent ('.migration-output-backup-' + [guid]::NewGuid().ToString('N'))
            if ([IO.Directory]::Exists((ConvertTo-MigrationExtendedPath -Path $target))) {
                Move-MigrationOutputDirectoryWithRetry -SourcePath $target -DestinationPath $backupPath -MoveAction $MoveAction
            } elseif ([IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $target))) {
                [IO.File]::Move((ConvertTo-MigrationExtendedPath -Path $target), (ConvertTo-MigrationExtendedPath -Path $backupPath))
            } else {
                throw "Migration output restore target changed to an unsupported filesystem entry: $target"
            }
        }
        $directoryInstalled = $false
        try {
            Move-MigrationOutputDirectoryWithRetry -SourcePath $stagedRoot -DestinationPath $target -MoveAction $MoveAction
            $directoryInstalled = $true
            foreach ($entry in @($Item.Entries | Where-Object { -not $_.IsDirectory })) {
                $installedPath = Resolve-MigrationOutputTreePath -RootPath $target -RelativePath $entry.RelativePath
                Assert-MigrationOutputPathNoReparse -Path $installedPath -BoundaryRoot $target
                Assert-MigrationOutputPayloadIntegrity -Path $installedPath -ExpectedSha256 $entry.PayloadSha256
            }
        } catch {
            $directoryInstallFailure = $_.Exception
            $directoryRollbackFailure = $null
            if ($directoryInstalled -and (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target))) {
                try { Remove-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target) -Recurse -Force -ErrorAction Stop }
                catch { $directoryRollbackFailure = $_.Exception }
                if ((Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target)) -and -not $directoryRollbackFailure) {
                    $directoryRollbackFailure = [IO.IOException]::new("Could not remove the unverified restore directory: $target")
                }
            }
            if ($backupPath -and -not (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target)) -and
                (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $backupPath))) {
                if ([IO.Directory]::Exists((ConvertTo-MigrationExtendedPath -Path $backupPath))) {
                    try { Move-MigrationOutputDirectoryWithRetry -SourcePath $backupPath -DestinationPath $target -MoveAction $MoveAction }
                    catch { $directoryRollbackFailure = $_.Exception }
                } elseif ([IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $backupPath))) {
                    try { [IO.File]::Move((ConvertTo-MigrationExtendedPath -Path $backupPath), (ConvertTo-MigrationExtendedPath -Path $target)) }
                    catch { $directoryRollbackFailure = $_.Exception }
                }
            }
            if ($directoryRollbackFailure) {
                throw [AggregateException]::new("Verified directory installation failed and rollback also failed. Preserve recovery data; target='$target', backup='$backupPath'.", `
                    [Exception[]]@($directoryInstallFailure, $directoryRollbackFailure))
            }
            throw $directoryInstallFailure
        }
        $stagedRoot = $null
        if ($backupPath -and (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $backupPath))) {
            Remove-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $backupPath) -Recurse -Force -ErrorAction Stop
        }
    } catch {
        $restoreFailure = $_.Exception
        if ($stagedRoot -and (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $stagedRoot))) {
            try { Remove-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $stagedRoot) -Recurse -Force -ErrorAction Stop } catch { }
        }
        if ($backupPath -and (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $backupPath)) -and
            -not (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target))) {
            if ([IO.Directory]::Exists((ConvertTo-MigrationExtendedPath -Path $backupPath))) {
                try { Move-MigrationOutputDirectoryWithRetry -SourcePath $backupPath -DestinationPath $target -MoveAction $MoveAction } catch { }
            } elseif ([IO.File]::Exists((ConvertTo-MigrationExtendedPath -Path $backupPath))) {
                try { [IO.File]::Move((ConvertTo-MigrationExtendedPath -Path $backupPath), (ConvertTo-MigrationExtendedPath -Path $target)) } catch { }
            }
        }
        throw $restoreFailure
    }
}

function Restore-MigrationOutputSnapshot {
    param([Parameter(Mandatory = $true)]$Snapshot)

    if ([int]$Snapshot.SchemaVersion -ge 3) {
        foreach ($item in @($Snapshot.Items) | Where-Object Existed) {
            if ($item.IsDirectory) {
                foreach ($entry in @($item.Entries | Where-Object { -not $_.IsDirectory })) {
                    $payloadPath = if ([string]::IsNullOrEmpty([string]$entry.PayloadName)) {
                        Resolve-MigrationOutputTreePath -RootPath $item.SnapshotEntryRoot -RelativePath $entry.RelativePath
                    } else {
                        Resolve-MigrationSnapshotPayloadPath -EntryRoot $item.SnapshotEntryRoot -PayloadName $entry.PayloadName
                    }
                    Assert-MigrationOutputPayloadIntegrity -Path $payloadPath -ExpectedSha256 $entry.PayloadSha256
                }
            } else {
                Assert-MigrationOutputPayloadIntegrity -Path $item.PayloadPath -ExpectedSha256 $item.Entries[0].PayloadSha256
            }
        }
    }

    $restoreErrors = [Collections.Generic.List[string]]::new()
    foreach ($item in @($Snapshot.Items)) {
      try {
        if ([int]$Snapshot.SchemaVersion -ge 3) {
            Restore-MigrationOutputSnapshotItemV3 -Snapshot $Snapshot -Item $item
            continue
        }
        $target = Resolve-MigrationOutputPath -WorkspaceRoot $Snapshot.WorkspaceRoot -RelativePath $item.RelativePath
        if (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target)) {
            Remove-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $target) -Recurse -Force -ErrorAction Stop
        }
        if (-not $item.Existed) { continue }

        if (-not $item.IsDirectory) {
            $parent = Split-Path -Parent $target
            [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $parent)) | Out-Null
            Assert-MigrationOutputPathNoReparse -Path $item.PayloadPath -BoundaryRoot $item.SnapshotEntryRoot
            if ([int]$Snapshot.SchemaVersion -ge 3) {
                Copy-MigrationOutputPayloadVerified -SourcePath $item.PayloadPath -DestinationPath $target `
                    -ExpectedSha256 $item.Entries[0].PayloadSha256
            } else {
                [IO.File]::Copy((ConvertTo-MigrationExtendedPath -Path $item.PayloadPath), (ConvertTo-MigrationExtendedPath -Path $target), $true)
            }
            Set-MigrationOutputMetadata -Entry $item.Entries[0] -Path $target
            continue
        }

        $directories = @($item.Entries | Where-Object IsDirectory | Sort-Object { $_.RelativePath.Length })
        foreach ($entry in $directories) {
            $directoryPath = if ([string]::IsNullOrEmpty($entry.RelativePath)) { $target } else { Join-Path $target $entry.RelativePath }
            [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $directoryPath)) | Out-Null
        }
        foreach ($entry in @($item.Entries | Where-Object { -not $_.IsDirectory })) {
            $sourcePath = if ([string]::IsNullOrEmpty([string]$entry.PayloadName)) {
                Resolve-MigrationOutputTreePath -RootPath $item.SnapshotEntryRoot -RelativePath $entry.RelativePath
            } else {
                Resolve-MigrationSnapshotPayloadPath -EntryRoot $item.SnapshotEntryRoot -PayloadName $entry.PayloadName
            }
            $targetPath = Resolve-MigrationOutputTreePath -RootPath $target -RelativePath $entry.RelativePath
            $parent = Split-Path -Parent $targetPath
            [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $parent)) | Out-Null
            if ([int]$Snapshot.SchemaVersion -ge 3) {
                Copy-MigrationOutputPayloadVerified -SourcePath $sourcePath -DestinationPath $targetPath `
                    -ExpectedSha256 $entry.PayloadSha256
            } else {
                [IO.File]::Copy((ConvertTo-MigrationExtendedPath -Path $sourcePath), (ConvertTo-MigrationExtendedPath -Path $targetPath), $true)
            }
            Set-MigrationOutputMetadata -Entry $entry -Path $targetPath
        }
        foreach ($entry in @($directories | Sort-Object { $_.RelativePath.Length } -Descending)) {
            $directoryPath = if ([string]::IsNullOrEmpty($entry.RelativePath)) { $target } else { Join-Path $target $entry.RelativePath }
            Set-MigrationOutputMetadata -Entry $entry -Path $directoryPath
        }
      } catch {
        $restoreErrors.Add(($item.RelativePath + ': ' + $_.Exception.Message))
      }
    }
    if ($restoreErrors.Count -gt 0) {
        throw ('Failed to restore one or more local build outputs: ' + ($restoreErrors -join '; '))
    }
}
