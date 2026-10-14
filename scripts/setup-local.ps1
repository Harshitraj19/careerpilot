<#
.SYNOPSIS
    One-time setup for running CareerPilot on Windows without Docker.

.DESCRIPTION
    Downloads portable MySQL 8.4, Redis, Erlang/OTP and RabbitMQ into %USERPROFILE%\placify-runtime
    (nothing is installed system-wide), initialises the MySQL data directory and creates the CareerPilot
    databases and user from the values in .env. Run it once; afterwards use start-local.ps1.

    Requires: Windows 10/11, JDK 21, about 2 GB of free disk space.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\setup-local.ps1
#>
param(
    [string]$Runtime = (Join-Path $env:USERPROFILE 'placify-runtime')
)

$ErrorActionPreference = 'Continue'   # native tools log to stderr; failures are checked through exit codes
$ProgressPreference = 'SilentlyContinue'
$Project = Split-Path -Parent $PSScriptRoot

$MySqlVersion = '8.4.9'
$Downloads = [ordered]@{
    mysql  = "https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-$MySqlVersion-winx64.zip"
    redis  = 'https://github.com/redis-windows/redis-windows/releases/download/8.10.2/Redis-8.10.2-Windows-x64-msys2.zip'
    erlang = 'https://github.com/erlang/otp/releases/download/OTP-28.5.0.7/otp_win64_28.5.0.7.zip'
    rabbit = 'https://github.com/rabbitmq/rabbitmq-server/releases/download/v4.3.6/rabbitmq-server-windows-4.3.6.zip'
}

function Read-DotEnv([string]$Path) {
    $values = @{}
    if (Test-Path $Path) {
        foreach ($line in Get-Content $Path) {
            if ($line -match '^\s*([A-Z_][A-Z0-9_]*)\s*=(.*)$') { $values[$Matches[1]] = $Matches[2].Trim() }
        }
    }
    return $values
}

function Get-Package([string]$Name, [string]$Url, [string]$Target, [string[]]$Exclude = @()) {
    if (Test-Path $Target) {
        Write-Host "  $Name already present"
        return
    }
    $zip = Join-Path $Runtime "$Name.zip"
    Write-Host "  downloading $Name ..."
    & curl.exe -sSL --fail -o $zip $Url
    if ($LASTEXITCODE -ne 0) { throw "Download of $Name failed: $Url" }
    Write-Host "  extracting $Name ..."
    $tmp = Join-Path $Runtime "$Name-extract"
    New-Item -ItemType Directory -Force $tmp | Out-Null
    $tarArgs = @('-xf', $zip, '-C', $tmp)
    foreach ($e in $Exclude) { $tarArgs += @('--exclude', $e) }
    & tar.exe @tarArgs
    if ($LASTEXITCODE -ne 0) { throw "Extracting $Name failed" }
    $inner = Get-ChildItem $tmp
    if ($inner.Count -eq 1 -and $inner[0].PSIsContainer) {
        Move-Item $inner[0].FullName $Target
        Remove-Item $tmp -Recurse -Force
    } else {
        Move-Item $tmp $Target
    }
    Remove-Item $zip -Force
}

Write-Host "CareerPilot local runtime: $Runtime"
New-Item -ItemType Directory -Force $Runtime, (Join-Path $Runtime 'logs') | Out-Null

Get-Package 'mysql' $Downloads.mysql (Join-Path $Runtime 'mysql') @('*/lib/debug/*', '*.pdb', '*/include/*', '*/docs/*', '*mysqld-debug*', '*/lib/mecab/*')
Get-Package 'redis' $Downloads.redis (Join-Path $Runtime 'redis')
Get-Package 'erlang' $Downloads.erlang (Join-Path $Runtime 'erlang') @('*/vc_redist.exe', '*/doc/*', '*/lib/*/doc/*', '*/lib/*/examples/*')
Get-Package 'rabbitmq' $Downloads.rabbit (Join-Path $Runtime 'rabbitmq')

# ------------------------------------------------------------------ MySQL data directory and users
$dotenv = Read-DotEnv (Join-Path $Project '.env')
$dbUser = if ($dotenv.DB_USER) { $dotenv.DB_USER } else { 'placify' }
$dbPassword = if ($dotenv.DB_PASSWORD) { $dotenv.DB_PASSWORD } else { 'placify' }
$mysqlHome = Join-Path $Runtime 'mysql'
$dataDir = Join-Path $Runtime 'mysql-data'
$ini = Join-Path $Runtime 'my.ini'
@"
[mysqld]
basedir=$($mysqlHome -replace '\\', '/')
datadir=$($dataDir -replace '\\', '/')
port=3306
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-time-zone=+00:00
innodb_buffer_pool_size=128M
"@ | Set-Content -Encoding ascii $ini

if (-not (Test-Path $dataDir)) {
    Write-Host '  initialising MySQL data directory ...'
    & (Join-Path $mysqlHome 'bin\mysqld.exe') "--defaults-file=$ini" --initialize-insecure --console 2>&1 | Out-Null
    $mysqld = Start-Process (Join-Path $mysqlHome 'bin\mysqld.exe') -ArgumentList "--defaults-file=`"$ini`"" -WindowStyle Hidden -PassThru
    $client = Join-Path $mysqlHome 'bin\mysql.exe'
    for ($i = 0; $i -lt 60; $i++) {
        & $client -uroot -e 'SELECT 1' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    }
    $sql = @"
CREATE USER IF NOT EXISTS '$dbUser'@'localhost' IDENTIFIED BY '$dbPassword';
CREATE USER IF NOT EXISTS '$dbUser'@'%' IDENTIFIED BY '$dbPassword';
"@
    $sql += (Get-Content (Join-Path $Project 'docker\mysql\init.sql') -Raw)
    $sql += "GRANT ALL PRIVILEGES ON ``placify\_%``.* TO '$dbUser'@'localhost'; FLUSH PRIVILEGES;"
    $sql | & $client -uroot
    if ($LASTEXITCODE -ne 0) { throw 'Creating the CareerPilot databases failed' }
    Write-Host '  databases and user created'
    & (Join-Path $mysqlHome 'bin\mysqladmin.exe') -uroot shutdown 2>$null
    $mysqld.WaitForExit(30000) | Out-Null
}

Write-Host ''
Write-Host 'Setup complete. Start CareerPilot with:  powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1'
