[CmdletBinding()]
param(
    [ValidateSet('all', 'managed-plus-extra', 'no-copimine-client', 'no-mods', 'copimine-client-plus-fabric-api')]
    [string] $Scenario = 'all',
    [string]$InstanceRoot = '',
    [string] $ServerHost = '127.0.0.1',
    [int] $ServerPort = 25567,
    [string] $ServerLog = 'D:\Desktop\Copimine\copimine-main\.worktrees\site-launcher-audit\artifacts\local-validation\paper\logs\latest.log',
    [int] $WaitSeconds = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedValidationRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\artifacts\local-validation'))
$resolvedServerLog = [IO.Path]::GetFullPath($ServerLog)
if ([string]::IsNullOrWhiteSpace($InstanceRoot)) {
    $candidateRoots = @(Get-ChildItem -LiteralPath $resolvedValidationRoot -Directory -Filter 'msi-install-*' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        ForEach-Object { Join-Path $_.FullName 'CopiMine Launcher\Minecraft' } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Container })
    if ($candidateRoots.Count -eq 0) {
        throw "No disposable Launcher install fixture was found below $resolvedValidationRoot. Pass -InstanceRoot explicitly."
    }
    $InstanceRoot = $candidateRoots[0]
}
$resolvedInstanceRoot = [IO.Path]::GetFullPath($InstanceRoot)
if (-not $resolvedInstanceRoot.StartsWith($resolvedValidationRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "The direct matrix may only mutate an instance below $resolvedValidationRoot."
}
if (-not $resolvedServerLog.StartsWith($resolvedValidationRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "The direct matrix may only read a server log below $resolvedValidationRoot."
}

$modsDirectory = Join-Path $resolvedInstanceRoot 'mods'
$launchLog = Join-Path $resolvedInstanceRoot 'logs\launcher-process.log'
$copimineClientName = 'CopiMineClient-0.1.0.jar'
foreach ($path in @($resolvedInstanceRoot, $modsDirectory)) {
    if (-not (Test-Path -LiteralPath $path -PathType Container)) {
        throw "Required direct matrix directory is missing: $path"
    }
}
foreach ($path in @($launchLog, $resolvedServerLog)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required direct matrix file is missing: $path"
    }
}

if (-not ('CopiMineDirectCommandLine.Native' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

namespace CopiMineDirectCommandLine
{
    public static class Native
    {
        [DllImport("shell32.dll", SetLastError = true)]
        public static extern IntPtr CommandLineToArgvW(
            [MarshalAs(UnmanagedType.LPWStr)] string commandLine,
            out int argc);

        [DllImport("kernel32.dll")]
        public static extern IntPtr LocalFree(IntPtr handle);
    }
}
'@
}

function ConvertFrom-WindowsCommandLine([string] $CommandLine) {
    $argc = 0
    $argv = [CopiMineDirectCommandLine.Native]::CommandLineToArgvW($CommandLine, [ref]$argc)
    if ($argv -eq [IntPtr]::Zero -or $argc -le 0) {
        throw 'The recorded Minecraft command line could not be parsed.'
    }

    try {
        $values = [System.Collections.Generic.List[string]]::new()
        for ($index = 0; $index -lt $argc; $index++) {
            $pointer = [Runtime.InteropServices.Marshal]::ReadIntPtr($argv, $index * [IntPtr]::Size)
            $values.Add([Runtime.InteropServices.Marshal]::PtrToStringUni($pointer))
        }
        return @($values)
    }
    finally {
        [void][CopiMineDirectCommandLine.Native]::LocalFree($argv)
    }
}

function Read-LogTail([string] $Path, [long] $StartingLength) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return ''
    }

    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        $stream.Position = if ($StartingLength -le $stream.Length) { $StartingLength } else { 0 }
        $reader = [IO.StreamReader]::new($stream)
        try {
            return $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

$commandLine = Get-Content -LiteralPath $launchLog | Where-Object { $_ -match ' COMMAND file=' } | Select-Object -Last 1
if ([string]::IsNullOrWhiteSpace($commandLine) -or $commandLine -notmatch 'COMMAND file=(?<file>.+?) cwd=(?<cwd>.+?) args=(?<args>.*)$') {
    throw "A successful recorded Minecraft command is required in $launchLog."
}

$recordedJavaPath = [string]$Matches['file']
$recordedWorkingDirectory = [string]$Matches['cwd']
$directArguments = @(ConvertFrom-WindowsCommandLine ([string]$Matches['args']))
if (-not (Test-Path -LiteralPath $recordedJavaPath -PathType Leaf)) {
    throw "The recorded Java runtime is missing: $recordedJavaPath"
}
if (-not (Test-Path -LiteralPath $recordedWorkingDirectory -PathType Container)) {
    throw "The recorded Minecraft working directory is missing: $recordedWorkingDirectory"
}

$quickPlayIndex = [Array]::IndexOf($directArguments, '--quickPlayMultiplayer')
if ($quickPlayIndex -lt 0 -or $quickPlayIndex + 1 -ge $directArguments.Count) {
    throw 'The recorded Minecraft command has no quick-play server target.'
}
$directArgumentLine = [string]$Matches['args']
$directTarget = "$ServerHost`:$ServerPort"
$directArgumentLine = [regex]::Replace(
    $directArgumentLine,
    '(--quickPlayMultiplayer\s+)(?:"[^"]*"|\S+)',
    { param($match) $match.Groups[1].Value + $directTarget })

$initialFiles = @(Get-ChildItem -LiteralPath $modsDirectory -File | Sort-Object Name)
if (-not ($initialFiles.Name -contains $copimineClientName)) {
    throw "The baseline instance does not contain $copimineClientName."
}

$scenarios = switch ($Scenario) {
    'all' {
        @(
            @{ Name = 'managed-plus-extra'; ExpectedGate = 'ACCEPT'; Keep = @($initialFiles.Name) },
            @{ Name = 'no-copimine-client'; ExpectedGate = 'REJECT'; Keep = @($initialFiles.Name | Where-Object { $_ -ne $copimineClientName }) },
            @{ Name = 'no-mods'; ExpectedGate = 'REJECT'; Keep = @() },
            @{ Name = 'copimine-client-plus-fabric-api'; ExpectedGate = 'ACCEPT'; Keep = @($copimineClientName, 'fabric-api-0.116.11+1.21.1.jar') }
        )
    }
    'managed-plus-extra' { @(@{ Name = 'managed-plus-extra'; ExpectedGate = 'ACCEPT'; Keep = @($initialFiles.Name) }) }
    'no-copimine-client' { @(@{ Name = 'no-copimine-client'; ExpectedGate = 'REJECT'; Keep = @($initialFiles.Name | Where-Object { $_ -ne $copimineClientName }) }) }
    'no-mods' { @(@{ Name = 'no-mods'; ExpectedGate = 'REJECT'; Keep = @() }) }
    'copimine-client-plus-fabric-api' { @(@{ Name = 'copimine-client-plus-fabric-api'; ExpectedGate = 'ACCEPT'; Keep = @($copimineClientName, 'fabric-api-0.116.11+1.21.1.jar') }) }
}

function Invoke-DirectScenario([hashtable] $Definition) {
    $scenarioName = [string]$Definition.Name
    $expectedGate = [string]$Definition.ExpectedGate
    $keep = @($Definition.Keep)
    $quarantine = Join-Path $resolvedInstanceRoot ('.validation-quarantine\direct-' + $scenarioName)
    New-Item -ItemType Directory -Path $quarantine -Force | Out-Null
    $baselineNames = @($initialFiles.Name)
    $process = $null

    try {
        foreach ($file in @(Get-ChildItem -LiteralPath $modsDirectory -File)) {
            if ($keep -notcontains $file.Name) {
                Move-Item -LiteralPath $file.FullName -Destination $quarantine
            }
        }

        $currentNames = @(Get-ChildItem -LiteralPath $modsDirectory -File | Select-Object -ExpandProperty Name | Sort-Object)
        $expectedNames = @($keep | Sort-Object)
        if (($currentNames -join "`n") -ne ($expectedNames -join "`n")) {
            throw "Scenario $scenarioName produced an unexpected mod profile."
        }

        $serverLength = (Get-Item -LiteralPath $resolvedServerLog).Length
        $stdoutPath = Join-Path $resolvedInstanceRoot ('.validation-direct-' + $scenarioName + '.stdout.log')
        $stderrPath = Join-Path $resolvedInstanceRoot ('.validation-direct-' + $scenarioName + '.stderr.log')
        $process = Start-Process -FilePath $recordedJavaPath `
            -ArgumentList $directArgumentLine `
            -WorkingDirectory $recordedWorkingDirectory `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath `
            -WindowStyle Hidden `
            -PassThru

        $deadline = [DateTimeOffset]::UtcNow.AddSeconds([Math]::Max(30, $WaitSeconds))
        $evidence = ''
        while ([DateTimeOffset]::UtcNow -lt $deadline) {
            $evidence = Read-LogTail $resolvedServerLog $serverLength
            $gateSeen = ($expectedGate -eq 'ACCEPT' -and $evidence.Contains('CLIENT_GATE_ACCEPT player=SmokePlayer')) -or ($expectedGate -eq 'REJECT' -and $evidence.Contains('CLIENT_GATE_REJECT'))
            if ($gateSeen) {
                break
            }
            Start-Sleep -Milliseconds 250
        }

        if ($expectedGate -eq 'ACCEPT') {
            if (-not $evidence.Contains('CLIENT_GATE_ACCEPT player=SmokePlayer')) {
                throw "Direct scenario $scenarioName did not produce CLIENT_GATE_ACCEPT."
            }
        }
        elseif (-not $evidence.Contains('CLIENT_GATE_REJECT')) {
            throw "Direct scenario $scenarioName did not produce CLIENT_GATE_REJECT."
        }

        Write-Host "direct_scenario=$scenarioName expected=$expectedGate result=PASS" -ForegroundColor Green
    }
    finally {
        if ($null -ne $process) {
            try {
                if (-not $process.HasExited) {
                    $process.Kill()
                    $process.WaitForExit()
                }
            }
            catch {
                # The client can close itself immediately after the server kick.
            }
            $process.Dispose()
        }

        Remove-Item -LiteralPath (Join-Path $resolvedInstanceRoot ('.validation-direct-' + $scenarioName + '.stdout.log')) -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $resolvedInstanceRoot ('.validation-direct-' + $scenarioName + '.stderr.log')) -Force -ErrorAction SilentlyContinue

        foreach ($file in @(Get-ChildItem -LiteralPath $quarantine -File -ErrorAction SilentlyContinue)) {
            Move-Item -LiteralPath $file.FullName -Destination $modsDirectory
        }
        $restoredNames = @(Get-ChildItem -LiteralPath $modsDirectory -File | Select-Object -ExpandProperty Name | Sort-Object)
        if (($restoredNames -join "`n") -ne (($baselineNames | Sort-Object) -join "`n")) {
            throw "Direct scenario $scenarioName did not restore the baseline mod set."
        }
        Remove-Item -LiteralPath $quarantine -Force -ErrorAction SilentlyContinue
    }
}

foreach ($definition in $scenarios) {
    Invoke-DirectScenario $definition
}

Write-Host 'CLIENT_GATE_MATRIX_DIRECT=PASS' -ForegroundColor Green
exit 0
