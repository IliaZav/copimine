. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$env:COPIMINE_REPO_ROOT = $repoRoot

$script = @'
from pathlib import Path
import os
import re
import sys

repo_root = Path(os.environ["COPIMINE_REPO_ROOT"])
scan_roots = [
    repo_root / "copimine-admin-plugin" / "src",
    repo_root / "copimine-artifacts" / "src",
    repo_root / "copimine-economy-core" / "src",
    repo_root / "copimine-election-core" / "src",
    repo_root / "copimine-narcotics" / "src",
    repo_root / "copimine-world-core" / "src",
    repo_root / "CopiMineClient" / "src",
    repo_root / "CopiMineClient" / "src" / "main" / "java",
]
bad_tokens = [
    "\u00d0", "\u00d1", "\u0412\u00a7", "\u0420'\u0412\u00a7",
    "\u0420\u040e", "\u0420\u045f", "\u0420\u045e", "\u0420\u045a",
    "\u0421\u0403", "\u0421\u201a", "\u0421\u0402", "\u0421\u0453",
    "?????", "?????????", "\ufffd"
]
bad_escape_patterns = [
    r"\\u0420\\u00[0-9A-Fa-f]{2}",
    r"\\u0420\\u201[0-9A-Fa-f]",
    r"\\u0420\\u045[0-9A-Fa-f]",
    r"\\u0421\\u201[0-9A-Fa-f]",
    r"\\u0421\\u040[0-9A-Fa-f]",
]
mojibake_patterns = [
    re.compile(r"(?:\u0420[\u0400-\u04ff]\u0421[\u0400-\u04ff]){2,}"),
    re.compile(r"(?:\u00d0.\u00d1.){2,}"),
]

hits = []
for root in scan_roots:
    if not root.exists():
        continue
    for path in root.rglob("*.java"):
        try:
            text = path.read_text(encoding="utf-8")
        except Exception:
            continue
        if any(token in text for token in bad_tokens):
            hits.append(str(path))
            continue
        if any(re.search(pattern, text) for pattern in bad_escape_patterns):
            hits.append(str(path))
            continue
        if any(pattern.search(text) for pattern in mojibake_patterns):
            hits.append(str(path))

for hit in hits:
    print(hit)
sys.exit(1 if hits else 0)
'@

$output = $script | python - 2>&1
if ($LASTEXITCODE -ne 0) {
  $printed = $false
  foreach ($line in ($output -split "`r?`n")) {
    if (-not [string]::IsNullOrWhiteSpace($line)) {
      $script:errors.Add("Broken Russian/mojibake validation output: $line")
      $printed = $true
    }
  }
  if (-not $printed) {
    $script:errors.Add("Broken Russian/mojibake Java validator failed without output.")
  }
}

Throw-IfErrors 'ValidateCopiMineJavaNoBrokenRussianEncoding'
