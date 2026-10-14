<#
.SYNOPSIS
    Starts CareerPilot on Windows without Docker: MySQL, Redis, RabbitMQ and all 14 Spring Boot apps.

.DESCRIPTION
    Uses the portable runtime created by setup-local.ps1. Settings come from .env. Builds the jars
    first if they are missing (or with -Build). Logs go to %USERPROFILE%\placify-runtime\logs.
    Open http://localhost:3000 when it reports that the portal is ready.

.PARAMETER Build
    Rebuild all jars with Gradle before starting.

.PARAMETER WithModels
    Enable the DJL models (sentence embeddings and phone detection). They are downloaded on first
    use (about 500 MB); without them the built-in fallbacks are used.

.PARAMETER PublicUrl
    Public https address of the portal (set by share-online.ps1). Added to the allowed origins.

.PARAMETER PublicWsUrl
    Public wss address the browser uses for live notifications (set by share-online.ps1).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1
#>
param(
    [switch]$Build,
    [switch]$WithModels,
    [string]$PublicUrl,
    [string]$PublicWsUrl,
    [string]$Runtime = (Join-Path $env:USERPROFILE 'placify-runtime')
)

$ErrorActionPreference = 'Continue'   # native tools log to stderr; failures are checked explicitly
$Project = Split-Path -Parent $PSScriptRoot
$Logs = Join-Path $Runtime 'logs'

if (-not (Test-Path (Join-Path $Runtime 'mysql'))) {
    throw "The local runtime is missing. Run scripts\setup-local.ps1 first."
}
New-Item -ItemType Directory -Force $Logs | Out-Null

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

function Wait-Port([int]$Port, [string]$Name, [int]$Seconds = 120) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Test-Port $Port) { return }
        Start-Sleep -Seconds 1
    }
    throw "$Name did not start on port $Port. See the logs in $Logs"
}

function Find-Java21 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $candidates += Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }
    foreach ($c in $candidates) {
        if (Test-Path $c) {
            $version = (& $c -version 2>&1 | Select-Object -First 1).ToString()
            if ($version -match '"21') { return $c }
        }
    }
    throw 'JDK 21 was not found. Install it (e.g. Eclipse Temurin 21) or set JAVA_HOME.'
}

# ------------------------------------------------------------------ settings from .env
foreach ($line in Get-Content (Join-Path $Project '.env')) {
    if ($line -match '^\s*([A-Z_][A-Z0-9_]*)\s*=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
    }
}
if (-not $WithModels) {
    $env:EMBEDDINGS_ENABLED = 'false'
    $env:PHONE_DETECTION_ENABLED = 'false'
}
if ($PublicUrl) {
    $PublicUrl = $PublicUrl.TrimEnd('/')
    $env:PUBLIC_BASE_URL = $PublicUrl
    $env:WEB_ORIGIN = "http://localhost:3000,$PublicUrl"
    $env:WS_ALLOWED_ORIGINS = "http://localhost:3000,http://localhost:8080,$PublicUrl"
}
if ($PublicWsUrl) { $env:PUBLIC_WS_URL = $PublicWsUrl }
$env:STORAGE_ROOT = Join-Path $Runtime 'storage'
$env:THYMELEAF_CACHE = 'true'

# ------------------------------------------------------------------ infrastructure
if (-not (Test-Port 3306)) {
    Write-Host 'Starting MySQL ...'
    Start-Process (Join-Path $Runtime 'mysql\bin\mysqld.exe') -ArgumentList "--defaults-file=`"$(Join-Path $Runtime 'my.ini')`"" `
        -WindowStyle Hidden -RedirectStandardError (Join-Path $Logs 'mysql.log')
}
if (-not (Test-Port 6379)) {
    Write-Host 'Starting Redis ...'
    Start-Process (Join-Path $Runtime 'redis\redis-server.exe') -ArgumentList '--port 6379 --bind 127.0.0.1 --save "" --appendonly no' `
        -WorkingDirectory (Join-Path $Runtime 'redis') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $Logs 'redis.log')
}
if (-not (Test-Port 5672)) {
    Write-Host 'Starting RabbitMQ ...'
    $rabbitEnv = "set ERLANG_HOME=$(Join-Path $Runtime 'erlang')&& set RABBITMQ_BASE=$(Join-Path $Runtime 'rabbitmq-data')&& set RABBITMQ_NODENAME=rabbit@localhost&& "
    New-Item -ItemType Directory -Force (Join-Path $Runtime 'rabbitmq-data') | Out-Null
    Start-Process cmd.exe -ArgumentList "/c $rabbitEnv`"$(Join-Path $Runtime 'rabbitmq\sbin\rabbitmq-server.bat')`" > `"$(Join-Path $Logs 'rabbitmq.log')`" 2>&1" `
        -WindowStyle Hidden
}
Wait-Port 3306 'MySQL'
Wait-Port 6379 'Redis'
Wait-Port 5672 'RabbitMQ' 180
Write-Host 'Infrastructure is running (MySQL 3306, Redis 6379, RabbitMQ 5672).'

# ------------------------------------------------------------------ applications
$java = Find-Java21
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
$apps = [ordered]@{
    'discovery-server' = 8761; 'gateway' = 8080; 'auth-service' = 8081; 'profile-service' = 8082; 'placement-service' = 8083
    'coding-service' = 8084; 'submission-service' = 8085; 'assessment-service' = 8086; 'proctoring-service' = 8087
    'ai-helper-service' = 8088; 'interview-service' = 8089; 'notification-service' = 8090; 'analytics-service' = 8091; 'web-ui' = 3000
}
function Get-Jar([string]$App) {
    $dir = if ($App -eq 'web-ui') { Join-Path $Project 'web-ui\build\libs' } else { Join-Path $Project "services\$App\build\libs" }
    return Get-ChildItem $dir -Filter '*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
}
if ($Build -or ($apps.Keys | Where-Object { -not (Get-Jar $_) })) {
    Write-Host 'Building the jars (first time takes a few minutes) ...'
    Push-Location $Project
    & .\gradlew.bat bootJar --console=plain -q
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { throw 'The Gradle build failed.' }
}

function Start-App([string]$App) {
    if (Test-Port $apps[$App]) {
        Write-Host "  $App is already running"
        return
    }
    $heap = if ($App -in @('proctoring-service', 'ai-helper-service') -and $WithModels) { '768m' } else { '320m' }
    $jar = (Get-Jar $App).FullName
    # Register with Eureka as 127.0.0.1: the Wi-Fi address changes between networks, and a stale
    # registration makes the gateway forward requests to an address that no longer exists.
    $network = '-Deureka.instance.ip-address=127.0.0.1 -Deureka.instance.hostname=localhost -Dspring.cloud.inetutils.preferred-networks=127.0.0.1'
    Start-Process $java -ArgumentList "-Xmx$heap -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Duser.timezone=$($env:TZ) $network -jar `"$jar`"" `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $Logs "$App.log") -RedirectStandardError (Join-Path $Logs "$App.err.log")
    Write-Host "  started $App"
}

Write-Host 'Starting CareerPilot ...'
Start-App 'discovery-server'
Wait-Port 8761 'discovery-server'
foreach ($app in $apps.Keys) {
    if ($app -ne 'discovery-server') { Start-App $app }
}
Write-Host 'Waiting for the services to register (about 1-2 minutes) ...'
foreach ($app in $apps.Keys) {
    Wait-Port $apps[$app] $app 240
}
# services need a few seconds after opening their port to register with Eureka and the gateway
Start-Sleep -Seconds 40
Write-Host ''
Write-Host 'CareerPilot is ready:  http://localhost:3000'
Write-Host "Logs: $Logs      Stop with: powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1"
