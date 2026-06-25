. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$clientRoot = Join-Path $root 'CopiMineClient'
$clientJava = Get-ChildItem (Join-Path $clientRoot 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object {
  Get-Content -Raw -Encoding UTF8 $_.FullName
}
$combined = ($clientJava -join "`n")
$readme = Read-Utf8 (Join-Path $clientRoot 'README_INSTALL_RU.md')
$protocol = Read-Utf8 (Join-Path $clientRoot 'PROTOCOL.md')

Require-NotRegex $combined 'KeyBinding|KeyBindingHelper|GLFW_KEY_B|InputUtil' 'CopiMineClient must not register a conflicting default Emotecraft keybind.'
Require-Contains $combined 'copimine:client_bridge' 'CopiMineClient must use only its own plugin messaging channel.'
Require-Contains $readme 'Emotecraft' 'Client install README must document Emotecraft compatibility.'
Require-Contains $protocol 'Iris' 'PROTOCOL.md must document that Iris is optional and unmanaged by CopiMineClient.'

Throw-IfErrors 'ValidateCopiMineClientNoEmotecraftKeyConflict'
