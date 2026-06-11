<#
.SYNOPSIS
    Runs the WinRT capture probe against the already-built artifacts, bare then inside the AppContainer, and reports.

.DESCRIPTION
    Run this AFTER Provision-And-Build.ps1. It executes only the built files (no compiler, no Gradle build), so it is
    fast and works offline:
      Stage 0 (bare): proves WinRT can open the camera and deliver frames at all (camera present + consent granted).
      Stage 1 (AppContainer): runs the same probe inside the webcam AppContainer with only the 'webcam' capability -
          the case the old OpenCV/MSMF path failed. Frames flowing here = the in-sandbox design is proven on Windows.

    If the VM has no physical webcam, start OBS Studio > Start Virtual Camera (point a QR image at it) before running.

.PARAMETER RepoRoot
    Repo clone root. Default: C:\bisq2 (matches Provision-And-Build.ps1's default).

.PARAMETER JavaHome
    JDK 21 home. Default: $env:JAVA_HOME.

.PARAMETER Frames
    Frames to grab per stage. Default 15.

.PARAMETER Device
    Camera device index. Default 0.

.PARAMETER SkipBare
    Skip Stage 0 (run only the AppContainer stage).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File Run-Probe.ps1
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = 'C:\bisq2',
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$Frames = 15,
    [int]$Device = 0,
    [switch]$SkipBare
)

$ErrorActionPreference = 'Stop'

function Write-Section([string]$Title) {
    Write-Host ''
    Write-Host "==== $Title ====" -ForegroundColor Cyan
}

$ProbeMainClass = 'bisq.webcam.service.capture.WinRtCaptureProbe'

if (-not $JavaHome) { throw 'JAVA_HOME not set. Pass -JavaHome <jdk21> or open a new shell after Provision-And-Build.ps1.' }
$JavaExe = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path $JavaExe)) { throw "java.exe not found at $JavaExe" }

$ContentDir = Join-Path $RepoRoot 'apps\desktop\webcam-app\build\packaging\windows-app-content\webcam'
if (-not (Test-Path $ContentDir)) {
    throw "Built content dir not found: $ContentDir`nRun Provision-And-Build.ps1 first."
}
$Launcher = Join-Path $ContentDir 'bisq-webcam-appcontainer-launcher.exe'
$Dll = Join-Path $ContentDir 'bisq_webcam_winrt.dll'
$ContentJar = Get-ChildItem $ContentDir -Filter '*-all.jar' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
foreach ($p in @($Launcher, $Dll)) {
    if (-not (Test-Path $p)) { throw "Missing built artifact: $p" }
}
if (-not $ContentJar) { throw "shadow jar (*-all.jar) not found in $ContentDir" }

Write-Host "Content dir: $ContentDir"
Write-Host "WinRT DLL:   $Dll"
Write-Host "Shadow jar:  $($ContentJar.Name)"

# The AppContainer launcher grants read access by editing the DACL of each --grant-read path AND its parent chain. A
# JDK under C:\Program Files would require ACL-editing C:\Program Files (admin/WRITE_DAC) and fail with "Access is
# denied". Mirror the production per-user layout by granting a user-owned JDK copy whose parent chain stays inside the
# repo (user-owned), so no elevation is needed. (Stage 0 needs no grants, so it keeps using the original JDK.)
$Stage1JavaHome = $JavaHome
$Stage1JavaExe = $JavaExe
$programFiles = $env:ProgramFiles
if ($programFiles -and $JavaHome.StartsWith($programFiles, [System.StringComparison]::OrdinalIgnoreCase)) {
    $ProbeJdk = Join-Path $RepoRoot 'probe-runtime\jdk'
    if (-not (Test-Path (Join-Path $ProbeJdk 'bin\java.exe'))) {
        Write-Host "JDK is under '$programFiles'; copying to a user-owned dir for AppContainer grants:" -ForegroundColor Yellow
        Write-Host "  $ProbeJdk (one-time, ~300MB)" -ForegroundColor Yellow
        New-Item -ItemType Directory -Force -Path $ProbeJdk | Out-Null
        & robocopy $JavaHome $ProbeJdk /MIR /NFL /NDL /NJH /NJS /NC /NS /NP | Out-Null
        # robocopy exit codes < 8 indicate success.
        if ($LASTEXITCODE -ge 8) { throw "robocopy of JDK failed (exit $LASTEXITCODE)." }
        $global:LASTEXITCODE = 0
    }
    $Stage1JavaHome = $ProbeJdk
    $Stage1JavaExe = Join-Path $ProbeJdk 'bin\java.exe'
    Write-Host "Stage 1 JDK: $Stage1JavaHome" -ForegroundColor Green
}

# --- Stage 0: bare (no sandbox) ---
$BareExit = $null
if (-not $SkipBare) {
    Write-Section 'Stage 0: bare WinRT capture (no AppContainer)'
    & $JavaExe "-Djava.library.path=$ContentDir" '-cp' $ContentJar.FullName `
        $ProbeMainClass '--device' $Device '--frames' $Frames
    $BareExit = $LASTEXITCODE
    Write-Host "Stage 0 exit code: $BareExit" -ForegroundColor Yellow
}

# --- Stage 1: inside the AppContainer ---
# Mirror WindowsWebcamSandboxPolicy: webcam capability, read grants for the JDK and content dir, JavaCPP cache disabled
# and java.library.path pointed at the read-only content dir holding all natives (OpenCV + the WinRT DLL).
Write-Section 'Stage 1: WinRT capture inside the AppContainer'
$stage1Output = & $Launcher `
    '--profile-name' 'bisq.webcam' `
    '--capability' 'webcam' `
    '--grant-read' $Stage1JavaHome `
    '--grant-read' $ContentDir `
    '--appcontainer-storage-scope' 'winrt-probe' `
    '--javacpp-cache-scope' 'winrt-probe' `
    '--' `
    $Stage1JavaExe `
    '-Dorg.bytedeco.javacpp.cacheLibraries=false' `
    '-Dorg.bytedeco.javacpp.pathsFirst=true' `
    "-Djava.library.path=$ContentDir" `
    '-cp' $ContentJar.FullName `
    $ProbeMainClass '--device' $Device '--frames' $Frames 2>&1
$AppContainerExit = $LASTEXITCODE
$stage1Output | ForEach-Object { Write-Host $_ }
$stage1Text = ($stage1Output | Out-String)
Write-Host "Stage 1 exit code: $AppContainerExit" -ForegroundColor Yellow

# Did the launcher's own sandbox setup fail before the JVM/WinRT ever ran? Those failures are not camera/sandbox
# verdicts - they are environment problems with this probe run.
$setupFailed = ($stage1Text -match 'Access is denied') -or
    ($stage1Text -match 'Failed to .*(grant|AppContainer|profile|capability|token)') -or
    ($stage1Text -match 'Failed to (create|launch|prepare)')
$frameworkReached = $stage1Text -match 'result='

# --- Verdict ---
Write-Section 'Verdict'
if (-not $SkipBare -and $BareExit -ne 0) {
    Write-Host "Stage 0 (bare) FAILED (exit $BareExit) - WinRT could not capture even without the sandbox." -ForegroundColor Red
    Write-Host 'Fix the VM camera first: attach a webcam or start OBS Virtual Camera, and allow desktop apps in'
    Write-Host 'Settings > Privacy & security > Camera. The sandbox result is meaningless until Stage 0 passes.'
} elseif ($AppContainerExit -eq 0 -and ($stage1Text -match 'result=frames_flowing')) {
    Write-Host 'PASS: WinRT capture works INSIDE the AppContainer.' -ForegroundColor Green
    Write-Host 'The single-sandbox Windows design holds: capture + decode + preview stay caged, no broker needed.'
} elseif ($setupFailed -and -not $frameworkReached) {
    Write-Host "INCONCLUSIVE: the AppContainer launcher failed during sandbox setup (exit $AppContainerExit), before" -ForegroundColor Yellow
    Write-Host 'any camera access. This is NOT a camera/sandbox verdict - it is a launcher environment problem.'
    Write-Host 'If you see "Access is denied" on a Program Files path, a --grant-read target needs ACL edits the'
    Write-Host 'current user cannot make. This script copies a Program Files JDK to a user-owned dir to avoid that;'
    Write-Host 'if it still fails, re-run from an elevated PowerShell, or check which path is denied above.'
} elseif ($stage1Text -match 'result=camera_open_failed') {
    Write-Host 'FAIL: the JVM ran inside the AppContainer but WinRT could NOT open the camera there.' -ForegroundColor Red
    Write-Host 'Bare capture worked, so WinRT camera-open is blocked specifically inside the sandbox -> a full-trust'
    Write-Host 'capture broker would be required on Windows. This is the real negative signal.'
} else {
    Write-Host "UNCLEAR: Stage 1 exit $AppContainerExit and no decisive result= line. Inspect the output above." -ForegroundColor Yellow
}

if ($stage1Text -match 'meanByte=1\b' -or $BareExit -eq 0 -and $stage1Text -notmatch 'meanByte=[2-9]') {
    Write-Host ''
    Write-Host 'Note: frames look near-black (low meanByte). Capture works, but point a real camera or a running OBS' -ForegroundColor DarkYellow
    Write-Host 'Virtual Camera with visible content at it to validate a real image (and an actual QR decode).' -ForegroundColor DarkYellow
}
exit $AppContainerExit
