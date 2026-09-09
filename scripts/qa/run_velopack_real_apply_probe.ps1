[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $TargetRoot
)

$ErrorActionPreference = 'Stop'
$target = (Resolve-Path -LiteralPath $TargetRoot -ErrorAction Stop).Path
$currentDir = [string](Join-Path $target 'current')
$packagesDir = [string](Join-Path $target 'packages')
$updateExe = [string](Join-Path $target 'Update.exe')
$processPath = [string](Join-Path $currentDir 'CopiMineLauncher.App.exe')
$velopackPath = Join-Path $env:USERPROFILE '.nuget\packages\velopack\1.2.0\lib\net10.0\Velopack.dll'
$assembly = [Reflection.Assembly]::LoadFrom($velopackPath)
$locatorType = $assembly.GetType('Velopack.Locators.TestVelopackLocator')
$locatorCtor = $locatorType.GetConstructors() | Where-Object { $_.GetParameters().Count -eq 10 }
$locator = $locatorCtor.Invoke([object[]]@(
    'CopiMineLauncher',
    '1.0.7',
    $packagesDir,
    $currentDir,
    $target,
    $updateExe,
    'stable',
    $null,
    $null,
    $processPath))

$optionsType = $assembly.GetType('Velopack.UpdateOptions')
$options = [Activator]::CreateInstance($optionsType)
$optionsType.GetProperty('ExplicitChannel').SetValue($options, 'stable')
$managerType = $assembly.GetType('Velopack.UpdateManager')
$managerCtor = $managerType.GetConstructor([Type[]]@(
    [string],
    $optionsType,
    $assembly.GetType('Velopack.Locators.IVelopackLocator')))
$manager = $managerCtor.Invoke([object[]]@('https://copimine.ru/downloads/launcher/', $options, $locator))
$info = $manager.CheckForUpdatesAsync().GetAwaiter().GetResult()
if ($null -eq $info) {
    throw 'Velopack feed did not return an update candidate for the disposable 1.0.7 locator.'
}
$asset = $info.TargetFullRelease
if ($null -eq $asset) {
    throw 'Velopack feed returned an update without a full-release asset.'
}

$applyType = $assembly.GetType('Velopack.UpdateExe')
$applyMethod = $applyType.GetMethod('Apply', [Type[]]@(
    $assembly.GetType('Velopack.Locators.IVelopackLocator'),
    $assembly.GetType('Velopack.VelopackAsset'),
    [bool],
    [uint32],
    [bool],
    [string[]]))
if ($null -eq $applyMethod) {
    throw 'Velopack UpdateExe.Apply overload was not found.'
}
$applyMethod.Invoke($null, [object[]]@($locator, $asset, $true, [uint32]0, $false, [string[]]@())) | Out-Null

[pscustomobject]@{
    TargetRoot = $target
    BeforeVersion = $locator.CurrentlyInstalledVersion
    TargetVersion = $asset.Version.ToString()
    Package = $asset.FileName
    PackageBytes = $asset.Size
    PackageSha256 = $asset.SHA256.ToLowerInvariant()
    ApplyDispatched = $true
} | Format-List
