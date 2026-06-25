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
    }
    ServerJar = @{
        Url = "https://cdn.modrinth.com/data/pZ2wrerK/versions/DVp3FUqR/emotecraft-2.4.12-bukkit.jar"
        Name = "emotecraft-2.4.12-bukkit.jar"
    }
    FabricApiJar = @{
        Url = "https://cdn.modrinth.com/data/P7dR8mSH/versions/Lwt6YYHL/fabric-api-0.116.12%2B1.21.1.jar"
        Name = "fabric-api-0.116.12+1.21.1.jar"
    }
}

function Stage-Jar([string]$pathValue, [string]$targetDir, [hashtable]$meta) {
    $target = Join-Path $targetDir $meta.Name
    if ($pathValue) {
        if (-not (Test-Path -LiteralPath $pathValue)) {
            throw "Файл не найден: $pathValue"
        }
        if ([IO.Path]::GetExtension($pathValue) -ne ".jar") {
            throw "Нужен .jar файл: $pathValue"
        }
        Copy-Item -LiteralPath $pathValue -Destination $target -Force
    } else {
        Invoke-WebRequest $meta.Url -OutFile $target
    }
    return @{
        path = $target
        sha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $target).Hash.ToLowerInvariant()
        url = $meta.Url
    }
}

$client = Stage-Jar $ClientJar $clientDir $defaults.ClientJar
$server = Stage-Jar $ServerJar $serverDir $defaults.ServerJar
$fabricApi = Stage-Jar $FabricApiJar $clientDir $defaults.FabricApiJar

Write-Host "Emotecraft/Fabric API staged:"
Write-Host "  client: $($client.path) sha1=$($client.sha1)"
Write-Host "  server: $($server.path) sha1=$($server.sha1)"
Write-Host "  fabric-api: $($fabricApi.path) sha1=$($fabricApi.sha1)"
Write-Host "Official source:"
Write-Host "  https://modrinth.com/project/pZ2wrerK"
Write-Host "  https://modrinth.com/project/P7dR8mSH"
