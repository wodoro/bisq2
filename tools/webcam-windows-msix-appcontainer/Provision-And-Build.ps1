<#
.SYNOPSIS
    One-shot VM bootstrap: installs dependencies, clones the repo, and builds the WinRT webcam capture artifacts.

.DESCRIPTION
    Run this FIRST on a fresh Windows 10/11 VM, from an ELEVATED PowerShell (Run as Administrator) - winget package
    installs require it. It:
      1. Installs Git, JDK 21, Visual Studio 2022 Build Tools (Desktop C++ + Windows SDK), and optionally OBS Studio
         (handy as a virtual camera if the VM has no physical webcam), all via winget.
      2. Sets the machine JAVA_HOME.
      3. Clones the repo and checks out the branch.
      4. Imports the Visual Studio x64 build environment and builds the WinRT DLL + shadow jar + packaged content dir
         via Gradle (:apps:desktop:webcam-app:prepareWindowsWebcamAppContent).

    After it finishes, open a NEW normal PowerShell and run Run-Probe.ps1 to execute the harness.

    Copy this single file to the VM (e.g. to the Desktop) and run it; it bootstraps everything else.

.PARAMETER RepoUrl
    Git URL to clone. Default: the wodoro fork.

.PARAMETER Branch
    Branch to check out. Default: webcam-unified-pipeline.

.PARAMETER Dest
    Clone destination. Default: C:\bisq2.

.PARAMETER InstallObs
    Also install OBS Studio (for its virtual camera). Default: $true.

.PARAMETER SkipInstall
    Skip all winget installs (deps already present).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File Provision-And-Build.ps1
#>
[CmdletBinding()]
param(
    [string]$RepoUrl = 'https://github.com/wodoro/bisq2.git',
    [string]$Branch = 'webcam-unified-pipeline',
    [string]$Dest = 'C:\bisq2',
    [bool]$InstallObs = $true,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'

function Write-Section([string]$Title) {
    Write-Host ''
    Write-Host "==== $Title ====" -ForegroundColor Cyan
}

function Assert-Admin {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'This script must run as Administrator (winget installs need it). Right-click PowerShell > Run as administrator.'
    }
}

function Install-WingetPackage([string]$Id, [string]$Override) {
    Write-Host "Installing $Id ..." -ForegroundColor Yellow
    $args = @('install', '--id', $Id, '-e', '--accept-source-agreements', '--accept-package-agreements', '--silent')
    if ($Override) { $args += @('--override', $Override) }
    & winget @args
    # winget returns non-zero when already installed / no upgrade; treat those as success.
    if ($LASTEXITCODE -ne 0) {
        Write-Host "winget exit $LASTEXITCODE for $Id (likely already installed); continuing." -ForegroundColor DarkYellow
    }
}

# --- 1. Dependencies ---
if (-not $SkipInstall) {
    Assert-Admin
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw 'winget not found. Install "App Installer" from the Microsoft Store, or install deps manually.'
    }
    Write-Section 'Installing dependencies via winget'
    Install-WingetPackage 'Git.Git'
    Install-WingetPackage 'Microsoft.OpenJDK.21'
    # Add the C++ build tools workload (gives cl.exe, the Windows SDK, winrt headers and windowsapp.lib).
    Install-WingetPackage 'Microsoft.VisualStudio.2022.BuildTools' `
        '--quiet --wait --norestart --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended'
    if ($InstallObs) { Install-WingetPackage 'OBSProject.OBSStudio' }
}

# Refresh PATH from the persisted machine+user values so freshly installed tools (git) are usable in this session,
# even on the -SkipInstall path (where a prior run installed them). Also add git's well-known install dir as a
# fallback in case winget recorded it only after this process started.
$env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' +
    [System.Environment]::GetEnvironmentVariable('Path', 'User')
$gitCmdDir = 'C:\Program Files\Git\cmd'
if ((Test-Path $gitCmdDir) -and ($env:Path -notlike "*$gitCmdDir*")) {
    $env:Path = "$gitCmdDir;$env:Path"
}

# --- 2. JAVA_HOME ---
Write-Section 'Resolving JAVA_HOME'
$jdkDir = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like 'jdk-21*' } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdkDir) {
    throw 'JDK 21 not found under C:\Program Files\Microsoft. Install it or set JAVA_HOME manually and re-run with -SkipInstall.'
}
$JavaHome = $jdkDir.FullName
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', $JavaHome, 'Machine')
$env:JAVA_HOME = $JavaHome
Write-Host "JAVA_HOME = $JavaHome" -ForegroundColor Green

# --- 3. Clone ---
Write-Section "Cloning $RepoUrl ($Branch)"
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'git not found on PATH. Close and reopen PowerShell (as admin) so the new PATH applies, then re-run with -SkipInstall.'
}
if (Test-Path $Dest) {
    Write-Host "$Dest exists; fetching and checking out $Branch." -ForegroundColor Yellow
    git -C $Dest fetch origin $Branch
    git -C $Dest checkout $Branch
    git -C $Dest pull --ff-only origin $Branch
} else {
    git clone --branch $Branch $RepoUrl $Dest
}
if ($LASTEXITCODE -ne 0) { throw "git clone/checkout failed with exit code $LASTEXITCODE" }

# --- 4. Import Visual Studio build env, then build ---
Write-Section 'Importing Visual Studio x64 build environment'
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path $vswhere)) { throw "vswhere.exe not found; Visual Studio Build Tools install may have failed." }
$vsRoot = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $vsRoot) { throw 'VC++ toolset (VC.Tools.x86.x64) not found. Re-run install of Build Tools with the C++ workload.' }
$vcvars = Join-Path $vsRoot 'VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path $vcvars)) { throw "vcvars64.bat not found under $vsRoot." }

# Capture the environment vcvars sets (INCLUDE/LIB/PATH for cl.exe) and import it into this session so the Gradle
# cl.exe compile task can find the compiler, Windows SDK and windowsapp.lib.
& cmd.exe /c "call `"$vcvars`" >nul && set" | ForEach-Object {
    if ($_ -match '^(.*?)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
Write-Host 'Visual Studio build environment imported.' -ForegroundColor Green

Write-Section 'Building WinRT DLL + shadow jar + packaged content (Gradle)'
Push-Location $Dest
try {
    & .\gradlew.bat ':apps:desktop:webcam-app:prepareWindowsWebcamAppContent'
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

$ContentDir = Join-Path $Dest 'apps\desktop\webcam-app\build\packaging\windows-app-content\webcam'
$Dll = Join-Path $ContentDir 'bisq_webcam_winrt.dll'
Write-Section 'Build complete'
Write-Host "Repo:        $Dest"
Write-Host "Content dir: $ContentDir"
Write-Host "WinRT DLL:   $Dll  (exists: $(Test-Path $Dll))" -ForegroundColor Green
Write-Host ''
Write-Host 'Next: if the VM has no physical webcam, start OBS > Start Virtual Camera (point a QR image at it),' -ForegroundColor Yellow
Write-Host 'then run the probe:' -ForegroundColor Yellow
Write-Host "    powershell -ExecutionPolicy Bypass -File `"$Dest\tools\webcam-windows-msix-appcontainer\Run-Probe.ps1`"" -ForegroundColor White
