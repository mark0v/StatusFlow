param(
    [string]$AvdName = "StatusFlow_API_34",
    [string]$ApkPath = "apps/mobile/app/build/outputs/apk/debug/app-debug.apk",
    [string]$OutputDir = "outputs/mobile-ui",
    [switch]$SkipBuild,
    [switch]$StartEmulator
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$sdkRoot = Join-Path $repoRoot ".local\android-sdk"
$jdkRoot = Join-Path $repoRoot ".local\jdk\jdk-17.0.18+8"
$adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulatorPath = Join-Path $sdkRoot "emulator\emulator.exe"
$gradleWrapper = Join-Path $repoRoot "apps\mobile\gradlew.bat"
$resolvedApkPath = Join-Path $repoRoot $ApkPath
$resolvedOutputDir = Join-Path $repoRoot $OutputDir
$screenshotPath = Join-Path $resolvedOutputDir "mobile-smoke.png"
$dumpPath = Join-Path $resolvedOutputDir "mobile-smoke.xml"

foreach ($requiredPath in @($adbPath, $gradleWrapper, $jdkRoot)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Required path not found: $requiredPath"
    }
}

if ($StartEmulator -and -not (Test-Path $emulatorPath)) {
    throw "Android emulator binary not found at $emulatorPath"
}

New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null

function Get-OnlineDeviceCount {
    $lines = & $adbPath devices
    return @(
        $lines |
            Select-Object -Skip 1 |
            Where-Object { $_ -match "device$" -and $_ -notmatch "^List of devices" }
    ).Count
}

function Wait-ForBoot {
    & $adbPath wait-for-device | Out-Null
    do {
        Start-Sleep -Seconds 2
        $bootCompleted = (& $adbPath shell getprop sys.boot_completed).Trim()
    } while ($bootCompleted -ne "1")
}

if ((Get-OnlineDeviceCount) -eq 0) {
    if (-not $StartEmulator) {
        throw "No Android device/emulator is online. Re-run with -StartEmulator or start one manually."
    }

    Write-Host "Starting emulator $AvdName..."
    Start-Process -FilePath $emulatorPath -ArgumentList "-avd $AvdName -no-snapshot -no-window -gpu swiftshader_indirect" -WindowStyle Hidden
    Wait-ForBoot
} else {
    Wait-ForBoot
}

if (-not $SkipBuild) {
    Write-Host "Building debug APK..."
    Push-Location (Join-Path $repoRoot "apps\mobile")
    try {
        $env:JAVA_HOME = $jdkRoot
        $env:ANDROID_SDK_ROOT = $sdkRoot
        $env:PATH = "$jdkRoot\bin;$sdkRoot\platform-tools;$env:PATH"
        & $gradleWrapper assembleDebug
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $resolvedApkPath)) {
    throw "APK not found at $resolvedApkPath"
}

Write-Host "Installing APK..."
& $adbPath install -r $resolvedApkPath | Out-Null

Write-Host "Launching mobile app..."
& $adbPath shell am start -n "com.statusflow.mobile/.MainActivity" | Out-Null
Start-Sleep -Seconds 8

$windowDump = (& $adbPath shell dumpsys window windows) | Out-String
$activityDump = (& $adbPath shell dumpsys activity activities) | Out-String
$focusMatch = $windowDump | Select-String "mCurrentFocus" | Select-Object -First 1
$currentFocus = if ($focusMatch) { $focusMatch.ToString() } else { $null }

if (-not $currentFocus) {
    $focusMatch = $activityDump | Select-String "topResumedActivity|mResumedActivity" | Select-Object -First 1
    $currentFocus = if ($focusMatch) { $focusMatch.ToString() } else { $null }
}

if (-not $currentFocus) {
    throw "Unable to determine the currently focused Android activity."
}

if ($currentFocus -notmatch "com\.statusflow\.mobile") {
    throw "StatusFlow activity is not focused. Current focus: $currentFocus"
}

Write-Host "Dumping UI hierarchy..."
& $adbPath shell uiautomator dump /sdcard/statusflow-smoke.xml | Out-Null
& $adbPath pull /sdcard/statusflow-smoke.xml $dumpPath | Out-Null
& $adbPath exec-out screencap -p > $screenshotPath

$dumpContent = Get-Content -Path $dumpPath -Raw
$requiredMarkers = @("StatusFlow", "Active queue", "Queue controls", "Queue snapshot")
$missingMarkers = @($requiredMarkers | Where-Object { $dumpContent -notmatch [regex]::Escape($_) })

if ($missingMarkers.Count -gt 0) {
    throw "Mobile smoke check failed. Missing UI markers: $($missingMarkers -join ', ')"
}

Write-Host "Mobile smoke check passed."
Write-Host "Screenshot: $screenshotPath"
Write-Host "UI dump: $dumpPath"
