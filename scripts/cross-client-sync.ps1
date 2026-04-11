param(
    [string]$AvdName = "StatusFlow_API_34",
    [string]$OutputDir = "outputs/mobile-ui",
    [string]$ApiBaseUrl = "http://10.0.2.2:8000",
    [switch]$SkipBuild,
    [switch]$StartEmulator
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$sdkRoot = Join-Path $repoRoot ".local\android-sdk"
$jdkRoot = Join-Path $repoRoot ".local\jdk\jdk-17.0.18+8"
$androidUserHome = Join-Path $repoRoot ".local\android-home"
$adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulatorPath = Join-Path $sdkRoot "emulator\emulator.exe"
$gradleWrapper = Join-Path $repoRoot "apps\mobile\gradlew.bat"
$apkPath = Join-Path $repoRoot "apps\mobile\app\build\outputs\apk\debug\app-debug.apk"
$resolvedOutputDir = Join-Path $repoRoot $OutputDir
$dumpPath = Join-Path $resolvedOutputDir "cross-client-mobile.xml"
$screenshotPath = Join-Path $resolvedOutputDir "cross-client-mobile.png"
$remoteDumpPath = "/sdcard/statusflow-cross-client.xml"
$remoteScreenshotPath = "/sdcard/statusflow-cross-client.png"
$webApiBaseUrl = $ApiBaseUrl.Replace("10.0.2.2", "localhost")
$originalHome = $env:HOME
$originalUserProfile = $env:USERPROFILE
$originalHomeDrive = $env:HOMEDRIVE
$originalHomePath = $env:HOMEPATH
$originalAndroidUserHome = $env:ANDROID_USER_HOME
$originalAndroidSdkHome = $env:ANDROID_SDK_HOME

foreach ($requiredPath in @($adbPath, $gradleWrapper, $jdkRoot)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Required path not found: $requiredPath"
    }
}

if ($StartEmulator -and -not (Test-Path $emulatorPath)) {
    throw "Android emulator binary not found at $emulatorPath"
}

New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null
New-Item -ItemType Directory -Force -Path $androidUserHome | Out-Null

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [string[]]$ArgumentList = @(),

        [string]$FailureMessage = "Native command failed."
    )

    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage Exit code: $LASTEXITCODE"
    }
}

function Enable-AdbEnvironment {
    $env:HOME = $androidUserHome
    $env:USERPROFILE = $androidUserHome
    $env:HOMEDRIVE = [System.IO.Path]::GetPathRoot($androidUserHome).TrimEnd('\')
    $env:HOMEPATH = $androidUserHome.Substring($env:HOMEDRIVE.Length)
    $env:ANDROID_USER_HOME = $androidUserHome
    $env:ANDROID_SDK_HOME = $androidUserHome
}

function Restore-DefaultHomeEnvironment {
    $env:HOME = $originalHome
    $env:USERPROFILE = $originalUserProfile
    $env:HOMEDRIVE = $originalHomeDrive
    $env:HOMEPATH = $originalHomePath
    $env:ANDROID_USER_HOME = $originalAndroidUserHome
    $env:ANDROID_SDK_HOME = $originalAndroidSdkHome
}

function Get-OnlineDeviceCount {
    Enable-AdbEnvironment
    $lines = & $adbPath devices
    return @(
        $lines |
            Select-Object -Skip 1 |
            Where-Object { $_ -match "device$" -and $_ -notmatch "^List of devices" }
    ).Count
}

function Wait-ForBoot {
    Enable-AdbEnvironment
    & $adbPath wait-for-device | Out-Null
    do {
        Start-Sleep -Seconds 2
        $bootCompleted = (& $adbPath shell getprop sys.boot_completed).Trim()
    } while ($bootCompleted -ne "1")
}

function Ensure-Device {
    if ((Get-OnlineDeviceCount) -eq 0) {
        if (-not $StartEmulator) {
            throw "No Android device/emulator is online. Re-run with -StartEmulator or start one manually."
        }

        Write-Host "Starting emulator $AvdName..."
        Start-Process -FilePath $emulatorPath -ArgumentList "-avd $AvdName -no-snapshot -no-window -gpu swiftshader_indirect" -WindowStyle Hidden
        Wait-ForBoot
        return
    }

    Wait-ForBoot
}

function Build-And-InstallApk {
    if (-not $SkipBuild) {
        Write-Host "Building debug APK..."
        Push-Location (Join-Path $repoRoot "apps\mobile")
        try {
        $env:JAVA_HOME = $jdkRoot
        $env:ANDROID_SDK_ROOT = $sdkRoot
        $env:PATH = "$jdkRoot\bin;$sdkRoot\platform-tools;$env:PATH"
        Restore-DefaultHomeEnvironment
        Invoke-NativeCommand -FilePath $gradleWrapper -ArgumentList @("assembleDebug") -FailureMessage "Gradle debug build failed."
    } finally {
        Pop-Location
        }
    }

    if (-not (Test-Path $apkPath)) {
        throw "APK not found at $apkPath"
    }

    Write-Host "Installing debug APK..."
    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("install", "-r", $apkPath) -FailureMessage "APK install failed." | Out-Null
}

function Start-MobileApp {
    Write-Host "Launching mobile app..."
    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "am", "start", "-n", "com.statusflow.mobile/.MainActivity") -FailureMessage "App launch failed." | Out-Null
    Start-Sleep -Seconds 5
}

function Stop-MobileApp {
    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "am", "force-stop", "com.statusflow.mobile") -FailureMessage "App stop failed." | Out-Null
}

function Get-UiXml {
    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "uiautomator", "dump", $remoteDumpPath) -FailureMessage "UI hierarchy dump failed." | Out-Null
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("pull", $remoteDumpPath, $dumpPath) -FailureMessage "Failed to pull UI dump." | Out-Null
    return [xml](Get-Content -Path $dumpPath -Raw)
}

function Save-Screenshot {
    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "screencap", "-p", $remoteScreenshotPath) -FailureMessage "Screenshot capture failed." | Out-Null
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("pull", $remoteScreenshotPath, $screenshotPath) -FailureMessage "Failed to pull screenshot." | Out-Null
}

function Get-NodeCenter {
    param([Parameter(Mandatory = $true)][string]$Bounds)

    if ($Bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        throw "Unable to parse bounds: $Bounds"
    }

    $x = [int](($matches[1] + $matches[3]) / 2)
    $y = [int](($matches[2] + $matches[4]) / 2)

    return @{ X = $x; Y = $y }
}

function Get-UiNodeByText {
    param(
        [Parameter(Mandatory = $true)][xml]$Xml,
        [Parameter(Mandatory = $true)][string]$Text,
        [switch]$Contains
    )

    $nodes = @($Xml.SelectNodes("//node"))
    foreach ($node in $nodes) {
        $nodeText = [string]$node.text
        $contentDesc = [string]$node.'content-desc'

        if ($Contains) {
            if ($nodeText -like "*$Text*" -or $contentDesc -like "*$Text*") {
                return $node
            }
        } else {
            if ($nodeText -eq $Text -or $contentDesc -eq $Text) {
                return $node
            }
        }
    }

    return $null
}

function Get-FirstVisibleOrderCardNode {
    param([Parameter(Mandatory = $true)][xml]$Xml)

    $nodes = @($Xml.SelectNodes("//node"))
    $fallbackNode = $null
    foreach ($node in $nodes) {
        $contentDesc = [string]$node.'content-desc'
        if ($contentDesc -like "Open order *") {
            if (-not $fallbackNode) {
                $fallbackNode = $node
            }

            $bounds = [string]$node.bounds
            if ($bounds -match "\[(\d+),(\d+)\]\[(\d+),(\d+)\]" -and [int]$matches[2] -ge 400) {
                return $node
            }
        }
    }

    return $fallbackNode
}

function Invoke-UiTapNode {
    param([Parameter(Mandatory = $true)]$Node)

    Enable-AdbEnvironment
    $tapNode = $Node
    while ($tapNode -and [string]$tapNode.clickable -ne "true" -and $tapNode.ParentNode) {
        $tapNode = $tapNode.ParentNode
    }

    $center = Get-NodeCenter -Bounds ([string]$tapNode.bounds)
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "input", "tap", "$($center.X)", "$($center.Y)") -FailureMessage "UI tap failed." | Out-Null
    Start-Sleep -Milliseconds 700
}

function Scroll-Down {
    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "input", "swipe", "540", "1850", "540", "900", "250") -FailureMessage "Scroll gesture failed." | Out-Null
    Start-Sleep -Seconds 1
}

function Scroll-Up {
    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "input", "swipe", "540", "900", "540", "1850", "250") -FailureMessage "Scroll up gesture failed." | Out-Null
    Start-Sleep -Seconds 1
}

function Refresh-MobileQueue {
    try {
        Tap-UiText -Text "Refresh" -TimeoutSec 3
        return
    } catch {
        Write-Host "Refresh button not visible in the current viewport, searching lower in the queue screen..."
    }

    try {
        Tap-UiText -Text "Refresh" -AllowScroll -TimeoutSec 12
        Scroll-Up
        Scroll-Up
        return
    } catch {
        Write-Host "Refresh button was not reachable, falling back to pull-to-refresh gesture..."
    }

    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "input", "swipe", "540", "760", "540", "1880", "350") -FailureMessage "Pull-to-refresh gesture failed." | Out-Null
    Start-Sleep -Seconds 3
    Scroll-Up
    Scroll-Up
    Scroll-Up
}

function Convert-ToAdbInputText {
    param([Parameter(Mandatory = $true)][string]$Text)

    return ($Text -replace ' ', '%s')
}

function Type-UiText {
    param([Parameter(Mandatory = $true)][string]$Text)

    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "input", "text", (Convert-ToAdbInputText -Text $Text)) -FailureMessage "UI text entry failed." | Out-Null
    Start-Sleep -Seconds 1
}

function Filter-MobileQueue {
    param([Parameter(Mandatory = $true)][string]$Query)

    Tap-UiText -Text "Search code, title, or customer" -TimeoutSec 10
    Type-UiText -Text $Query
}

function Wait-ForUiText {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [switch]$Contains,
        [int]$TimeoutSec = 30,
        [switch]$AllowScroll
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $xml = Get-UiXml
        $node = Get-UiNodeByText -Xml $xml -Text $Text -Contains:$Contains
        if ($node) {
            return @{ Xml = $xml; Node = $node }
        }

        if ($AllowScroll) {
            Scroll-Down
        } else {
            Start-Sleep -Seconds 1
        }
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for mobile UI text: $Text"
}

function Tap-UiText {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [switch]$Contains,
        [int]$TimeoutSec = 20,
        [switch]$AllowScroll
    )

    $result = Wait-ForUiText -Text $Text -Contains:$Contains -TimeoutSec $TimeoutSec -AllowScroll:$AllowScroll
    Invoke-UiTapNode -Node $result.Node
}

function Tap-FirstVisibleOrderCard {
    $xml = Get-UiXml
    $node = Get-FirstVisibleOrderCardNode -Xml $xml
    if (-not $node) {
        throw "Unable to find a visible order card in the current mobile UI."
    }

    Invoke-UiTapNode -Node $node
}

function Tap-SelectedOrderCard {
    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "input", "tap", "540", "2000") -FailureMessage "Selected order card tap failed." | Out-Null
    Start-Sleep -Seconds 2
}

function Invoke-ProcessWithStdin {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][string]$InputText,
        [string]$FailureMessage = "Native command with stdin failed.",
        [int]$TimeoutMs = 15000
    )

    $quotedArguments = $ArgumentList | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + ($_ -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
        } else {
            $_
        }
    }

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = [string]::Join(' ', $quotedArguments)
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo

    try {
        if (-not $process.Start()) {
            throw "Process failed to start."
        }

        $process.StandardInput.Write($InputText)
        $process.StandardInput.Close()

        if (-not $process.WaitForExit($TimeoutMs)) {
            try {
                $process.Kill()
            } catch {
            }

            throw "$FailureMessage Timed out after $TimeoutMs ms."
        }

        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()

        if ($process.ExitCode -ne 0) {
            $details = ($stderr, $stdout | Where-Object { $_ -and $_.Trim().Length -gt 0 }) -join [Environment]::NewLine
            if ($details) {
                throw "$FailureMessage Exit code: $($process.ExitCode)`n$details"
            }

            throw "$FailureMessage Exit code: $($process.ExitCode)"
        }

        return @{
            StdOut = $stdout
            StdErr = $stderr
        }
    } finally {
        $process.Dispose()
    }
}

function Write-MobileSession {
    param([Parameter(Mandatory = $true)]$Session)

    $sessionPrefsDir = "/data/user/0/com.statusflow.mobile/shared_prefs"
    $sessionPrefsPath = "$sessionPrefsDir/statusflow_session.xml"
    $escapedToken = [System.Security.SecurityElement]::Escape([string]$Session.access_token)
    $escapedEmail = [System.Security.SecurityElement]::Escape([string]$Session.user.email)
    $escapedName = [System.Security.SecurityElement]::Escape([string]$Session.user.name)
    $escapedRole = [System.Security.SecurityElement]::Escape([string]$Session.user.role)
    $sessionXml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="access_token">$escapedToken</string>
    <string name="email">$escapedEmail</string>
    <string name="name">$escapedName</string>
    <string name="role">$escapedRole</string>
</map>
"@

    Enable-AdbEnvironment
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "run-as", "com.statusflow.mobile", "mkdir", "-p", $sessionPrefsDir) -FailureMessage "Failed to prepare mobile shared_prefs." | Out-Null
    Invoke-ProcessWithStdin `
        -FilePath $adbPath `
        -ArgumentList @("shell", "run-as", "com.statusflow.mobile", "tee", $sessionPrefsPath) `
        -InputText $sessionXml `
        -FailureMessage "Failed to seed mobile session." | Out-Null
    Invoke-NativeCommand -FilePath $adbPath -ArgumentList @("shell", "run-as", "com.statusflow.mobile", "cat", $sessionPrefsPath) -FailureMessage "Failed to verify seeded mobile session." | Out-Null
}

function Ensure-MobileSignedIn {
    $xml = Get-UiXml
    $loginNode = Get-UiNodeByText -Xml $xml -Text "Sign in to the live queue"
    if (-not $loginNode) {
        return
    }

    $deadline = (Get-Date).AddSeconds(30)
    do {
        $xml = Get-UiXml
        $loginStillVisible = Get-UiNodeByText -Xml $xml -Text "Sign in to the live queue"
        $signedInMarker = Get-UiNodeByText -Xml $xml -Text "Signed in"
        $queueSnapshotMarker = Get-UiNodeByText -Xml $xml -Text "Queue snapshot"

        if (-not $loginStillVisible -and ($signedInMarker -or $queueSnapshotMarker)) {
            return
        }

        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for the mobile app to leave the login screen."
}

function Invoke-ApiJson {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body,
        [string]$Token
    )

    $headers = @{}
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }

    $params = @{
        Method = $Method
        Uri = "$webApiBaseUrl$Path"
        Headers = $headers
        ContentType = "application/json"
    }

    if ($PSBoundParameters.ContainsKey("Body")) {
        $params.Body = ($Body | ConvertTo-Json -Depth 5)
    }

    return Invoke-RestMethod @params
}

Ensure-Device
Build-And-InstallApk

Write-Host "Authenticating against live API..."
$session = Invoke-ApiJson -Method "POST" -Path "/auth/login" -Body @{
    email = "operator@example.com"
    password = "operator123"
}
$token = $session.access_token
$users = Invoke-ApiJson -Method "GET" -Path "/users" -Token $token
$customer = $users | Where-Object { $_.role -eq "customer" } | Select-Object -First 1
$operator = $users | Where-Object { $_.role -eq "operator" } | Select-Object -First 1

if (-not $customer -or -not $operator) {
    throw "Unable to find seeded customer/operator users."
}

Write-Host "Seeding mobile operator session..."
Stop-MobileApp
Write-MobileSession -Session $session
Start-MobileApp
Ensure-MobileSignedIn

$unique = Get-Date -Format "yyyyMMddHHmmss"
$orderTitle = "Cross-client sync $unique"
$commentBody = "Cross-client note $unique"

Write-Host "Creating and mutating order through the live API..."
$created = Invoke-ApiJson -Method "POST" -Path "/orders" -Token $token -Body @{
    title = $orderTitle
    description = "Created by cross-client sync smoke."
    customer_id = $customer.id
}
$orderCode = if ($created.PSObject.Properties.Match("code").Count -gt 0 -and $created.code) {
    [string]$created.code
} else {
    $null
}
$orderUiMarker = if ($orderCode) { $orderCode } else { $orderTitle }

Invoke-ApiJson -Method "POST" -Path "/orders/$($created.id)/status-transitions" -Token $token -Body @{
    changed_by_id = $operator.id
    to_status = "in_review"
    reason = "Cross-client sync verification moved order to In review."
} | Out-Null

Invoke-ApiJson -Method "POST" -Path "/orders/$($created.id)/comments" -Token $token -Body @{
    author_id = $operator.id
    body = $commentBody
} | Out-Null

$createdDetail = Invoke-ApiJson -Method "GET" -Path "/orders/$($created.id)" -Token $token
if (-not $orderCode -and $createdDetail.PSObject.Properties.Match("code").Count -gt 0 -and $createdDetail.code) {
    $orderCode = [string]$createdDetail.code
}
$orderUiMarker = if ($orderCode) { $orderCode } else { $orderTitle }
Write-Host "Backend created order marker: $orderUiMarker"

Write-Host "Reloading mobile app and verifying synced order..."
Stop-MobileApp
Start-MobileApp
Ensure-MobileSignedIn
if ($orderCode) {
    Wait-ForUiText -Text $orderCode -TimeoutSec 20 | Out-Null
}
Scroll-Down
Tap-SelectedOrderCard

Wait-ForUiText -Text "Selected order" -TimeoutSec 20 | Out-Null
Wait-ForUiText -Text "In Review" -TimeoutSec 20 | Out-Null
Wait-ForUiText -Text "Post comment" -AllowScroll -TimeoutSec 20 | Out-Null
Wait-ForUiText -Text $commentBody -Contains -AllowScroll -TimeoutSec 40 | Out-Null

Save-Screenshot

Write-Host "Cross-client sync check passed."
Write-Host "Verified mobile received order: $orderUiMarker"
Write-Host "Verified mobile comment: $commentBody"
Write-Host "Screenshot: $screenshotPath"
Write-Host "UI dump: $dumpPath"
