$ErrorActionPreference = "Stop"
$basedir = "C:\Program Files\MySQL\MySQL Server 8.4"
$datadir = "D:\mysql-data\project_mgmt"
$mysqld = Join-Path $basedir "bin\mysqld.exe"
$mysqladmin = Join-Path $basedir "bin\mysqladmin.exe"

try {
    & $mysqladmin --protocol=tcp -P 3306 -u root -proot ping 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "MySQL already running on 3306"
        exit 0
    }
} catch {
}

Write-Host "Starting MySQL..."
Start-Process -FilePath $mysqld -ArgumentList @(
    "--console",
    "--basedir=$basedir",
    "--datadir=$datadir",
    "--port=3306",
    "--bind-address=127.0.0.1"
) -WindowStyle Hidden

for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        & $mysqladmin --protocol=tcp -P 3306 -u root -proot ping 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "MySQL ready"
            exit 0
        }
    } catch {
    }
}
Write-Host "MySQL did not become ready in time"
exit 1
