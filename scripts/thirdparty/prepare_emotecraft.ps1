param(
    [string]$ClientJar = "",
    [string]$ServerJar = "",
    [string]$FabricApiJar = "",
    [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if (-not $ProjectRoot) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
}

$clientDir = Join-Path $ProjectRoot "thirdparty\client-mods"
$serverDir = Join-Path $ProjectRoot "thirdparty\server-plugins"
New-Item -ItemType Directory -Force -Path $clientDir, $serverDir | Out-Null

$defaults = @{
    ClientJar = @{
        Url = "https://cdn.modrinth.com/data/pZ2wrerK/versions/daqt5qcK/emotecraft-for-MC1.21.1-2.4.12-fabric.jar"
        Name = "emotecraft-for-MC1.21.1-2.4.12-fabric.jar"
        Sha256 = "633a77711f650dbf0bd071a43c086b7946d6e117b28ac1a414d038ea7b339f7c"
    }
    ServerJar = @{
        Url = "https://cdn.modrinth.com/data/pZ2wrerK/versions/DVp3FUqR/emotecraft-2.4.12-bukkit.jar"
        Name = "emotecraft-2.4.12-bukkit.jar"
        Sha256 = "b8defd7f557262db50b9d0c411544d09ced5bb7f10703e1ffa05f4b38c851e23"
    }
    FabricApiJar = @{
        Url = "https://cdn.modrinth.com/data/P7dR8mSH/versions/IpaMcBLh/fabric-api-0.116.11%2B1.21.1.jar"
        Name = "fabric-api-0.116.11+1.21.1.jar"
        Sha256 = "b791de6f6dce9c58d4ea2af6c713bbcc6dc64d0a5995a8bad6f225ee58cf17d2"
    }
}

function Stage-Jar([string]$pathValue, [string]$targetDir, [hashtable]$meta) {
    $target = Join-Path $targetDir $meta.Name
    if ($pathValue) {
        if (-not (Test-Path -LiteralPath $pathValue)) {
            throw "File not found: $pathValue"
        }
        if ([IO.Path]::GetExtension($pathValue) -ne ".jar") {
            throw "A .jar file is required: $pathValue"
        }
        Copy-Item -LiteralPath $pathValue -Destination $target -Force
    } else {
        Invoke-WebRequest $meta.Url -OutFile $target
    }
    $sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToLowerInvariant()
    if ($sha256 -ne $meta.Sha256) {
        Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
        throw "SHA-256 mismatch for $($meta.Name)"
    }
    return @{ path = $target; sha256 = $sha256; url = $meta.Url }
}

$client = Stage-Jar $ClientJar $clientDir $defaults.ClientJar
$server = Stage-Jar $ServerJar $serverDir $defaults.ServerJar
$fabricApi = Stage-Jar $FabricApiJar $clientDir $defaults.FabricApiJar

Write-Host "Emotecraft/Fabric API staged:"
Write-Host "  client: $($client.path) sha256=$($client.sha256)"
Write-Host "  server: $($server.path) sha256=$($server.sha256)"
Write-Host "  fabric-api: $($fabricApi.path) sha256=$($fabricApi.sha256)"
Write-Host "Official source:"
Write-Host "  https://modrinth.com/project/pZ2wrerK"
Write-Host "  https://modrinth.com/project/P7dR8mSH"
