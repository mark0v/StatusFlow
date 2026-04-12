param(
    [string]$AvdName = "StatusFlow_API_34",
    [string]$ApkPath = "apps/mobile/app/build/outputs/apk/debug/app-debug.apk",
    [switch]$SkipInstall,
    [switch]$SkipLaunch,
    [switch]$RestartExisting
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$sdkRoot = Join-Path $repoRoot ".local\android-sdk"
$adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulatorPath = Join-Path $sdkRoot "emulator\emulator.exe"
$resolvedApkPath = Join-Path $repoRoot $ApkPath

foreach ($requiredPath in @($emulatorPath, $adbPath)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Required path not found: $requiredPath"
    }
}

function Wait-ForBoot {
    & $adbPath wait-for-device | Out-Null
    do {
        Start-Sleep -Seconds 2
        $bootCompleted = (& $adbPath shell getprop sys.boot_completed).Trim()
    } while ($bootCompleted -ne "1")
}

$emulatorProcesses = Get-Process -Name "emulator", "qemu-system-x86_64", "qemu-system-x86_64-headless" -ErrorAction SilentlyContinue
$headlessProcesses = Get-Process -Name "qemu-system-x86_64-headless" -ErrorAction SilentlyContinue
$hasOnlineDevice = @(
    (& $adbPath devices) |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "device$" -and $_ -notmatch "^List of devices" }
).Count -gt 0

if ($RestartExisting -or ($hasOnlineDevice -and $headlessProcesses)) {
    Write-Host "Stopping existing emulator processes before visible launch..."
    $emulatorProcesses | Stop-Process -Force
    Start-Sleep -Seconds 2
    $hasOnlineDevice = $false
}

if (-not $hasOnlineDevice) {
    Write-Host "Starting visible emulator $AvdName..."
    Start-Process -FilePath $emulatorPath -ArgumentList "-avd $AvdName -no-snapshot -gpu swiftshader_indirect"
    Wait-ForBoot
} else {
    Write-Host "Using existing online Android device/emulator."
    Wait-ForBoot
}

if (-not $SkipInstall) {
    if (-not (Test-Path $resolvedApkPath)) {
        throw "APK not found at $resolvedApkPath. Build it first or pass -SkipInstall."
    }

    Write-Host "Installing APK..."
    & $adbPath install -r $resolvedApkPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "APK install failed. Exit code: $LASTEXITCODE"
    }
}

if (-not $SkipLaunch) {
    Write-Host "Launching StatusFlow..."
    & $adbPath shell am start -n "com.statusflow.mobile/.MainActivity" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "App launch failed. Exit code: $LASTEXITCODE"
    }
}

Write-Host "Visible emulator is ready: $AvdName"
