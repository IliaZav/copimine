. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$script = @'
from pathlib import Path
import sys

scan_roots = [
    Path(r"D:\Desktop\Copimine\opt\copimine"),
    Path(r"D:\Desktop\Copimine\opt\copimine\CopiMineClient"),
]
extensions = {".java", ".py", ".js", ".yml", ".yaml", ".md"}
skip_parts = {"build", "target", "node_modules", ".git", ".gradle", ".venv", "backups", ".idea", ".mvn", "out"}
bad_tokens = [
    "\u00d0", "\u00d1", "\u0412\u00a7", "\u0420'\u0412\u00a7",
    "\u0420\u040e", "\u0420\u045f", "\u0420\u045e", "\u0420\u045a",
    "\u0421\u0403", "\u0421\u201a", "\u0421\u0402", "\u0421\u0453",
    "?????", "?????????", "\ufffd"
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

for hit in hits:
    print(hit)
sys.exit(1 if hits else 0)
'@

$output = $script | python -
if ($LASTEXITCODE -ne 0) {
  foreach ($line in ($output -split "`r?`n")) {
    if (-not [string]::IsNullOrWhiteSpace($line)) {
      $script:errors.Add("Broken Russian/mojibake found in $line")
    }
  }
}

Throw-IfErrors 'ValidateCopiMineNoBrokenRussianEncoding'
