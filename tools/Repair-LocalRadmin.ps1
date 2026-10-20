$ErrorActionPreference = 'Stop'
$logPath = Join-Path $env:TEMP 'copimine-radmin-admin-repair.log'

function Write-Check([string]$Message) {
  $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message
  Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
  Write-Output $line
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
  Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue
  try {
    $elevated = Start-Process -FilePath "$env:WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" -Verb RunAs -Wait -PassThru -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $PSCommandPath)
    if (Test-Path -LiteralPath $logPath) { Get-Content -LiteralPath $logPath }
    exit $elevated.ExitCode
  }
  catch {
    Add-Content -LiteralPath $logPath -Value ("UAC elevation was cancelled or unavailable: " + $_.Exception.Message) -Encoding UTF8
    Write-Error ("UAC elevation was cancelled or unavailable: " + $_.Exception.Message)
    exit 740
  }
}

Write-Check ("Admin identity: " + [Security.Principal.WindowsIdentity]::GetCurrent().Name)

$service = Get-Service -Name 'RvControlSvc' -ErrorAction Stop
Write-Check ("Radmin service before restart: " + $service.Status)
Restart-Service -Name 'RvControlSvc' -Force -ErrorAction Stop
Start-Sleep -Seconds 4
$service = Get-Service -Name 'RvControlSvc' -ErrorAction Stop
Write-Check ("Radmin service after restart: " + $service.Status)

$adapter = Get-NetAdapter -Name 'Radmin VPN' -ErrorAction Stop
Write-Check ("Radmin adapter before reset: " + $adapter.Status + ", ifIndex=" + $adapter.ifIndex)
Disable-NetAdapter -Name 'Radmin VPN' -Confirm:$false -ErrorAction Stop
Start-Sleep -Seconds 3
Enable-NetAdapter -Name 'Radmin VPN' -Confirm:$false -ErrorAction Stop
Start-Sleep -Seconds 6
$adapter = Get-NetAdapter -Name 'Radmin VPN' -ErrorAction Stop
Write-Check ("Radmin adapter after reset: " + $adapter.Status + ", ifIndex=" + $adapter.ifIndex)

Set-NetIPInterface -InterfaceAlias 'Radmin VPN' -AddressFamily IPv4 -AutomaticMetric Disabled -InterfaceMetric 1 -NlMtuBytes 1500 -ErrorAction Stop
Write-Check 'Radmin IPv4 interface metric=1, MTU=1500'

try {
  Set-NetConnectionProfile -InterfaceAlias 'Radmin VPN' -NetworkCategory Private -ErrorAction Stop
  Write-Check 'Radmin network profile set to Private'
}
catch {
  Write-Check ("Radmin network profile was not changed: " + $_.Exception.Message)
}

$rules = @(
  @{ Name = 'CopiMine Local Radmin TCP 25566'; Protocol = 'TCP'; Port = '25566'; Description = 'Local CopiMine Paper test server over Radmin VPN only.' },
  @{ Name = 'CopiMine Local Radmin TCP 8092'; Protocol = 'TCP'; Port = '8092'; Description = 'Local CopiMine resource pack over Radmin VPN only.' }
)

foreach ($item in $rules) {
  $existing = Get-NetFirewallRule -DisplayName $item.Name -ErrorAction SilentlyContinue
  if (-not $existing) {
    New-NetFirewallRule -DisplayName $item.Name -Direction Inbound -Action Allow -Protocol $item.Protocol -LocalPort $item.Port -InterfaceAlias 'Radmin VPN' -Profile Any -EdgeTraversalPolicy Block -Description $item.Description -ErrorAction Stop | Out-Null
    Write-Check ("Created narrow firewall rule: " + $item.Name)
  }
  else {
    Write-Check ("Narrow firewall rule already exists: " + $item.Name)
  }
}

$icmp = Get-NetFirewallRule -DisplayName 'CopiMine Local Radmin ICMPv4' -ErrorAction SilentlyContinue
if (-not $icmp) {
  New-NetFirewallRule -DisplayName 'CopiMine Local Radmin ICMPv4' -Direction Inbound -Action Allow -Protocol ICMPv4 -IcmpType 8 -InterfaceAlias 'Radmin VPN' -Profile Any -EdgeTraversalPolicy Block -Description 'Local Radmin peer ping diagnostics only.' -ErrorAction Stop | Out-Null
  Write-Check 'Created narrow firewall rule: CopiMine Local Radmin ICMPv4'
}
else {
  Write-Check 'Narrow firewall rule already exists: CopiMine Local Radmin ICMPv4'
}

$ip = Get-NetIPAddress -InterfaceAlias 'Radmin VPN' -AddressFamily IPv4 -ErrorAction Stop | Where-Object IPAddress -like '26.*' | Select-Object -First 1
$route = Get-NetRoute -InterfaceAlias 'Radmin VPN' -DestinationPrefix '26.0.0.0/8' -ErrorAction Stop | Select-Object -First 1
Write-Check ("Radmin IPv4=" + $ip.IPAddress + "/" + $ip.PrefixLength + ", route metric=" + $route.RouteMetric + ", state=" + $route.State)

Write-Check 'Peer ping 26.202.172.80 (10 packets):'
ping.exe -n 10 -w 1000 26.202.172.80

Write-Check 'Admin Radmin repair finished.'
