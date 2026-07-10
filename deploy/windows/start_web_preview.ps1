param(
    [int]$Port = 18082
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$FrontendRoot = Join-Path $ProjectRoot "admin-web\frontend"
$Python = "C:\Users\zavod\AppData\Local\Programs\Python\Python313\python.exe"

if (-not (Test-Path $FrontendRoot)) {
    throw "Frontend directory not found: $FrontendRoot"
}

if (-not (Test-Path $Python)) {
    throw "Python not found: $Python"
}

$Existing = Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -match "^python(\.exe)?$" -and
        $_.CommandLine -like "*http.server $Port*" -and
        $_.CommandLine -like "*admin-web\\frontend*"
    }

if ($Existing) {
    Write-Host "Preview server is already running on port $Port." -ForegroundColor Yellow
} else {
    Start-Process -FilePath $Python `
        -ArgumentList "-m", "http.server", "$Port", "--directory", $FrontendRoot `
        -WindowStyle Hidden | Out-Null
    Start-Sleep -Seconds 2
}

$BaseUrl = "http://127.0.0.1:$Port"

try {
    Invoke-WebRequest -UseBasicParsing "$BaseUrl/preview-admin.html" | Out-Null
    Invoke-WebRequest -UseBasicParsing "$BaseUrl/preview-player.html" | Out-Null
} catch {
    throw "Preview server did not respond on $BaseUrl"
}

Write-Host ""
Write-Host "Local web preview is ready:" -ForegroundColor Green
Write-Host "  Admin preview : $BaseUrl/preview-admin.html"
Write-Host "  Player preview: $BaseUrl/preview-player.html"
Write-Host "  Public pages  : $BaseUrl/index.html"
Write-Host ""
Write-Host "This preview serves static frontend files only." -ForegroundColor Cyan
Write-Host "Live API flows still require the backend and PostgreSQL runtime."
