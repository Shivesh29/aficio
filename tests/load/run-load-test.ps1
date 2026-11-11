# Run k6 load test using Docker (no local k6 installation required)
param (
    [string]$TargetUrl = "https://aficio.shivesh29.me"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Running Aficio Microservices Benchmark against $TargetUrl" -ForegroundColor Cyan
Write-Host " Simulating 0 -> 100 concurrent virtual users" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

Get-Content "$scriptDir\k6-benchmark.js" | docker run --rm -i -e TARGET_URL=$TargetUrl grafana/k6 run -

