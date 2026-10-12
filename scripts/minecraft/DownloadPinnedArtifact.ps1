function Save-TextFileAtomically {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Content,
        [Parameter(Mandatory)][Text.Encoding]$Encoding,
        [string]$StopAt
    )

    $destination = [IO.Path]::GetFullPath($Path)
    $parent = [IO.Path]::GetDirectoryName($destination)
    $temporary = Join-Path $parent (([IO.Path]::GetFileName($destination)) + '.candidate.' + [Guid]::NewGuid().ToString('N') + '.tmp')
    Assert-AtomicFileDestination -Path $destination -Description 'text file' -StopAt $StopAt
    try {
        [IO.File]::WriteAllText($temporary, $Content, $Encoding)
        Assert-AtomicFileDestination -Path $destination -Description 'text file' -StopAt $StopAt
        if (Test-Path -LiteralPath $destination) {
            $backup = $temporary + '.previous'
            [IO.File]::Replace($temporary, $destination, $backup)
            if (Test-Path -LiteralPath $backup -PathType Leaf) {
                Remove-Item -LiteralPath $backup -Force
            }
        } else {
            [IO.File]::Move($temporary, $destination)
        }
    } finally {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

function Assert-DirectoryPathNoReparse {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [string]$StopAt
    )

    $current = [IO.Path]::GetFullPath($Path)
    $boundary = if ([string]::IsNullOrWhiteSpace($StopAt)) { $null } else { [IO.Path]::GetFullPath($StopAt) }
    if ($boundary) {
        $separator = [IO.Path]::DirectorySeparatorChar
        $boundaryPrefix = $boundary.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
        $pathPrefix = $current.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
        if ($pathPrefix -ne $boundaryPrefix -and
            -not $pathPrefix.StartsWith($boundaryPrefix + $separator, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing directory path outside its allowed root'
        }
    }
    while ($current) {
        $existing = Get-Item -LiteralPath $current -Force -ErrorAction SilentlyContinue
        if ($existing -and (-not $existing.PSIsContainer -or
            ($existing.Attributes -band [IO.FileAttributes]::ReparsePoint))) {
            throw 'Refusing unsafe directory path'
        }
        if ($boundary -and [string]::Equals($current.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar),
                $boundary.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar),
                [StringComparison]::OrdinalIgnoreCase)) { break }
        $parent = [IO.Directory]::GetParent($current)
        if (-not $parent) { break }
        $current = $parent.FullName
    }
}

function Assert-AtomicFileDestination {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Description,
        [string]$StopAt
    )

    $destination = [IO.Path]::GetFullPath($Path)
    Assert-DirectoryPathNoReparse -Path ([IO.Path]::GetDirectoryName($destination)) -StopAt $StopAt
    $existing = Get-Item -LiteralPath $destination -Force -ErrorAction SilentlyContinue
    if ($existing -and ($existing.PSIsContainer -or
        ($existing.Attributes -band [IO.FileAttributes]::ReparsePoint))) {
        throw ('Refusing unsafe ' + $Description + ' destination')
    }
}

function Assert-ProfileDirectoryDestination {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Description,
        [string]$StopAt
    )

    $destination = [IO.Path]::GetFullPath($Path)
    $parent = if ($StopAt -and [string]::Equals($destination.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar),
            ([IO.Path]::GetFullPath($StopAt)).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar),
            [StringComparison]::OrdinalIgnoreCase)) {
        $destination
    } else {
        [IO.Path]::GetDirectoryName($destination)
    }
    Assert-DirectoryPathNoReparse -Path $parent -StopAt $StopAt
    $existing = Get-Item -LiteralPath $destination -Force -ErrorAction SilentlyContinue
    if ($existing -and (-not $existing.PSIsContainer -or
        ($existing.Attributes -band [IO.FileAttributes]::ReparsePoint))) {
        throw ('Refusing unsafe ' + $Description + ' destination')
    }
}

function Assert-DirectoryTreeNoReparse {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Description,
        [string]$StopAt
    )

    Assert-ProfileDirectoryDestination -Path $Path -Description $Description -StopAt $StopAt
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) { return }
    $pending = [Collections.Generic.Stack[string]]::new()
    $pending.Push([IO.Path]::GetFullPath($Path))
    while ($pending.Count -gt 0) {
        $current = $pending.Pop()
        foreach ($item in Get-ChildItem -LiteralPath $current -Force -ErrorAction Stop) {
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw ('Refusing reparse point in ' + $Description)
            }
            if ($item.PSIsContainer) { $pending.Push($item.FullName) }
        }
    }
}

function Reset-GeneratedDirectory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Description,
        [string]$StopAt
    )

    $destination = [IO.Path]::GetFullPath($Path)
    Assert-ProfileDirectoryDestination -Path $destination -Description $Description -StopAt $StopAt
    if (Test-Path -LiteralPath $destination -PathType Container) {
        Assert-DirectoryTreeNoReparse -Path $destination -Description $Description -StopAt $StopAt
        foreach ($item in Get-ChildItem -LiteralPath $destination -Force -ErrorAction Stop) {
            # Removing a hard-link entry does not modify its other names. The
            # fresh tree prevents generators from truncating attacker-planted links.
            Remove-Item -LiteralPath $item.FullName -Force -Recurse -ErrorAction Stop
        }
    } else {
        New-Item -ItemType Directory -Path $destination -Force -ErrorAction Stop | Out-Null
    }
    Assert-DirectoryTreeNoReparse -Path $destination -Description $Description -StopAt $StopAt
}

function Save-PinnedArtifact {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][string]$Destination,
        [Parameter(Mandatory)][ValidateSet('SHA1', 'SHA256', 'SHA512')][string]$Algorithm,
        [Parameter(Mandatory)][string]$ExpectedHash,
        [Parameter(Mandatory)][long]$ExpectedSize,
        [Parameter(Mandatory)][long]$MaximumBytes,
        [string]$StopAt
    )

    $hexLength = switch ($Algorithm) { 'SHA1' { 40 } 'SHA256' { 64 } 'SHA512' { 128 } }
    if ($ExpectedHash -notmatch ('^[0-9a-f]{' + $hexLength + '}$')) { throw 'Pinned download digest is invalid' }
    if ($ExpectedSize -lt 0 -or $MaximumBytes -le 0 -or $ExpectedSize -gt $MaximumBytes) {
        throw 'Pinned download size limit is invalid'
    }

    $destinationPath = [IO.Path]::GetFullPath($Destination)
    Assert-AtomicFileDestination -Path $destinationPath -Description 'pinned artifact' -StopAt $StopAt
    $parent = [IO.Path]::GetDirectoryName($destinationPath)
    Assert-DirectoryPathNoReparse -Path $parent -StopAt $StopAt
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Assert-DirectoryPathNoReparse -Path $parent -StopAt $StopAt
    $temporary = $destinationPath + '.download'
    Assert-AtomicFileDestination -Path $temporary -Description 'pinned download temporary file' -StopAt $StopAt
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }

    $request = $null
    $response = $null
    $inputStream = $null
    $outputStream = $null
    $hasher = $null
    try {
        $request = [Net.HttpWebRequest]::Create([Uri]$Uri)
        $request.Method = 'GET'
        $request.UserAgent = 'CopiMineMigration/1.0'
        $request.Timeout = 120000
        $request.ReadWriteTimeout = 120000
        $response = $request.GetResponse()
        if ($response.ContentLength -gt $MaximumBytes -or
            ($ExpectedSize -gt 0 -and $response.ContentLength -ge 0 -and $response.ContentLength -ne $ExpectedSize)) {
            throw 'Pinned download size mismatch'
        }
        $inputStream = $response.GetResponseStream()
        $outputStream = [IO.File]::Open($temporary, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $buffer = New-Object byte[] 65536
        $total = [long]0
        while (($read = $inputStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $nextTotal = $total + $read
            if ($nextTotal -gt $MaximumBytes -or ($ExpectedSize -gt 0 -and $nextTotal -gt $ExpectedSize)) {
                throw 'Pinned download exceeded its size limit'
            }
            $outputStream.Write($buffer, 0, $read)
            $total = $nextTotal
        }
        $outputStream.Flush()
        $outputStream.Dispose()
        $outputStream = $null
        $inputStream.Dispose()
        $inputStream = $null
        $response.Dispose()
        $response = $null

        if ($ExpectedSize -gt 0 -and $total -ne $ExpectedSize) { throw 'Pinned download size mismatch' }
        switch ($Algorithm) {
            'SHA1' { $hasher = [Security.Cryptography.SHA1]::Create() }
            'SHA256' { $hasher = [Security.Cryptography.SHA256]::Create() }
            'SHA512' { $hasher = [Security.Cryptography.SHA512]::Create() }
        }
        $hashStream = [IO.File]::OpenRead($temporary)
        try { $actualHash = ([BitConverter]::ToString($hasher.ComputeHash($hashStream)) -replace '-', '').ToLowerInvariant() }
        finally { $hashStream.Dispose(); $hasher.Dispose(); $hasher = $null }
        if ($actualHash -ne $ExpectedHash) { throw 'Pinned download digest mismatch' }

        Assert-AtomicFileDestination -Path $destinationPath -Description 'pinned artifact' -StopAt $StopAt
        if (Test-Path -LiteralPath $destinationPath -PathType Leaf) {
            $backup = $destinationPath + '.previous'
            Assert-AtomicFileDestination -Path $backup -Description 'pinned artifact backup' -StopAt $StopAt
            if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup -Force }
            [IO.File]::Replace($temporary, $destinationPath, $backup)
            if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup -Force }
        } elseif (Test-Path -LiteralPath $destinationPath) {
            throw 'Pinned download destination is not a regular file'
        } else {
            [IO.File]::Move($temporary, $destinationPath)
        }
    } finally {
        if ($outputStream) { $outputStream.Dispose() }
        if ($inputStream) { $inputStream.Dispose() }
        if ($response) { $response.Dispose() }
        if ($hasher) { $hasher.Dispose() }
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Copy-PinnedArtifactAtomically {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Destination,
        [Parameter(Mandatory)][ValidateSet('SHA1', 'SHA256', 'SHA512')][string]$Algorithm,
        [Parameter(Mandatory)][string]$ExpectedHash,
        [Parameter(Mandatory)][long]$ExpectedSize,
        [string]$StopAt
    )

    $hexLength = switch ($Algorithm) { 'SHA1' { 40 } 'SHA256' { 64 } 'SHA512' { 128 } }
    if ($ExpectedHash -notmatch ('^[0-9a-f]{' + $hexLength + '}$') -or $ExpectedSize -le 0) {
        throw 'Pinned copy digest or size is invalid'
    }
    $sourcePath = [IO.Path]::GetFullPath($Source)
    $destinationPath = [IO.Path]::GetFullPath($Destination)
    if ([string]::Equals($sourcePath, $destinationPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Pinned copy source and destination must differ'
    }
    Assert-AtomicFileDestination -Path $sourcePath -Description 'pinned copy source' -StopAt $StopAt
    Assert-AtomicFileDestination -Path $destinationPath -Description 'pinned copy destination' -StopAt $StopAt
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { throw 'Pinned copy source is missing' }
    if ((Get-Item -LiteralPath $sourcePath -Force).Length -ne $ExpectedSize) { throw 'Pinned copy source size mismatch' }

    $parent = [IO.Path]::GetDirectoryName($destinationPath)
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Assert-DirectoryPathNoReparse -Path $parent -StopAt $StopAt
    $temporary = $destinationPath + '.candidate.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    $backup = $null
    $published = $false
    try {
        [IO.File]::Copy($sourcePath, $temporary, $false)
        if ((Get-Item -LiteralPath $temporary -Force).Length -ne $ExpectedSize -or
            (Get-FileHash -LiteralPath $temporary -Algorithm $Algorithm).Hash.ToLowerInvariant() -ne $ExpectedHash) {
            throw 'Pinned copy candidate digest or size mismatch'
        }
        Assert-AtomicFileDestination -Path $destinationPath -Description 'pinned copy destination' -StopAt $StopAt
        if (Test-Path -LiteralPath $destinationPath -PathType Leaf) {
            $backup = $destinationPath + '.previous.' + [Guid]::NewGuid().ToString('N')
            Assert-AtomicFileDestination -Path $backup -Description 'pinned copy backup' -StopAt $StopAt
            [IO.File]::Replace($temporary, $destinationPath, $backup)
        } elseif (Test-Path -LiteralPath $destinationPath) {
            throw 'Pinned copy destination is not a regular file'
        } else {
            [IO.File]::Move($temporary, $destinationPath)
        }
        $published = $true
        if ((Get-Item -LiteralPath $destinationPath -Force).Length -ne $ExpectedSize -or
            (Get-FileHash -LiteralPath $destinationPath -Algorithm $Algorithm).Hash.ToLowerInvariant() -ne $ExpectedHash) {
            throw 'Published pinned artifact digest or size mismatch'
        }
        return $backup
    } catch {
        if ($backup -and (Test-Path -LiteralPath $backup -PathType Leaf)) {
            [IO.File]::Replace($backup, $destinationPath, [NullString]::Value)
        } elseif ($published -and (Test-Path -LiteralPath $destinationPath -PathType Leaf)) {
            Remove-Item -LiteralPath $destinationPath -Force -ErrorAction Stop
        }
        throw
    } finally {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Install-PinnedToolArchive {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Archive,
        [Parameter(Mandatory)][ValidateSet('SHA256', 'SHA512')][string]$Algorithm,
        [Parameter(Mandatory)][string]$ExpectedHash,
        [Parameter(Mandatory)][long]$ExpectedSize,
        [Parameter(Mandatory)][string]$DestinationDirectory,
        [Parameter(Mandatory)][string]$ExecutableRelativePath,
        [string]$StopAt
    )

    $archivePath = [IO.Path]::GetFullPath($Archive)
    Assert-AtomicFileDestination -Path $archivePath -Description 'pinned tool archive' -StopAt $StopAt
    if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) { throw 'Pinned tool archive is missing' }
    $archiveItem = Get-Item -LiteralPath $archivePath
    if ($archiveItem.Length -ne $ExpectedSize) { throw 'Pinned tool archive size mismatch' }
    $archiveHasher = if ($Algorithm -eq 'SHA256') { [Security.Cryptography.SHA256]::Create() } else { [Security.Cryptography.SHA512]::Create() }
    $archiveStream = [IO.File]::OpenRead($archivePath)
    try { $archiveHash = ([BitConverter]::ToString($archiveHasher.ComputeHash($archiveStream)) -replace '-', '').ToLowerInvariant() }
    finally { $archiveStream.Dispose(); $archiveHasher.Dispose() }
    if ($archiveHash -ne $ExpectedHash) {
        throw 'Pinned tool archive digest mismatch'
    }

    $destinationPath = [IO.Path]::GetFullPath($DestinationDirectory)
    $parent = [IO.Path]::GetDirectoryName($destinationPath)
    $name = [IO.Path]::GetFileName($destinationPath)
    $relativeParts = $ExecutableRelativePath.Replace('/', '\').Split('\')
    if (-not $name -or [IO.Path]::IsPathRooted($ExecutableRelativePath) -or
        ($relativeParts | Where-Object { -not $_ -or $_ -eq '.' -or $_ -eq '..' }).Count -gt 0) {
        throw 'Pinned tool extraction paths must be a direct directory and a relative executable path'
    }
    Assert-DirectoryPathNoReparse -Path $parent -StopAt $StopAt
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    Assert-DirectoryPathNoReparse -Path $parent -StopAt $StopAt
    $scratch = Join-Path $parent ('.' + $name + '.extract-' + [Guid]::NewGuid().ToString('N'))
    $backup = Join-Path $parent ('.' + $name + '.previous-' + [Guid]::NewGuid().ToString('N'))
    $oldMoved = $false
    try {
        New-Item -ItemType Directory -Path $scratch -ErrorAction Stop | Out-Null
        Expand-Archive -LiteralPath $archivePath -DestinationPath $scratch -Force
        $candidate = Join-Path $scratch $name
        $candidateExecutable = Join-Path $candidate $ExecutableRelativePath
        if (-not (Test-Path -LiteralPath $candidate -PathType Container) -or
            -not (Test-Path -LiteralPath $candidateExecutable -PathType Leaf)) {
            throw 'Pinned archive does not contain the expected tool distribution'
        }
        $candidateItem = Get-Item -LiteralPath $candidateExecutable -Force
        if ($candidateItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Pinned tool executable cannot be a reparse point'
        }
        $existing = Get-Item -LiteralPath $destinationPath -Force -ErrorAction SilentlyContinue
        if ($existing) {
            if (-not $existing.PSIsContainer -or ($existing.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                throw 'Existing pinned tool directory is not a regular directory'
            }
            Move-Item -LiteralPath $destinationPath -Destination $backup -ErrorAction Stop
            $oldMoved = $true
        }
        try {
            Assert-DirectoryPathNoReparse -Path $parent -StopAt $StopAt
            Move-Item -LiteralPath $candidate -Destination $destinationPath -ErrorAction Stop
        } catch {
            if ($oldMoved -and -not (Test-Path -LiteralPath $destinationPath)) {
                Move-Item -LiteralPath $backup -Destination $destinationPath -ErrorAction Stop
                $oldMoved = $false
            }
            throw
        }
        if ($oldMoved) {
            Remove-Item -LiteralPath $backup -Recurse -Force -ErrorAction Stop
            $oldMoved = $false
        }
    } finally {
        if ($oldMoved -and -not (Test-Path -LiteralPath $destinationPath) -and (Test-Path -LiteralPath $backup)) {
            Move-Item -LiteralPath $backup -Destination $destinationPath -ErrorAction SilentlyContinue
        }
        if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue }
    }
}
