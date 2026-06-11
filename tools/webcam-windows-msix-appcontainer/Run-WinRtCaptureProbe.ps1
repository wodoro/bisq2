<#
.SYNOPSIS
    Builds and runs the WinRT camera capture probe, first bare and then inside the Bisq webcam AppContainer.

.DESCRIPTION
    Validates the in-sandbox WinRT capture backend (bisq_webcam_winrt.dll + WinRtFrameGrabber) on real Windows.

    Stage 0 (bare): proves WinRT can open the camera and deliver frames at all in this VM (camera present + consent).
    Stage 1 (AppContainer): proves it still works with TokenIsAppContainer=true and only the 'webcam' capability -
        the case where the old OpenCV/MSMF path failed. This is the linchpin the design rests on.

    Run from any PowerShell; the script locates the Visual Studio C++ build environment itself.

.PARAMETER JavaHome
    JDK 21 home. Defaults to $env:JAVA_HOME. Needs include/ (JNI headers) for the build and bin/java.exe to run.

.PARAMETER Frames
    Number of frames each probe stage tries to grab. Default 15.

.PARAMETER Device
    Camera device index. Default 0.

.PARAMETER SkipBare
    Skip Stage 0 and only run the AppContainer stage.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\webcam-windows-msix-appcontainer\Run-WinRtCaptureProbe.ps1
#>
[CmdletBinding()]
param(
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

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$WebcamAppDir = Join-Path $RepoRoot 'apps\desktop\webcam-app'
$CppSource = Join-Path $WebcamAppDir 'src\main\c\bisq_webcam_winrt.cpp'
$ProbeMainClass = 'bisq.webcam.service.capture.WinRtCaptureProbe'

if (-not $JavaHome) {
    throw 'JavaHome not set. Pass -JavaHome <jdk21> or set $env:JAVA_HOME.'
}
$JavaExe = Join-Path $JavaHome 'bin\java.exe'
$JniInclude = Join-Path $JavaHome 'include'
$JniIncludeWin32 = Join-Path $JniInclude 'win32'
foreach ($path in @($JavaExe, $JniInclude, $JniIncludeWin32, $CppSource)) {
    if (-not (Test-Path $path)) { throw "Missing required path: $path" }
}

# --- Locate the Visual Studio x64 native build environment (vcvars64.bat) ---
function Find-VcVars64 {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    if (-not (Test-Path $vswhere)) {
        throw "vswhere.exe not found. Install Visual Studio Build Tools 2022 with the Desktop C++ workload."
    }
    $vsRoot = & $vswhere -latest -products * `
        -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
        -property installationPath
    if (-not $vsRoot) { throw 'No Visual Studio C++ toolset found (VC.Tools.x86.x64).' }
    $vcvars = Join-Path $vsRoot 'VC\Auxiliary\Build\vcvars64.bat'
    if (-not (Test-Path $vcvars)) { throw "vcvars64.bat not found under $vsRoot." }
    return $vcvars
}

# --- Stage A: compile the JNI shim to bisq_webcam_winrt.dll ---
Write-Section 'Building bisq_webcam_winrt.dll'
$VcVars = Find-VcVars64
$OutDir = Join-Path $WebcamAppDir 'build\native\windows'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$DllPath = Join-Path $OutDir 'bisq_webcam_winrt.dll'

$clArgs = @(
    '/nologo', '/LD', '/EHsc', '/MT', '/O2', '/W3', '/std:c++17',
    '/DUNICODE', '/D_UNICODE', '/D_WIN32_WINNT=0x0A00',
    '/I', "`"$JniInclude`"", '/I', "`"$JniIncludeWin32`"",
    "/Fe:`"$DllPath`"", "`"$CppSource`"",
    '/link', 'windowsapp.lib'
) -join ' '

# Source vcvars then compile in the same cmd session; build in $OutDir so .obj artifacts stay there.
$buildCmd = "call `"$VcVars`" >nul && cd /d `"$OutDir`" && cl.exe $clArgs"
cmd.exe /c $buildCmd
if ($LASTEXITCODE -ne 0) { throw "cl.exe failed with exit code $LASTEXITCODE" }
if (-not (Test-Path $DllPath)) { throw "DLL was not produced at $DllPath" }
Write-Host "Built: $DllPath" -ForegroundColor Green

# --- Stage B: build the shadow jar (contains javacv/javacpp/zxing + opencv natives) ---
Write-Section 'Building webcam-app shadowJar'
& (Join-Path $RepoRoot 'gradlew.bat') ':apps:desktop:webcam-app:shadowJar' '-q'
if ($LASTEXITCODE -ne 0) { throw "gradlew shadowJar failed with exit code $LASTEXITCODE" }
$ShadowJar = Get-ChildItem (Join-Path $WebcamAppDir 'build\libs') -Filter '*-all.jar' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $ShadowJar) { throw 'shadowJar (*-all.jar) not found under build\libs.' }
Write-Host "Shadow jar: $($ShadowJar.FullName)" -ForegroundColor Green

# --- Stage 0: bare run (no sandbox) ---
if (-not $SkipBare) {
    Write-Section 'Stage 0: bare WinRT capture (no AppContainer)'
    & $JavaExe "-Djava.library.path=$OutDir" '-cp' $ShadowJar.FullName `
        $ProbeMainClass '--device' $Device '--frames' $Frames
    Write-Host "Stage 0 exit code: $LASTEXITCODE" -ForegroundColor Yellow
}

# --- Stage 1: AppContainer run via the packaged content dir + launcher ---
Write-Section 'Stage 1: WinRT capture inside the AppContainer'
& (Join-Path $RepoRoot 'gradlew.bat') ':apps:desktop:webcam-app:prepareWindowsWebcamAppContent' '-q'
if ($LASTEXITCODE -ne 0) { throw "gradlew prepareWindowsWebcamAppContent failed with exit code $LASTEXITCODE" }

$ContentDir = Join-Path $WebcamAppDir 'build\packaging\windows-app-content\webcam'
$Launcher = Join-Path $ContentDir 'bisq-webcam-appcontainer-launcher.exe'
$ContentJar = Get-ChildItem $ContentDir -Filter '*-all.jar' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
foreach ($path in @($Launcher, $ContentDir)) {
    if (-not (Test-Path $path)) { throw "Missing AppContainer input: $path" }
}
if (-not $ContentJar) { throw "shadowJar not found in content dir $ContentDir." }

# Mirror WindowsWebcamSandboxPolicy: webcam capability, read grants for the JDK and content dir, JavaCPP cache
# disabled + java.library.path pointed at the read-only content dir where all natives (incl. the WinRT DLL) live.
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

Write-Section 'Verdict'
if ($AppContainerExit -eq 0) {
    Write-Host 'PASS: WinRT capture works inside the AppContainer. The single-sandbox design holds.' -ForegroundColor Green
} else {
    Write-Host "FAIL: AppContainer capture did not produce frames (exit $AppContainerExit)." -ForegroundColor Red
    Write-Host 'Check the printed result= line above. If Stage 0 passed but Stage 1 failed, WinRT camera open is'
    Write-Host 'blocked in the AppContainer too, and a full-trust broker would be required on Windows.'
}
exit $AppContainerExit
