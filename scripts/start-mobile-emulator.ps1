param(
    [string]$AvdName = "StatusFlow_API_34"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$sdkRoot = Join-Path $repoRoot ".local\android-sdk"
$adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulatorPath = Join-Path $sdkRoot "emulator\emulator.exe"

if (-not (Test-Path $emulatorPath)) {
    throw "Android emulator binary not found at $emulatorPath"
}

if (-not (Test-Path $adbPath)) {
    throw "adb.exe not found at $adbPath"
}

Start-Process -FilePath $emulatorPath -ArgumentList "-avd $AvdName -no-snapshot -no-window -gpu swiftshader_indirect" -WindowStyle Hidden

Write-Host "Waiting for emulator device..."
& $adbPath wait-for-device | Out-Null

do {
    Start-Sleep -Seconds 2
    $bootCompleted = (& $adbPath shell getprop sys.boot_completed).Trim()
} while ($bootCompleted -ne "1")

Write-Host "Emulator is ready: $AvdName"
