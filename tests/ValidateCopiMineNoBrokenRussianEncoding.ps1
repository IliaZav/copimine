. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$script = @'
from pathlib import Path
import re
import sys

scan_roots = [
    Path(r"D:\Desktop\Copimine\opt\copimine"),
    Path(r"D:\Desktop\Copimine\CopiMineClient"),
    Path(r"D:\Desktop\Copimine\opt\copimine\docs\superpowers\specs"),
    Path(r"D:\Desktop\Copimine\opt\copimine\docs\superpowers\plans"),
]
extensions = {".java", ".py", ".js", ".yml", ".yaml", ".md"}
skip_parts = {"build", "target", "node_modules", ".git", ".gradle", ".venv", "backups", ".idea", ".mvn", "out"}
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
    re.compile(r"(?:\u0420.|\u0421.|\u00D0.|\u00D1.){3,}"),
    re.compile(r"(?:\u0432\u20AC|\u0432\u20AC\u00A6|\u0432\u20AC\u201D|\u0432\u20AC\u0153|\u0432\u20AC\u015D|\u0432\u20AC\u2122|\u0432\u20AC\u2122)"),
]

hits = []
for root in scan_roots:
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix.lower() not in extensions:
            continue
        if any(part in skip_parts for part in path.parts):
            continue
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
    $script:errors.Add("Broken Russian/mojibake validator failed without output.")
  }
}

Throw-IfErrors 'ValidateCopiMineNoBrokenRussianEncoding'
