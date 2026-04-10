param(
    [string]$ApkPath = "apps/mobile/app/build/outputs/apk/debug/app-debug.apk",
    [string]$OutputPath = "outputs/mobile-ui/mobile-home.png"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$sdkRoot = Join-Path $repoRoot ".local\android-sdk"
$adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
$resolvedApkPath = Join-Path $repoRoot $ApkPath
$resolvedOutputPath = Join-Path $repoRoot $OutputPath
$outputDir = Split-Path $resolvedOutputPath -Parent

if (-not (Test-Path $adbPath)) {
    throw "adb.exe not found at $adbPath"
}

if (-not (Test-Path $resolvedApkPath)) {
    throw "APK not found at $resolvedApkPath"
}

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

& $adbPath install -r $resolvedApkPath
& $adbPath shell am start -n "com.statusflow.mobile/.MainActivity" | Out-Null
Start-Sleep -Seconds 8
& $adbPath exec-out screencap -p > $resolvedOutputPath

Write-Host "Saved screenshot to $resolvedOutputPath"
