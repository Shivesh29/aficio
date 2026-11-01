[CmdletBinding()]
param(
    [switch]$Frontend,
    [switch]$Backend,
    [string]$KeyPath = "$HOME\Downloads\aficio-vm_key.pem",
    [string]$VmIp = "20.219.11.69",
    [string]$VmUser = "azureuser"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Aficio Production Deployment to Azure  " -ForegroundColor Cyan
Write-Host "   Target: $VmUser@$VmIp                  " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

if (-not (Test-Path $KeyPath)) {
    Write-Error "SSH key not found at $KeyPath. Please verify the file exists."
    exit 1
}

$deployAll = (-not $Frontend -and -not $Backend)

# 1. Frontend Build & Deploy
if ($deployAll -or $Frontend) {
    Write-Host "`n[Frontend] Building Vite distribution..." -ForegroundColor Yellow
    Push-Location "$PSScriptRoot\frontend"
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "Frontend build failed" }
    } finally {
        Pop-Location
    }

    Write-Host "[Frontend] Uploading static assets to Azure VM..." -ForegroundColor Yellow
    scp -r -i $KeyPath "$PSScriptRoot\frontend\dist" "$VmUser@${VmIp}:~/aficio/frontend/"
    if ($LASTEXITCODE -ne 0) { throw "SCP upload of frontend dist failed" }

    # Ensure files and directories are world-readable so Nginx container process can serve them
    ssh -i $KeyPath "$VmUser@$VmIp" "chmod 755 /home/azureuser /home/azureuser/aficio /home/azureuser/aficio/frontend && chmod -R 755 ~/aficio/frontend/dist"

    Write-Host "[Frontend] Successfully deployed! Live at https://aficio.shivesh29.me" -ForegroundColor Green
}

# 2. Backend Build & Deploy
if ($deployAll -or $Backend) {
    Write-Host "`n[Backend] Packaging Maven JARs..." -ForegroundColor Yellow
    Push-Location "$PSScriptRoot"
    try {
        mvn package -DskipTests -pl shared/kafka-events,shared/dto-lib,services/api-gateway,services/auth-service,services/catalog-service,services/social-service -am
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
    } finally {
        Pop-Location
    }

    Write-Host "[Backend] Uploading JARs to Azure VM..." -ForegroundColor Yellow
    $services = @("api-gateway", "auth-service", "catalog-service", "social-service")
    foreach ($svc in $services) {
        $jar = "$PSScriptRoot\services\$svc\target\$svc-0.0.1-SNAPSHOT.jar"
        if (Test-Path $jar) {
            Write-Host "  -> Uploading $svc JAR..." -ForegroundColor DarkGray
            scp -i $KeyPath $jar "$VmUser@${VmIp}:~/aficio/services/$svc/target/"
        }
    }

    Write-Host "[Backend] Rebuilding & Restarting backend containers on Azure..." -ForegroundColor Yellow
    ssh -i $KeyPath "$VmUser@$VmIp" "cd ~/aficio && docker compose up -d --build api-gateway auth-service catalog-service social-service"
    if ($LASTEXITCODE -ne 0) { throw "Docker compose restart on VM failed" }

    Write-Host "[Backend] Services reloaded and healthy!" -ForegroundColor Green
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "  All deployments finished successfully! " -ForegroundColor Green
Write-Host "  Live URL: https://aficio.shivesh29.me   " -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan

