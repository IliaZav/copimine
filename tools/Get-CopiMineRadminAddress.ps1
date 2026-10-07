$ErrorActionPreference = 'SilentlyContinue'

$addresses = @(
  Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'Radmin VPN' |
    Where-Object {
      $_.IPAddress -and
      $_.IPAddress -notlike '127.*' -and
      $_.AddressState -notin @('Tentative', 'Duplicate')
    } |
    Select-Object -ExpandProperty IPAddress
)
if ($addresses.Count -eq 0) {
  $addresses = @(
    Get-NetIPAddress -AddressFamily IPv4 |
      Where-Object {
        $_.InterfaceAlias -match '(?i)radmin' -and
        $_.IPAddress -and
        $_.IPAddress -notlike '127.*'
      } |
      Select-Object -ExpandProperty IPAddress
  )
}
@($addresses | Select-Object -First 1)
