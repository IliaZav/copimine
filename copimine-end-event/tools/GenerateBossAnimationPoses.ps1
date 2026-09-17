param(
  [switch]$Check,
  [string]$OutputPath
)

# Use -Check in gates to prove the checked-in Java resource is reproducible.

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pluginRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$repositoryRoot = (Resolve-Path (Join-Path $pluginRoot '..')).Path
$clientAssetRoot = Join-Path $repositoryRoot 'CopiMineClient\src\main\resources\assets\copimineclient\models\entity\end_rift_guardian'
$animationRoot = Join-Path $clientAssetRoot 'animations'
$defaultOutput = Join-Path $pluginRoot 'src\me\copimine\endevent\domain\GeneratedBossAnimationPoses.java'

if (-not $OutputPath) {
  $OutputPath = $defaultOutput
}

$culture = [Globalization.CultureInfo]::InvariantCulture
$clips = @(
  [ordered]@{ File = 'idle.json'; Id = 'IDLE_BREATH'; Loop = $true },
  [ordered]@{ File = 'running.json'; Id = 'RUN'; Loop = $true },
  [ordered]@{ File = 'swipe.json'; Id = 'MELEE_SWIPE'; Loop = $false },
  [ordered]@{ File = 'hurt.json'; Id = 'HURT'; Loop = $false },
  [ordered]@{ File = 'dying.json'; Id = 'DYING'; Loop = $false },
  [ordered]@{ File = 'udar_iz_grudi.json'; Id = 'CHEST_STRIKE'; Loop = $false },
  [ordered]@{ File = 'udar_po_zemle.animation.json'; Id = 'GROUND_SLAM'; Loop = $false }
)

function Fail-Asset([string]$source, [string]$message) {
  throw "End Rift animation generator validation failed source=$source : $message"
}

function Read-Json([string]$path) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    Fail-Asset $path 'file is missing'
  }
  try {
    return (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)
  } catch {
    Fail-Asset $path ("invalid JSON: " + $_.Exception.Message)
  }
}

function Assert-OnlyProperties($object, [string[]]$allowed, [string]$source, [string]$path) {
  if ($null -eq $object) {
    Fail-Asset $source "$path is missing"
  }
  $allowedSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  foreach ($name in $allowed) { [void]$allowedSet.Add($name) }
  foreach ($property in @($object.PSObject.Properties)) {
    if (-not $allowedSet.Contains($property.Name)) {
      Fail-Asset $source "$path contains unsupported field '$($property.Name)'"
    }
  }
}

function Get-FiniteNumber($value, [string]$source, [string]$path) {
  if ($null -eq $value -or $value -is [string] -or $value -is [bool]) {
    Fail-Asset $source "$path must be a finite number"
  }
  try { $number = [double]$value } catch { Fail-Asset $source "$path must be a finite number" }
  if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) {
    Fail-Asset $source "$path must be a finite number"
  }
  return $number
}

function Get-Vector($value, [string]$source, [string]$path) {
  $values = @($value)
  if ($values.Count -ne 3) {
    Fail-Asset $source "$path must contain exactly three numbers"
  }
  return @(
    (Get-FiniteNumber $values[0] $source "$path[0]"),
    (Get-FiniteNumber $values[1] $source "$path[1]"),
    (Get-FiniteNumber $values[2] $source "$path[2]")
  )
}

function Get-KeyframeTime([string]$value, [string]$source, [string]$path) {
  if ([string]::IsNullOrWhiteSpace($value)) {
    Fail-Asset $source "$path must be numeric"
  }
  try { $number = [double]::Parse($value, [Globalization.NumberStyles]::Float, $culture) }
  catch { Fail-Asset $source "$path must be numeric" }
  if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) {
    Fail-Asset $source "$path must be finite"
  }
  return $number
}

function Get-Channel($value, [string]$source, [string]$path) {
  if ($null -eq $value) { return $null }
  if ($value -is [System.Array]) {
    return @([ordered]@{ Time = 0.0D; Vector = (Get-Vector $value $source $path) })
  }
  $frames = [Collections.Generic.List[object]]::new()
  $times = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  foreach ($property in @($value.PSObject.Properties | Sort-Object Name)) {
    if ($property.Name -eq 'lerp_mode') {
      Fail-Asset $source "$path.lerp_mode is unsupported"
    }
    if ($property.Name -eq 'pre' -or $property.Name -eq 'post' -or $property.Name -eq 'vector') {
      Fail-Asset $source "$path uses an unsupported keyframe shape"
    }
    $time = Get-KeyframeTime $property.Name $source "$path.$($property.Name)"
    if ($time -lt 0.0D) { Fail-Asset $source "$path.$($property.Name) keyframe time must not be negative" }
    $timeKey = $time.ToString('R', $culture)
    if (-not $times.Add($timeKey)) { Fail-Asset $source "$path contains duplicate keyframe time $time" }
    $keyframe = $property.Value
    Assert-OnlyProperties $keyframe @('vector') $source "$path.$($property.Name)"
    if ($null -eq $keyframe.vector) {
      Fail-Asset $source "$path.$($property.Name).vector is missing"
    }
    [void]$frames.Add([ordered]@{
        Time = $time * 20.0D
        Vector = (Get-Vector $keyframe.vector $source "$path.$($property.Name).vector")
      })
  }
  if ($frames.Count -eq 0) { Fail-Asset $source "$path contains no keyframes" }
  return @($frames | Sort-Object { $_.Time })
}

function Format-JavaDouble([double]$value) {
  if ($value -eq 0.0D) { return '0.0D' }
  $text = $value.ToString('R', $culture)
  if ($text -notmatch '[\.eE]') { $text += '.0' }
  return "$text`D"
}

function Render-Vector($vector) {
  return "new Vec3($(Format-JavaDouble $vector[0]), $(Format-JavaDouble $vector[1]), $(Format-JavaDouble $vector[2]))"
}

function Render-Channel($frames) {
  if ($null -eq $frames) { return 'Channel.EMPTY' }
  $items = @($frames | ForEach-Object {
      "new Keyframe($(Format-JavaDouble $_.Time), $(Render-Vector $_.Vector))"
    })
  return "new Channel(List.of($($items -join ', ')))"
}

function Get-SourceDigest($inputs) {
  $sha = [Security.Cryptography.SHA256]::Create()
  try {
    $bytes = [Collections.Generic.List[byte]]::new()
    foreach ($input in $inputs) {
      $normalized = (Get-Content -LiteralPath $input.Path -Raw).Replace("`r`n", "`n").Replace("`r", "`n")
      $payload = "$($input.Name)`n$normalized`n"
      $bytes.AddRange([Text.Encoding]::UTF8.GetBytes($payload))
    }
    return ([BitConverter]::ToString($sha.ComputeHash($bytes.ToArray())) -replace '-', '').ToLowerInvariant()
  } finally {
    $sha.Dispose()
  }
}

$geometryPath = Join-Path $clientAssetRoot 'geometry.json'
$geometry = Read-Json $geometryPath
Assert-OnlyProperties $geometry @('format_version', 'minecraft:geometry') $geometryPath 'document'
$geometryList = @($geometry.'minecraft:geometry')
if ($geometryList.Count -ne 1) { Fail-Asset $geometryPath 'minecraft:geometry must contain exactly one definition' }
$geometryDefinition = $geometryList[0]
Assert-OnlyProperties $geometryDefinition @('description', 'bones') $geometryPath 'minecraft:geometry[0]'
$geometryBones = @($geometryDefinition.bones)
if ($geometryBones.Count -eq 0) { Fail-Asset $geometryPath 'bones must not be empty' }
$knownBones = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($bone in $geometryBones) {
  Assert-OnlyProperties $bone @('name', 'parent', 'pivot', 'rotation', 'cubes') $geometryPath 'bone'
  if ($null -eq $bone.name -or [string]::IsNullOrWhiteSpace([string]$bone.name)) {
    Fail-Asset $geometryPath 'bone name must not be blank'
  }
  if (-not $knownBones.Add([string]$bone.name)) { Fail-Asset $geometryPath "duplicate bone '$($bone.name)'" }
}
$geometryDefinitions = [Collections.Generic.List[object]]::new()
foreach ($bone in $geometryBones) {
  $boneName = [string]$bone.name
  $parent = if ($null -eq $bone.parent -or [string]::IsNullOrWhiteSpace([string]$bone.parent)) {
    $null
  } else {
    [string]$bone.parent
  }
  if ($null -ne $parent -and -not $knownBones.Contains($parent)) {
    Fail-Asset $geometryPath "bone '$boneName' references unknown parent '$parent'"
  }
  $pivot = Get-Vector $bone.pivot $geometryPath "bone.$boneName.pivot"
  $bindRotation = if ($null -eq $bone.rotation) {
    @(0.0D, 0.0D, 0.0D)
  } else {
    Get-Vector $bone.rotation $geometryPath "bone.$boneName.rotation"
  }
  [void]$geometryDefinitions.Add([ordered]@{
      Name = $boneName
      Parent = $parent
      Pivot = $pivot
      BindRotation = $bindRotation
    })
}

$parsedClips = [Collections.Generic.List[object]]::new()
$digestInputs = [Collections.Generic.List[object]]::new()
$digestInputs.Add([ordered]@{ Name = 'geometry.json'; Path = $geometryPath })
foreach ($clipSpec in $clips) {
  $path = Join-Path $animationRoot $clipSpec.File
  $document = Read-Json $path
  $digestInputs.Add([ordered]@{ Name = "animations/$($clipSpec.File)"; Path = $path })
  Assert-OnlyProperties $document @('format_version', 'animations') $path 'document'
  $animationProperties = @($document.animations.PSObject.Properties)
  if ($animationProperties.Count -ne 1) { Fail-Asset $path 'animations must contain exactly one definition' }
  $animation = $animationProperties[0].Value
  Assert-OnlyProperties $animation @('animation_length', 'loop', 'bones') $path 'animation'
  $lengthSeconds = Get-FiniteNumber $animation.animation_length $path 'animation.animation_length'
  if ($lengthSeconds -le 0.0D) { Fail-Asset $path 'animation_length must be positive' }
  if ($null -ne $animation.loop -and $animation.loop -isnot [bool]) {
    Fail-Asset $path 'animation.loop must be boolean'
  }
  $sourceLoop = if ($null -eq $animation.loop) { $null } else { [bool]$animation.loop }
  if ($null -ne $sourceLoop -and $sourceLoop -ne [bool]$clipSpec.Loop) {
    Fail-Asset $path "animation.loop=$sourceLoop does not match clip specification loop=$($clipSpec.Loop)"
  }
  $tracks = [ordered]@{}
  if ($null -ne $animation.bones) {
    foreach ($boneProperty in @($animation.bones.PSObject.Properties | Sort-Object Name)) {
      $boneName = [string]$boneProperty.Name
      if (-not $knownBones.Contains($boneName)) {
        Fail-Asset $path "animation track references unknown geometry bone '$boneName'"
      }
      Assert-OnlyProperties $boneProperty.Value @('rotation', 'position', 'scale') $path "animation.bones.$boneName"
      $parsedTrack = [ordered]@{
        Rotation = Get-Channel $boneProperty.Value.rotation $path "animation.bones.$boneName.rotation"
        Position = Get-Channel $boneProperty.Value.position $path "animation.bones.$boneName.position"
        Scale = Get-Channel $boneProperty.Value.scale $path "animation.bones.$boneName.scale"
      }
      if ($null -ne $parsedTrack.Scale) {
        Fail-Asset $path "animation.bones.$boneName.scale is unsupported by server hitbox PoseOffset"
      }
      $tracks[$boneName] = $parsedTrack
    }
  }
  $parsedClips.Add([ordered]@{
      Id = $clipSpec.Id
      LengthTicks = $lengthSeconds * 20.0D
      Loop = [bool]$clipSpec.Loop
      Tracks = $tracks
    })
}

$digest = Get-SourceDigest $digestInputs
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('package me.copimine.endevent.domain;')
$lines.Add('')
$lines.Add('import java.util.LinkedHashMap;')
$lines.Add('import java.util.List;')
$lines.Add('import java.util.Map;')
$lines.Add('')
$lines.Add('/** Generated by copimine-end-event/tools/GenerateBossAnimationPoses.ps1; do not edit. */')
$lines.Add('public final class GeneratedBossAnimationPoses {')
$lines.Add('    public static final String SOURCE_DIGEST = "' + $digest + '";')
$lines.Add('    private static final Map<String, BoneDefinition> BONES = buildBones();')
$lines.Add('    private static final Map<String, Clip> CLIPS = build();')
$lines.Add('')
$lines.Add('    private GeneratedBossAnimationPoses() { }')
$lines.Add('')
$lines.Add('    public static Clip clip(String animationId) {')
$lines.Add('        return CLIPS.get(animationId);')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public static Map<String, Clip> clips() {')
$lines.Add('        return CLIPS;')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public static BoneDefinition bone(String name) {')
$lines.Add('        return BONES.get(name);')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public static Map<String, BoneDefinition> bones() {')
$lines.Add('        return BONES;')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    private static Map<String, BoneDefinition> buildBones() {')
$lines.Add('        return Map.ofEntries(')
$boneEntries = @($geometryDefinitions | ForEach-Object {
    $parentExpression = if ($null -eq $_.Parent) { 'null' } else { '"' + $_.Parent + '"' }
    '                Map.entry("' + $_.Name + '", new BoneDefinition(' + $parentExpression + ', ' +
      (Render-Vector $_.Pivot) + ', ' + (Render-Vector $_.BindRotation) + '))'
})
for ($index = 0; $index -lt $boneEntries.Count; $index++) {
  $suffix = if ($index -eq $boneEntries.Count - 1) { '' } else { ',' }
  $lines.Add($boneEntries[$index] + $suffix)
}
$lines.Add('        );')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    private static Map<String, Clip> build() {')
$lines.Add('        Map<String, Clip> result = new LinkedHashMap<>();')
foreach ($clip in $parsedClips) {
  $entries = @($clip.Tracks.GetEnumerator() | Sort-Object Key | ForEach-Object {
      $track = $_.Value
      $entryLine = '                Map.entry("' + $_.Key + '", new BoneTrack(' + (Render-Channel $track.Rotation) + ', ' + (Render-Channel $track.Position) + ', ' + (Render-Channel $track.Scale) + '))'
      $entryLine
    })
  $mapExpression = if ($entries.Count -eq 0) { 'Map.of()' } else { "Map.of($($entries -join ', '))" }
  if ($entries.Count -gt 0) {
    $mapExpression = "Map.ofEntries($($entries -join ', '))"
  }
  $clipLine = '        result.put("' + $clip.Id + '", new Clip(' + (Format-JavaDouble $clip.LengthTicks) + ', ' + $clip.Loop.ToString().ToLowerInvariant() + ', ' + $mapExpression + '));'
  $lines.Add($clipLine)
}
$lines.Add('        return Map.copyOf(result);')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public record Clip(double lengthTicks, boolean loop, Map<String, BoneTrack> bones) {')
$lines.Add('        public Clip {')
$lines.Add('            if (!Double.isFinite(lengthTicks) || lengthTicks <= 0.0D || bones == null)')
$lines.Add('                throw new IllegalArgumentException("invalid generated boss animation clip");')
$lines.Add('            bones = Map.copyOf(bones);')
$lines.Add('        }')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public record BoneDefinition(String parent, Vec3 pivot, Vec3 bindRotation) {')
$lines.Add('        public BoneDefinition {')
$lines.Add('            if (pivot == null || bindRotation == null)')
$lines.Add('                throw new IllegalArgumentException("invalid generated boss bone definition");')
$lines.Add('            parent = parent == null || parent.isBlank() ? null : parent;')
$lines.Add('        }')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public record BoneTrack(Channel rotation, Channel position, Channel scale) {')
$lines.Add('        public BoneTrack {')
$lines.Add('            rotation = rotation == null ? Channel.EMPTY : rotation;')
$lines.Add('            position = position == null ? Channel.EMPTY : position;')
$lines.Add('            scale = scale == null ? Channel.EMPTY : scale;')
$lines.Add('        }')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public record Channel(List<Keyframe> frames) {')
$lines.Add('        private static final Channel EMPTY = new Channel(List.of());')
$lines.Add('        public Channel { frames = frames == null ? List.of() : List.copyOf(frames); }')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public record Keyframe(double timeTicks, Vec3 vector) {')
$lines.Add('        public Keyframe {')
$lines.Add('            if (!Double.isFinite(timeTicks) || timeTicks < 0.0D || vector == null)')
$lines.Add('                throw new IllegalArgumentException("invalid generated boss animation keyframe");')
$lines.Add('        }')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    public record Vec3(double x, double y, double z) {')
$lines.Add('        public Vec3 {')
$lines.Add('            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))')
$lines.Add('                throw new IllegalArgumentException("invalid generated boss animation vector");')
$lines.Add('        }')
$lines.Add('    }')
$lines.Add('}')
$generated = ($lines -join [Environment]::NewLine) + [Environment]::NewLine

if ($Check) {
  if (-not (Test-Path -LiteralPath $OutputPath -PathType Leaf)) {
    throw "Generated boss animation resource is missing: $OutputPath"
  }
  $actual = Get-Content -LiteralPath $OutputPath -Raw
  if ($actual -ne $generated) {
    throw "Generated boss animation resource is stale: $OutputPath"
  }
  Write-Host "Boss animation pose resource is current: $OutputPath"
} else {
  $parent = Split-Path -Parent $OutputPath
  New-Item -ItemType Directory -Path $parent -Force | Out-Null
  [IO.File]::WriteAllText($OutputPath, $generated, [Text.UTF8Encoding]::new($false))
  Write-Host "Generated $OutputPath"
  Write-Host "Source digest $digest"
}
