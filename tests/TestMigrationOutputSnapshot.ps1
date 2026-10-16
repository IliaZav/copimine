$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$snapshotScript = Join-Path $repoRoot 'scripts/minecraft/MigrationOutputSnapshot.ps1'
$snapshotSource = Get-Content -LiteralPath $snapshotScript -Raw
if ($snapshotSource -notmatch '(?s)\$destinationParent = Split-Path -Parent \$DestinationPath\s+Assert-MigrationOutputPathNoReparse -Path \$DestinationPath -BoundaryRoot \$destinationParent') {
    throw 'Verified file restoration must scope its post-install reparse check to the destination parent.'
}
. $snapshotScript

$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('copimine-output-snapshot-test-' + [guid]::NewGuid().ToString('N'))
$snapshotRoot = Join-Path ([IO.Path]::GetTempPath()) ('copimine-output-snapshot-data-' + [guid]::NewGuid().ToString('N') + '-padding-abcdefghijklmnop')
$legacySnapshotRoot = Join-Path ([IO.Path]::GetTempPath()) ('copimine-output-snapshot-legacy-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
try {
    $escapeRejected = $false
    try {
        New-MigrationOutputSnapshot -WorkspaceRoot $testRoot -SnapshotDirectory $snapshotRoot -RelativePaths @('..\outside.txt') | Out-Null
    } catch {
        $escapeRejected = $true
    }
    if (-not $escapeRejected) { throw 'A snapshot target outside the workspace was accepted.' }

    $nestedSnapshot = Join-Path $testRoot 'snapshot-data'
    $nestedSnapshotRejected = $false
    try {
        New-MigrationOutputSnapshot -WorkspaceRoot $testRoot -SnapshotDirectory $nestedSnapshot -RelativePaths @('existing.jar') | Out-Null
    } catch {
        $nestedSnapshotRejected = $true
    }
    if (-not $nestedSnapshotRejected -or (Test-Path -LiteralPath $nestedSnapshot)) {
        throw 'Snapshot data inside the workspace was not rejected before creation.'
    }

    $existingFile = Join-Path $testRoot 'existing.jar'
    [IO.File]::WriteAllText($existingFile, 'original jar')
    $existingDirectory = Join-Path $testRoot 'existing-build'
    New-Item -ItemType Directory -Path (Join-Path $existingDirectory 'classes') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $existingDirectory 'empty-assets') -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $existingDirectory 'classes/marker.txt'), 'original build output')
    $longDirectory = $existingDirectory
    while ((Join-Path $longDirectory 'long-path-output.jar').Length -lt 275) {
        $longDirectory = Join-Path $longDirectory ('nested-' + ('x' * 38))
    }
    [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $longDirectory)) | Out-Null
    $longFileName = 'long-path-output.jar'
    $longFilePath = Join-Path $longDirectory $longFileName
    $longRelativePath = $longFilePath.Substring($existingDirectory.Length + 1)
    if ($longFilePath.Length -le 260) { throw 'The long-path snapshot fixture does not exceed the legacy source path limit.' }
    [IO.File]::WriteAllText((ConvertTo-MigrationExtendedPath -Path $longFilePath), 'original long-path build output')
    # Windows may publish directory write times a few milliseconds after fixture creation.
    $stableDirectoryTimestamp = [datetime]::new(2020, 1, 2, 3, 4, 5, [DateTimeKind]::Utc)
    $fixtureDirectories = @((Get-Item -LiteralPath $existingDirectory -Force)) + @(
        Get-ChildItem -LiteralPath $existingDirectory -Directory -Recurse -Force
    )
    foreach ($fixtureDirectory in @($fixtureDirectories | Sort-Object { $_.FullName.Length } -Descending)) {
        $fixtureDirectoryPath = ConvertTo-MigrationExtendedPath -Path $fixtureDirectory.FullName
        [IO.Directory]::SetCreationTimeUtc($fixtureDirectoryPath, $stableDirectoryTimestamp)
        [IO.Directory]::SetLastWriteTimeUtc($fixtureDirectoryPath, $stableDirectoryTimestamp)
    }
    $copyTargetPath = Join-Path $testRoot 'copy-integrity-target.bin'
    [IO.File]::WriteAllText($copyTargetPath, 'pre-existing target')
    $copyFailureMessage = ''
    try {
        Copy-MigrationOutputPayloadVerified -SourcePath $existingFile -DestinationPath $copyTargetPath `
            -ExpectedSha256 ('0' * 64)
    } catch {
        $copyFailureMessage = $_.Exception.Message
    }
    if ($copyFailureMessage -notmatch 'payload SHA-256 does not match') {
        throw "A failed verified payload copy was accepted or rejected for another reason: $copyFailureMessage"
    }
    if ([IO.File]::ReadAllText($copyTargetPath) -ne 'pre-existing target') {
        throw 'A failed verified payload copy changed the existing destination.'
    }
    $copyStagingFiles = @(Get-ChildItem -LiteralPath $testRoot -Filter 'copy-integrity-target.bin.restore-*.tmp' -Force)
    if ($copyStagingFiles.Count -ne 0) {
        throw 'A failed verified payload copy left a staging file behind.'
    }
    $existingFileSha256 = (Get-FileHash -LiteralPath $existingFile -Algorithm SHA256).Hash.ToLowerInvariant()
    Copy-MigrationOutputPayloadVerified -SourcePath $existingFile -DestinationPath $copyTargetPath -ExpectedSha256 $existingFileSha256
    if ([IO.File]::ReadAllText($copyTargetPath) -ne 'original jar') {
        throw 'A verified payload copy did not replace the existing destination.'
    }
    $copyStagingFiles = @(Get-ChildItem -LiteralPath $testRoot -Filter 'copy-integrity-target.bin.restore-*.tmp' -Force)
    $copyBackupFiles = @(Get-ChildItem -LiteralPath $testRoot -Filter 'copy-integrity-target.bin.restore-backup-*.tmp' -Force)
    if ($copyStagingFiles.Count -ne 0 -or $copyBackupFiles.Count -ne 0) {
        throw 'A successful verified payload copy left staging or backup files behind.'
    }
    $absentFile = Join-Path $testRoot 'generated.jar'
    $absentDirectory = Join-Path $testRoot 'generated-build'

    $snapshot = New-MigrationOutputSnapshot -WorkspaceRoot $testRoot -SnapshotDirectory $snapshotRoot -RelativePaths @(
        'existing.jar', 'existing-build', 'generated.jar', 'generated-build'
    )
    if (-not (Test-Path -LiteralPath $snapshot.ManifestPath -PathType Leaf)) {
        throw 'The snapshot recovery manifest was not persisted.'
    }

    $metadataRetryPath = Join-Path $testRoot 'metadata-retry-build'
    [IO.Directory]::CreateDirectory($metadataRetryPath) | Out-Null
    $rootDirectoryEntry = @($snapshot.Items[1].Entries | Where-Object { $_.IsDirectory -and [string]::IsNullOrEmpty($_.RelativePath) }) | Select-Object -First 1
    if (-not $rootDirectoryEntry) { throw 'The directory metadata retry fixture has no root entry.' }
    $metadataRetryState = [pscustomobject]@{ Attempts = 0 }
    $metadataAction = {
        param($entry, [string]$ioPath)
        $metadataRetryState.Attempts += 1
        if ($metadataRetryState.Attempts -eq 1) {
            [IO.Directory]::SetCreationTimeUtc($ioPath, [datetime]$entry.CreationTimeUtc)
            throw [IO.IOException]::new('injected transient sharing violation', -2147024864)
        }
        [IO.Directory]::SetCreationTimeUtc($ioPath, [datetime]$entry.CreationTimeUtc)
        [IO.Directory]::SetLastWriteTimeUtc($ioPath, [datetime]$entry.LastWriteTimeUtc)
        [IO.File]::SetAttributes($ioPath, [IO.FileAttributes][int]$entry.Attributes)
    }
    $previousWarningPreference = $WarningPreference
    try {
        $WarningPreference = 'SilentlyContinue'
        Set-MigrationOutputMetadata -Entry $rootDirectoryEntry -Path $metadataRetryPath `
            -MetadataAction $metadataAction -RetryDelaysMilliseconds @(1)
    } finally {
        $WarningPreference = $previousWarningPreference
    }
    $metadataRetryAttributes = [IO.File]::GetAttributes($metadataRetryPath)
    if ($metadataRetryState.Attempts -ne 2 -or -not [IO.Directory]::Exists($metadataRetryPath) -or
        [IO.Directory]::GetCreationTimeUtc($metadataRetryPath) -ne [datetime]$rootDirectoryEntry.CreationTimeUtc -or
        [IO.Directory]::GetLastWriteTimeUtc($metadataRetryPath) -ne [datetime]$rootDirectoryEntry.LastWriteTimeUtc -or
        $metadataRetryAttributes -ne [IO.FileAttributes][int]$rootDirectoryEntry.Attributes) {
        throw 'A partial metadata update was not safely completed after a transient sharing violation.'
    }

    $unrelatedMetadataState = [pscustomobject]@{ Attempts = 0 }
    $unrelatedMetadataAction = {
        param($entry, [string]$ioPath)
        $unrelatedMetadataState.Attempts += 1
        throw [IO.IOException]::new('unrelated HRESULT with a Win32-like low word', -2146238459)
    }
    $unrelatedMetadataMessage = ''
    try {
        Set-MigrationOutputMetadata -Entry $rootDirectoryEntry -Path $metadataRetryPath `
            -MetadataAction $unrelatedMetadataAction -RetryDelaysMilliseconds @(1)
    } catch {
        $unrelatedMetadataMessage = $_.Exception.Message
    }
    if ($unrelatedMetadataState.Attempts -ne 1 -or
        $unrelatedMetadataMessage -ne 'unrelated HRESULT with a Win32-like low word') {
        throw "A non-Win32 HRESULT was retried or reported incorrectly (attempts=$($unrelatedMetadataState.Attempts); message='$unrelatedMetadataMessage')."
    }

    $manifestPath = Join-Path $snapshotRoot 'manifest.json'
    $validManifest = Get-Content -LiteralPath $manifestPath -Raw
    $validManifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash
    $expectedRelativePaths = @('existing.jar', 'existing-build', 'generated.jar', 'generated-build')
    Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot `
        -ExpectedRelativePaths $expectedRelativePaths -ExpectedManifestSha256 $validManifestSha256 | Out-Null
    if (-not (Test-MigrationOutputSnapshotMatchesWorkspace -Snapshot $snapshot -Verbose)) {
        throw 'An unchanged workspace did not match its captured output snapshot.'
    }
    $longFileEntry = @($snapshot.Items[1].Entries | Where-Object { $_.RelativePath -eq $longRelativePath }) | Select-Object -First 1
    if (-not $longFileEntry -or [string]::IsNullOrWhiteSpace([string]$longFileEntry.PayloadName)) {
        throw 'A long nested output path was not stored under a compact snapshot payload name.'
    }
    $longPayloadPath = Join-Path $snapshot.Items[1].SnapshotEntryRoot ([string]$longFileEntry.PayloadName)
    if ($longPayloadPath.Length -ge 240) {
        throw "The long-path snapshot payload was not kept short: $($longPayloadPath.Length) characters."
    }

    $originalLongPayload = [IO.File]::ReadAllText((ConvertTo-MigrationExtendedPath -Path $longPayloadPath))
    [IO.File]::WriteAllText((ConvertTo-MigrationExtendedPath -Path $longPayloadPath), 'tampered directory payload')
    $directoryPayloadMessage = ''
    try {
        Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot `
            -ExpectedRelativePaths $expectedRelativePaths | Out-Null
    } catch {
        $directoryPayloadMessage = $_.Exception.Message
    }
    if ($directoryPayloadMessage -notmatch 'payload SHA-256') {
        throw "A modified directory snapshot payload was accepted or rejected for another reason: $directoryPayloadMessage"
    }
    [IO.File]::WriteAllText((ConvertTo-MigrationExtendedPath -Path $longPayloadPath), $originalLongPayload)

    $singleFilePayloadPath = $snapshot.Items[0].PayloadPath
    [IO.File]::WriteAllText((ConvertTo-MigrationExtendedPath -Path $singleFilePayloadPath), 'tampered top-level payload')
    $singleFilePayloadMessage = ''
    try {
        Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot `
            -ExpectedRelativePaths $expectedRelativePaths | Out-Null
    } catch {
        $singleFilePayloadMessage = $_.Exception.Message
    }
    if ($singleFilePayloadMessage -notmatch 'payload SHA-256') {
        throw "A modified single-file snapshot payload was accepted or rejected for another reason: $singleFilePayloadMessage"
    }
    [IO.File]::WriteAllText((ConvertTo-MigrationExtendedPath -Path $singleFilePayloadPath), 'original jar')

    $tamperedManifest = $validManifest | ConvertFrom-Json
    $tamperedManifest.items[1].entries[2].relativePath = '..\outside.txt'
    [IO.File]::WriteAllText($manifestPath, (ConvertTo-Json -InputObject $tamperedManifest -Depth 12), [Text.UTF8Encoding]::new($false))
    $treeEscapeMessage = ''
    try {
        Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot | Out-Null
    } catch {
        $treeEscapeMessage = $_.Exception.Message
    }
    if ($treeEscapeMessage -notmatch 'escapes the migration output directory') {
        throw "A snapshot tree path outside its output directory was accepted or rejected for another reason: $treeEscapeMessage"
    }

    $tamperedManifest = $validManifest | ConvertFrom-Json
    $flatFiles = @($tamperedManifest.items[1].entries | Where-Object { -not $_.isDirectory })
    if ($flatFiles.Count -lt 2) { throw 'The snapshot did not contain the two expected build files.' }
    $flatFiles[1].payloadName = $flatFiles[0].payloadName
    [IO.File]::WriteAllText($manifestPath, (ConvertTo-Json -InputObject $tamperedManifest -Depth 12), [Text.UTF8Encoding]::new($false))
    $duplicatePayloadMessage = ''
    try {
        Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot | Out-Null
    } catch {
        $duplicatePayloadMessage = $_.Exception.Message
    }
    if ($duplicatePayloadMessage -notmatch 'payload name is invalid or duplicated') {
        throw "A duplicate snapshot payload name was accepted or rejected for another reason: $duplicatePayloadMessage"
    }

    $tamperedManifest = $validManifest | ConvertFrom-Json
    $firstFlatFile = @($tamperedManifest.items[1].entries | Where-Object { -not $_.isDirectory }) | Select-Object -First 1
    $firstFlatFile.payloadName = '..\outside.txt'
    [IO.File]::WriteAllText($manifestPath, (ConvertTo-Json -InputObject $tamperedManifest -Depth 12), [Text.UTF8Encoding]::new($false))
    $unsafePayloadMessage = ''
    try {
        Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot | Out-Null
    } catch {
        $unsafePayloadMessage = $_.Exception.Message
    }
    if ($unsafePayloadMessage -notmatch 'payload name is invalid') {
        throw "An unsafe snapshot payload path was accepted or rejected for another reason: $unsafePayloadMessage"
    }

    $tamperedManifest = $validManifest | ConvertFrom-Json
    $tamperedManifest.workspaceRoot = [IO.Path]::GetPathRoot($testRoot)
    [IO.File]::WriteAllText($manifestPath, (ConvertTo-Json -InputObject $tamperedManifest -Depth 12), [Text.UTF8Encoding]::new($false))
    $workspaceMismatchMessage = ''
    try {
        Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot | Out-Null
    } catch {
        $workspaceMismatchMessage = $_.Exception.Message
    }
    if ($workspaceMismatchMessage -notmatch 'does not match the expected workspace') {
        throw "A snapshot manifest for a different workspace was accepted or rejected for another reason: $workspaceMismatchMessage"
    }

    $tamperedManifest = $validManifest | ConvertFrom-Json
    $tamperedManifest.workspaceRoot = [IO.Path]::GetPathRoot($testRoot)
    [IO.File]::WriteAllText($manifestPath, (ConvertTo-Json -InputObject $tamperedManifest -Depth 12), [Text.UTF8Encoding]::new($false))
    $manifestDigestMessage = ''
    try {
        Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot `
            -ExpectedManifestSha256 $validManifestSha256 | Out-Null
    } catch {
        $manifestDigestMessage = $_.Exception.Message
    }
    if ($manifestDigestMessage -notmatch 'manifest changed after the recovery record was written') {
        throw "A manifest changed after recovery-record creation was accepted or rejected for another reason: $manifestDigestMessage"
    }

    $tamperedManifest = $validManifest | ConvertFrom-Json
    $tamperedManifest.items[2].relativePath = 'unlisted-output'
    $allowlistManifestMessage = ''
    [IO.File]::WriteAllText($manifestPath, (ConvertTo-Json -InputObject $tamperedManifest -Depth 12), [Text.UTF8Encoding]::new($false))
    try {
        $unlistedSnapshot = Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot `
            -ExpectedRelativePaths $expectedRelativePaths
        Restore-MigrationOutputSnapshot -Snapshot $unlistedSnapshot
    } catch {
        $allowlistManifestMessage = $_.Exception.Message
    }
    if ($allowlistManifestMessage -notmatch 'outside the expected output allowlist') {
        throw "A snapshot item outside the expected output allowlist was accepted or rejected for another reason: $allowlistManifestMessage"
    }
    if (Test-Path -LiteralPath (Join-Path $testRoot 'unlisted-output')) {
        throw 'A rejected snapshot manifest altered an output outside the expected allowlist.'
    }

    [IO.File]::WriteAllText($manifestPath, $validManifest, [Text.UTF8Encoding]::new($false))

    [IO.File]::SetAttributes($existingFile, [IO.FileAttributes]::Normal)
    [IO.File]::WriteAllText($existingFile, 'new jar')
    [IO.File]::SetAttributes($existingFile, [IO.FileAttributes]::ReadOnly)
    Remove-Item -LiteralPath $existingDirectory -Recurse -Force
    [IO.File]::WriteAllText($absentFile, 'new generated jar')
    New-Item -ItemType Directory -Path $absentDirectory -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $absentDirectory 'new.txt'), 'generated')
    if (Test-MigrationOutputSnapshotMatchesWorkspace -Snapshot $snapshot) {
        throw 'Post-snapshot output changes were not detected.'
    }

    [IO.File]::WriteAllText((ConvertTo-MigrationExtendedPath -Path $longPayloadPath), 'tampered before restore')
    $restoreIntegrityMessage = ''
    try {
        Restore-MigrationOutputSnapshot -Snapshot $snapshot
    } catch {
        $restoreIntegrityMessage = $_.Exception.Message
    }
    if ($restoreIntegrityMessage -notmatch 'payload SHA-256') {
        throw "A modified payload was restored or rejected for another reason: $restoreIntegrityMessage"
    }
    if ([IO.File]::ReadAllText($existingFile) -ne 'new jar' -or
        (Test-Path -LiteralPath (ConvertTo-MigrationExtendedPath -Path $longFilePath)) -or
        [IO.File]::ReadAllText($absentFile) -ne 'new generated jar') {
        throw 'A tampered payload caused workspace outputs to change before restore was rejected.'
    }
    [IO.File]::WriteAllText((ConvertTo-MigrationExtendedPath -Path $longPayloadPath), $originalLongPayload)

    # Recovery must work from the on-disk manifest after the in-memory object is gone.
    $snapshot = $null
    $snapshot = Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $testRoot `
        -ExpectedRelativePaths $expectedRelativePaths
    Restore-MigrationOutputSnapshot -Snapshot $snapshot
    if (-not (Test-MigrationOutputSnapshotMatchesWorkspace -Snapshot $snapshot)) {
        throw 'The restored workspace does not match its captured output snapshot.'
    }

    if ([IO.File]::ReadAllText($existingFile) -ne 'original jar') { throw 'Existing file contents were not restored.' }
    if ([IO.File]::GetAttributes($existingFile) -band [IO.FileAttributes]::ReadOnly) {
        throw 'Existing file attributes were not restored after replacing a read-only destination.'
    }
    if ([IO.File]::ReadAllText((Join-Path $existingDirectory 'classes/marker.txt')) -ne 'original build output') {
        throw 'Existing directory contents were not restored.'
    }
    if ([IO.File]::ReadAllText((ConvertTo-MigrationExtendedPath -Path $longFilePath)) -ne 'original long-path build output') {
        throw 'Existing long-path build output was not restored.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $existingDirectory 'empty-assets') -PathType Container)) {
        throw 'An empty directory from an existing build tree was not restored.'
    }
    if (Test-Path -LiteralPath $absentFile) { throw 'A newly generated file was not removed.' }
    if (Test-Path -LiteralPath $absentDirectory) { throw 'A newly generated directory was not removed.' }

    $moveRetrySource = Join-Path $testRoot 'transient-move-source'
    $moveRetryTarget = Join-Path $testRoot 'transient-move-target'
    [IO.Directory]::CreateDirectory($moveRetrySource) | Out-Null
    $moveRetryFile = Join-Path $moveRetrySource 'locked.bin'
    [IO.File]::WriteAllText($moveRetryFile, 'transient file lock')
    $moveAttemptState = [pscustomobject]@{ Attempts = 0; FirstErrorType = $null; FirstWin32Error = $null }
    $moveAction = {
        param([string]$sourcePath, [string]$destinationPath)
        $moveAttemptState.Attempts += 1
        if ($moveAttemptState.Attempts -eq 1) {
            $lockedFilePath = $sourcePath.TrimEnd('\') + '\locked.bin'
            $lockStream = [IO.File]::Open($lockedFilePath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
            try {
                try {
                    [IO.Directory]::Move($sourcePath, $destinationPath)
                } catch {
                    $firstMoveError = $_.Exception
                    while ($firstMoveError -is [System.Management.Automation.MethodInvocationException] -and $firstMoveError.InnerException) {
                        $firstMoveError = $firstMoveError.InnerException
                    }
                    $moveAttemptState.FirstErrorType = $firstMoveError.GetType().FullName
                    $moveAttemptState.FirstWin32Error = [int]($firstMoveError.HResult -band 0xFFFF)
                    throw
                }
            } finally {
                $lockStream.Dispose()
            }
            return
        }
        [IO.Directory]::Move($sourcePath, $destinationPath)
    }
    $previousWarningPreference = $WarningPreference
    try {
        $WarningPreference = 'SilentlyContinue'
        Move-MigrationOutputDirectoryWithRetry -SourcePath $moveRetrySource -DestinationPath $moveRetryTarget `
            -MoveAction $moveAction -RetryDelaysMilliseconds @(1) | Out-Null
    } finally {
        $WarningPreference = $previousWarningPreference
    }
    if ($moveAttemptState.Attempts -ne 2 -or $moveAttemptState.FirstErrorType -notin @('System.IO.IOException', 'System.UnauthorizedAccessException') -or
        $moveAttemptState.FirstWin32Error -notin @(5, 32, 33) -or -not [IO.Directory]::Exists($moveRetryTarget) -or
        [IO.Directory]::Exists($moveRetrySource)) {
        throw "A transient filesystem error was not diagnosed and retried after release (attempts=$($moveAttemptState.Attempts), type=$($moveAttemptState.FirstErrorType), win32=$($moveAttemptState.FirstWin32Error))."
    }

    $unrelatedMoveSource = Join-Path $testRoot 'unrelated-move-source'
    $unrelatedMoveTarget = Join-Path $testRoot 'unrelated-move-target'
    [IO.Directory]::CreateDirectory($unrelatedMoveSource) | Out-Null
    $unrelatedMoveState = [pscustomobject]@{ Attempts = 0 }
    $unrelatedMoveAction = {
        param([string]$sourcePath, [string]$destinationPath)
        $unrelatedMoveState.Attempts += 1
        throw [IO.IOException]::new('unrelated move HRESULT with a Win32-like low word', -2146238459)
    }
    $unrelatedMoveMessage = ''
    try {
        Move-MigrationOutputDirectoryWithRetry -SourcePath $unrelatedMoveSource -DestinationPath $unrelatedMoveTarget `
            -MoveAction $unrelatedMoveAction -RetryDelaysMilliseconds @(1, 1) | Out-Null
    } catch {
        $unrelatedMoveMessage = $_.Exception.Message
    }
    if ($unrelatedMoveState.Attempts -ne 1 -or
        $unrelatedMoveMessage -ne 'unrelated move HRESULT with a Win32-like low word' -or
        -not [IO.Directory]::Exists($unrelatedMoveSource) -or [IO.Directory]::Exists($unrelatedMoveTarget)) {
        throw "A non-Win32 HRESULT was retried or reported incorrectly (attempts=$($unrelatedMoveState.Attempts); message='$unrelatedMoveMessage')."
    }

    $transactionTarget = Join-Path $testRoot 'rollback-build'
    $transactionMarker = Join-Path $transactionTarget 'classes/marker.txt'
    [IO.Directory]::CreateDirectory((Split-Path -Parent $transactionMarker)) | Out-Null
    [IO.File]::WriteAllText($transactionMarker, 'generated output that must be rolled back')
    $transactionItem = [pscustomobject]@{
        Existed = $true
        RelativePath = 'rollback-build'
        IsDirectory = $true
        Entries = $snapshot.Items[1].Entries
        PayloadPath = $null
        SnapshotEntryRoot = $snapshot.Items[1].SnapshotEntryRoot
    }
    $transactionSnapshot = [pscustomobject]@{ WorkspaceRoot = $testRoot }
    $transactionTargetExtended = (ConvertTo-MigrationExtendedPath -Path $transactionTarget).TrimEnd('\')
    $installFailure = [InvalidOperationException]::new('injected stage installation failure')
    $rollbackMove = {
        param([string]$sourcePath, [string]$destinationPath)
        if ($destinationPath.TrimEnd('\') -ieq $transactionTargetExtended -and
            $sourcePath.Contains('.migration-output-restore-')) {
            throw $installFailure
        }
        [IO.Directory]::Move($sourcePath, $destinationPath)
    }
    $installationFailureMessage = ''
    try {
        Restore-MigrationOutputSnapshotItemV3 -Snapshot $transactionSnapshot -Item $transactionItem -MoveAction $rollbackMove
    } catch {
        $installationFailureMessage = $_.Exception.Message
    }
    $transactionMarkerContents = [IO.File]::ReadAllText($transactionMarker)
    $transactionBackupDirectories = @(Get-ChildItem -LiteralPath $testRoot -Directory -Filter '.migration-output-backup-*' -Force)
    $transactionRestoreDirectories = @(Get-ChildItem -LiteralPath $testRoot -Directory -Filter '.migration-output-restore-*' -Force)
    if ($installationFailureMessage -ne $installFailure.Message -or
        $transactionMarkerContents -ne 'generated output that must be rolled back' -or
        $transactionBackupDirectories.Count -ne 0 -or $transactionRestoreDirectories.Count -ne 0) {
        throw "A failed staged installation did not restore the pre-attempt output (error='$installationFailureMessage'; marker='$transactionMarkerContents'; backupCount=$($transactionBackupDirectories.Count); restoreCount=$($transactionRestoreDirectories.Count))."
    }

    [IO.File]::WriteAllText($transactionMarker, 'generated output with a retained recovery backup')
    $rollbackFailure = [InvalidOperationException]::new('injected backup rollback failure')
    $rollbackFailureState = [pscustomobject]@{ BackupPath = $null; RollbackAttempts = 0 }
    $failedRollbackMove = {
        param([string]$sourcePath, [string]$destinationPath)
        if ($destinationPath.TrimEnd('\') -ieq $transactionTargetExtended -and
            $sourcePath.Contains('.migration-output-restore-')) {
            throw $installFailure
        }
        if ($destinationPath.TrimEnd('\') -ieq $transactionTargetExtended -and
            $sourcePath.Contains('.migration-output-backup-')) {
            $rollbackFailureState.RollbackAttempts += 1
            $rollbackFailureState.BackupPath = $sourcePath
            throw $rollbackFailure
        }
        [IO.Directory]::Move($sourcePath, $destinationPath)
    }
    $aggregateFailure = $null
    try {
        Restore-MigrationOutputSnapshotItemV3 -Snapshot $transactionSnapshot -Item $transactionItem -MoveAction $failedRollbackMove
    } catch {
        $aggregateFailure = $_.Exception
    }
    $rollbackBackupMarker = if ($rollbackFailureState.BackupPath) {
        $rollbackFailureState.BackupPath.TrimEnd('\') + '\classes\marker.txt'
    } else { $null }
    $retainedRestoreDirectories = @(Get-ChildItem -LiteralPath $testRoot -Directory -Filter '.migration-output-restore-*' -Force)
    if (-not ($aggregateFailure -is [AggregateException]) -or $aggregateFailure.InnerExceptions.Count -ne 2 -or
        $aggregateFailure.InnerExceptions[0].Message -ne $installFailure.Message -or
        $aggregateFailure.InnerExceptions[1].Message -ne $rollbackFailure.Message -or
        $rollbackFailureState.RollbackAttempts -lt 1 -or
        -not [IO.Directory]::Exists($rollbackFailureState.BackupPath) -or
        [IO.File]::ReadAllText($rollbackBackupMarker) -ne 'generated output with a retained recovery backup' -or
        [IO.Directory]::Exists($transactionTarget) -or $retainedRestoreDirectories.Count -ne 0) {
        throw 'A failed install and rollback did not retain the original backup, both exceptions, and clean the staging directory.'
    }
    [IO.Directory]::Move($rollbackFailureState.BackupPath, (ConvertTo-MigrationExtendedPath -Path $transactionTarget))

    # Schema 1 recovery snapshots stored directory files under their original relative paths.
    $legacyEntryRoot = Join-Path $legacySnapshotRoot '0000'
    New-Item -ItemType Directory -Path $legacyEntryRoot -Force | Out-Null
    $legacyEntries = @($snapshot.Items[1].Entries | ForEach-Object {
        if ($_.IsDirectory) {
            $legacyDirectory = if ([string]::IsNullOrEmpty($_.RelativePath)) { $legacyEntryRoot } else { Join-Path $legacyEntryRoot $_.RelativePath }
            [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path $legacyDirectory)) | Out-Null
        } else {
            $legacyPayload = Join-Path $legacyEntryRoot $_.RelativePath
            [IO.Directory]::CreateDirectory((ConvertTo-MigrationExtendedPath -Path (Split-Path -Parent $legacyPayload))) | Out-Null
            [IO.File]::Copy((ConvertTo-MigrationExtendedPath -Path (Join-Path $existingDirectory $_.RelativePath)), `
                (ConvertTo-MigrationExtendedPath -Path $legacyPayload), $true)
        }
        [pscustomobject]@{
            RelativePath = $_.RelativePath
            IsDirectory = [bool]$_.IsDirectory
            Attributes = [int]$_.Attributes
            CreationTimeUtc = $_.CreationTimeUtc
            LastWriteTimeUtc = $_.LastWriteTimeUtc
        }
    })
    $legacyManifest = [pscustomobject]@{
        SchemaVersion = 1
        WorkspaceRoot = $testRoot
        SnapshotDirectory = $legacySnapshotRoot
        Items = @([pscustomobject]@{
            RelativePath = 'existing-build'
            Existed = $true
            IsDirectory = $true
            Entries = $legacyEntries
        })
    }
    $legacyManifestPath = Join-Path $legacySnapshotRoot 'manifest.json'
    $legacyLongPayload = Join-Path $legacyEntryRoot $longRelativePath
    if ($legacyLongPayload.Length -le 260) { throw 'The schema-1 snapshot fixture does not exceed the legacy payload path limit.' }
    [IO.File]::WriteAllText($legacyManifestPath, (ConvertTo-Json -InputObject $legacyManifest -Depth 12), [Text.UTF8Encoding]::new($false))
    $legacySnapshot = Read-MigrationOutputSnapshot -ManifestPath $legacyManifestPath -WorkspaceRoot $testRoot `
        -ExpectedRelativePaths @('existing-build')
    if (-not (Test-MigrationOutputSnapshotMatchesWorkspace -Snapshot $legacySnapshot -Verbose)) {
        throw 'A schema-1 recovery snapshot did not match the original nested workspace output.'
    }
    [IO.File]::WriteAllText((ConvertTo-MigrationExtendedPath -Path $longFilePath), 'changed after legacy snapshot')
    Restore-MigrationOutputSnapshot -Snapshot $legacySnapshot
    if (-not (Test-MigrationOutputSnapshotMatchesWorkspace -Snapshot $legacySnapshot)) {
        throw 'A schema-1 recovery snapshot did not restore its nested workspace output.'
    }
    if ([IO.File]::ReadAllText((ConvertTo-MigrationExtendedPath -Path $longFilePath)) -ne 'original long-path build output') {
        throw 'A schema-1 recovery snapshot did not restore the nested long-path file.'
    }

    Write-Host 'Migration output snapshot behavior passed.'
} finally {
    Remove-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $testRoot) -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $snapshotRoot) -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (ConvertTo-MigrationExtendedPath -Path $legacySnapshotRoot) -Recurse -Force -ErrorAction SilentlyContinue
}
