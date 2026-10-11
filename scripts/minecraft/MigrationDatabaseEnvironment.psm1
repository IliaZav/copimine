$script:MigrationDatabaseEnvironmentVariableNames = @(
    'POSTGRES_HOST',
    'POSTGRES_PORT',
    'POSTGRES_DB',
    'POSTGRES_USER',
    'POSTGRES_PASSWORD',
    'POSTGRES_SCHEMA',
    'PGHOST',
    'PGPORT',
    'PGDATABASE',
    'PGUSER',
    'PGPASSWORD',
    'PGSCHEMA'
)

function Assert-MigrationPathNoReparseAncestors {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path
    )

    $fullPath = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetPathRoot($fullPath)
    if ([string]::IsNullOrWhiteSpace($root)) {
        throw 'Migration PostgreSQL paths must be fully qualified.'
    }

    $current = $root
    $segments = $fullPath.Substring($root.Length) -split '[\\/]'
    foreach ($segment in $segments) {
        if ([string]::IsNullOrWhiteSpace($segment)) { continue }
        $current = Join-Path -Path $current -ChildPath $segment
        try {
            $item = Get-Item -LiteralPath $current -Force -ErrorAction Stop
        } catch {
            if ($_.CategoryInfo.Category -eq [System.Management.Automation.ErrorCategory]::ObjectNotFound -or
                $_.Exception -is [IO.FileNotFoundException] -or
                $_.Exception -is [IO.DirectoryNotFoundException]) {
                # A missing component means later components cannot exist yet.
                break
            }
            throw "Unable to verify migration PostgreSQL path component: $current"
        }
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Migration PostgreSQL paths cannot contain reparse points: $current"
        }
    }
}

function ConvertTo-MigrationPostgresPasswordHex {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string]$Value
    )

    if ($Value.IndexOf([char]0) -ge 0) {
        throw 'Migration PostgreSQL passwords cannot contain a NUL character.'
    }
    try {
        $encoding = [Text.UTF8Encoding]::new($false, $true)
        $bytes = $encoding.GetBytes($Value)
    } catch {
        throw 'Migration PostgreSQL password is not valid Unicode.'
    }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

function New-MigrationPostgresPassword {
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = [Array]::CreateInstance([byte], 32)
        $generator.GetBytes($bytes)
        return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
    } finally {
        $generator.Dispose()
    }
}

function Read-MigrationPrivateEnvValue {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Name
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    $file = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'Migration PostgreSQL credential files cannot be reparse points.'
    }
    if ($file.PSIsContainer) {
        throw 'Migration PostgreSQL credential paths must be regular files.'
    }
    $lines = @(Get-Content -LiteralPath $Path -Encoding UTF8 -ErrorAction Stop |
        Where-Object { $_ -match ('^' + [regex]::Escape($Name) + '=') } |
        ForEach-Object { $_ })
    if ($lines.Count -gt 1) {
        throw "Migration PostgreSQL credential file contains a duplicate key: $Name"
    }
    if ($lines.Count -eq 0) { return $null }
    return $lines[0].Substring($Name.Length + 1)
}

function Assert-MigrationDatabaseEnvironment {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string[]]$RequiredSettings
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'Migration database environment file is missing.'
    }
    $file = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'Migration database environment file cannot be a reparse point.'
    }

    $settings = @{}
    foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8 -ErrorAction Stop)) {
        $trimmed = ([string]$line).Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) {
            throw 'Migration database environment file contains a malformed setting.'
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw 'Migration database environment file contains an invalid setting name.'
        }
        if ($settings.ContainsKey($name)) {
            throw "Duplicate migration database environment key: $name"
        }
        $settings[$name] = $value
    }

    foreach ($requiredSetting in $RequiredSettings) {
        $separator = $requiredSetting.IndexOf('=')
        if ($separator -le 0) { throw 'Migration database expected settings are malformed.' }
        $name = $requiredSetting.Substring(0, $separator)
        $expectedValue = $requiredSetting.Substring($separator + 1)
        if (-not $settings.ContainsKey($name)) {
            throw "Migration database environment is missing required key $name."
        }
        if ([string]$settings[$name] -cne $expectedValue) {
            throw "Migration database environment value does not match the isolated runtime for key $name"
        }
    }

    if (-not $settings.ContainsKey('POSTGRES_PASSWORD') -or
        [string]::IsNullOrWhiteSpace([string]$settings['POSTGRES_PASSWORD'])) {
        throw 'The isolated migration database user password is not present in its private environment file.'
    }
    return $settings
}

function Initialize-MigrationPostgresCredentialFiles {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$AdminPath,
        [Parameter(Mandatory)][string]$PluginPath,
        [Parameter(Mandatory)][bool]$DatabaseInitialized,
        [Parameter(Mandatory)][ValidateRange(1, 65535)][int]$Port,
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Schema,
        [Parameter(Mandatory)][string]$Role
    )

    $adminPassword = Read-MigrationPrivateEnvValue -Path $AdminPath -Name 'POSTGRES_SUPERUSER_PASSWORD'
    $pluginPassword = Read-MigrationPrivateEnvValue -Path $PluginPath -Name 'POSTGRES_PASSWORD'
    if (-not $DatabaseInitialized) {
        if ([string]::IsNullOrWhiteSpace($adminPassword)) {
            if (Test-Path -LiteralPath $AdminPath) {
                throw 'The isolated PostgreSQL admin credential file exists but has no usable password.'
            }
            $adminPassword = New-MigrationPostgresPassword
            Write-MigrationPrivateEnvFile -Path $AdminPath -Contents "POSTGRES_SUPERUSER_PASSWORD=$adminPassword`n"
        }
        if ([string]::IsNullOrWhiteSpace($pluginPassword)) {
            if (Test-Path -LiteralPath $PluginPath) {
                throw 'The isolated PostgreSQL plugin credential file exists but has no usable password.'
            }
            $pluginPassword = New-MigrationPostgresPassword
            $pluginContents = @(
                'POSTGRES_HOST=127.0.0.1', "POSTGRES_PORT=$Port", "POSTGRES_DB=$Database", "POSTGRES_SCHEMA=$Schema",
                "POSTGRES_USER=$Role", "POSTGRES_PASSWORD=$pluginPassword",
                "DATABASE_URL=postgresql://${Role}:${pluginPassword}@127.0.0.1:${Port}/${Database}?schema=${Schema}"
            ) -join "`n"
            Write-MigrationPrivateEnvFile -Path $PluginPath -Contents ($pluginContents + "`n")
        }
    }

    if ([string]::IsNullOrWhiteSpace($adminPassword) -or [string]::IsNullOrWhiteSpace($pluginPassword)) {
        throw 'The isolated PostgreSQL credential files are missing; the shared database will not be used as a fallback.'
    }
    return [PSCustomObject]@{
        AdminPassword = $adminPassword
        PluginPassword = $pluginPassword
    }
}

function Write-MigrationPrivateEnvFile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Contents
    )

    if (Test-Path -LiteralPath $Path) {
        $existing = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
        if ($existing.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Migration PostgreSQL credential destinations cannot be reparse points.'
        }
        throw 'Migration PostgreSQL credential destinations must not already exist.'
    }

    $currentUserSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $security = [Security.AccessControl.FileSecurity]::new()
    $security.SetAccessRuleProtection($true, $false)
    foreach ($sidText in @($currentUserSid.Value, 'S-1-5-18', 'S-1-5-32-544')) {
        $sid = [Security.Principal.SecurityIdentifier]::new($sidText)
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $sid,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.AccessControlType]::Allow
        )
        [void]$security.AddAccessRule($rule)
    }

    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Contents)
    $stream = $null
    $createdByThisCall = $false
    try {
        $stream = [IO.FileStream]::new(
            $Path,
            [IO.FileMode]::CreateNew,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [IO.FileShare]::None,
            4096,
            [IO.FileOptions]::None,
            $security
        )
        $createdByThisCall = $true
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    } catch {
        if ($null -ne $stream) {
            $stream.Dispose()
            $stream = $null
        }
        if ($createdByThisCall -and (Test-Path -LiteralPath $Path)) {
            $failedFile = Get-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
            if ($null -ne $failedFile -and -not ($failedFile.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
            }
        }
        throw
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
}

function Remove-MigrationPrivateFileIfOwned {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][bool]$CreatedByThisCall
    )

    if (-not $CreatedByThisCall) { return }
    try {
        $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    } catch {
        if ($_.CategoryInfo.Category -eq [System.Management.Automation.ErrorCategory]::ObjectNotFound -or
            $_.Exception -is [IO.FileNotFoundException] -or
            $_.Exception -is [IO.DirectoryNotFoundException]) {
            return
        }
        throw 'Unable to verify the owned migration PostgreSQL temporary file before cleanup.'
    }
    if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'The owned migration PostgreSQL temporary file changed to an unsafe filesystem object before cleanup.'
    }
    Remove-Item -LiteralPath $Path -Force -ErrorAction Stop
}

function Restore-MigrationDatabaseOverrides {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [System.Collections.IDictionary]$Values
    )

    foreach ($name in $Values.Keys) {
        [Environment]::SetEnvironmentVariable(
            [string]$name,
            [string]$Values[$name],
            [EnvironmentVariableTarget]::Process
        )
    }
}

function Clear-MigrationDatabaseOverrides {
    [CmdletBinding()]
    param()

    $previousValues = [ordered]@{}
    try {
        foreach ($name in $script:MigrationDatabaseEnvironmentVariableNames) {
            $value = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)
            if ($null -ne $value) {
                $previousValues[$name] = $value
                Remove-Item -LiteralPath ("Env:{0}" -f $name) -ErrorAction Stop
            }
        }
    }
    catch {
        Restore-MigrationDatabaseOverrides -Values $previousValues
        throw
    }

    return $previousValues
}

Export-ModuleMember -Function Clear-MigrationDatabaseOverrides, Restore-MigrationDatabaseOverrides, `
    Assert-MigrationDatabaseEnvironment, ConvertTo-MigrationPostgresPasswordHex, `
    Assert-MigrationPathNoReparseAncestors, `
    Initialize-MigrationPostgresCredentialFiles, New-MigrationPostgresPassword, `
    Read-MigrationPrivateEnvValue, Remove-MigrationPrivateFileIfOwned, Write-MigrationPrivateEnvFile
