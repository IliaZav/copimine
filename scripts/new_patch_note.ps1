[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-z0-9][a-z0-9-]{1,119}$')][string]$Slug,
    [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Title,
    [string]$Date = (Get-Date -Format 'yyyy-MM-dd')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$target = Join-Path $repoRoot "content/patches/$Date-$Version.yaml"
if (Test-Path -LiteralPath $target) {
    throw "Patch source already exists: $target"
}

$content = @"
schemaVersion: 1
id: "$Date-$Slug"
slug: "$Slug"
version: "$Version"
title: "$($Title.Replace('"', '\"'))"
publishedAt: "$(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssZ')"
summary:
  - ""
sections:
  general: []
  technical: []
  bugfixes: []
items: []
review:
  reviewedBy: ""
  reviewedAtUtc: ""
  genericMarketingCopyFound: false
"@

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
[System.IO.File]::WriteAllText($target, $content, [System.Text.UTF8Encoding]::new($false))
Write-Output $target
