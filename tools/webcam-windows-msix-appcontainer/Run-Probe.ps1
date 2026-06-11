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
& $Launcher `
    '--profile-name' 'bisq.webcam' `
    '--capability' 'webcam' `
    '--grant-read' $JavaHome `
    '--grant-read' $ContentDir `
    '--appcontainer-storage-scope' 'winrt-probe' `
    '--javacpp-cache-scope' 'winrt-probe' `
    '--' `
    $JavaExe `
    '-Dorg.bytedeco.javacpp.cacheLibraries=false' `
    '-Dorg.bytedeco.javacpp.pathsFirst=true' `
    "-Djava.library.path=$ContentDir" `
    '-cp' $ContentJar.FullName `
    $ProbeMainClass '--device' $Device '--frames' $Frames
$AppContainerExit = $LASTEXITCODE
Write-Host "Stage 1 exit code: $AppContainerExit" -ForegroundColor Yellow

# --- Verdict ---
Write-Section 'Verdict'
if (-not $SkipBare -and $BareExit -ne 0) {
    Write-Host "Stage 0 (bare) FAILED (exit $BareExit) - WinRT could not capture even without the sandbox." -ForegroundColor Red
    Write-Host 'Fix the VM camera first: attach a webcam or start OBS Virtual Camera, and allow desktop apps in'
    Write-Host 'Settings > Privacy & security > Camera. The sandbox result is meaningless until Stage 0 passes.'
} elseif ($AppContainerExit -eq 0) {
    Write-Host 'PASS: WinRT capture works INSIDE the AppContainer.' -ForegroundColor Green
    Write-Host 'The single-sandbox Windows design holds: capture + decode + preview stay caged, no broker needed.'
} else {
    Write-Host "FAIL: bare capture worked but AppContainer capture did not (exit $AppContainerExit)." -ForegroundColor Red
    Write-Host 'WinRT camera-open is blocked in the AppContainer too -> a full-trust capture broker would be'
    Write-Host 'required on Windows. Capture the result= lines above for the report.'
}
exit $AppContainerExit
