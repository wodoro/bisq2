<#
.SYNOPSIS
    Compares QR-detection effectiveness of the WinRT vs OpenCV capture backends against the SAME camera/scene.

.DESCRIPTION
    Runs QrDecodeProbe twice (bare, no sandbox), once per backend, each running the real ZXing decode pipeline on the
    frames that backend captures. Because the decode stage is identical for both, any difference in decode_rate
    reflects frame quality, not the decoder. Point the camera at a fixed QR code before running.

    Run this on the desktop (both backends work bare). Keep the QR steady and well-lit in front of the camera.

.PARAMETER RepoRoot
    Repo clone root. Default: C:\bisq2.

.PARAMETER JavaHome
    JDK 21 home. Default: $env:JAVA_HOME.

.PARAMETER Frames
    Frames to capture+decode per backend. Default 60.

.PARAMETER Device
    Camera device index. Default 0.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File Compare-QrDecode.ps1 -Frames 100
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = 'C:\bisq2',
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$Frames = 60,
    [int]$Device = 0
)

$ErrorActionPreference = 'Stop'

function Write-Section([string]$Title) {
    Write-Host ''
    Write-Host "==== $Title ====" -ForegroundColor Cyan
}

$ProbeMainClass = 'bisq.webcam.service.capture.QrDecodeProbe'

if (-not $JavaHome) { throw 'JAVA_HOME not set. Pass -JavaHome <jdk21>.' }
$JavaExe = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path $JavaExe)) { throw "java.exe not found at $JavaExe" }

$ContentDir = Join-Path $RepoRoot 'apps\desktop\webcam-app\build\packaging\windows-app-content\webcam'
if (-not (Test-Path $ContentDir)) { throw "Built content dir not found: $ContentDir. Run Provision-And-Build.ps1 first." }
$ContentJar = Get-ChildItem $ContentDir -Filter '*-all.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $ContentJar) { throw "shadow jar (*-all.jar) not found in $ContentDir" }

function Invoke-DecodeProbe([string]$Backend) {
    Write-Section "Backend: $Backend"
    $output = & $JavaExe "-Djava.library.path=$ContentDir" '-cp' $ContentJar.FullName `
        $ProbeMainClass '--backend' $Backend '--device' $Device '--frames' $Frames 2>&1
    $output | ForEach-Object { Write-Host $_ }
    $text = ($output | Out-String)
    $values = @{}
    foreach ($line in ($text -split "`r?`n")) {
        if ($line -match '^([a-z_]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
    }
    return [pscustomobject]@{
        Backend     = $Backend
        Opened      = $values['open_result']
        Negotiated  = $values['negotiated']
        Processed   = $values['processed_count']
        Decoded     = $values['decoded_count']
        DecodeRate  = $values['decode_rate']
        AvgDecodeMs = $values['avg_decode_ms']
        AvgMeanByte = $values['avg_mean_byte']
        Payload     = $values['first_payload']
    }
}

Write-Host "Keep a QR code steady in front of the camera for both runs." -ForegroundColor Yellow
$winrt = Invoke-DecodeProbe 'winrt'
$opencv = Invoke-DecodeProbe 'opencv'

Write-Section 'Comparison'
@($winrt, $opencv) | Format-Table Backend, Opened, Negotiated, Processed, Decoded, DecodeRate, AvgDecodeMs, AvgMeanByte -AutoSize

Write-Section 'Verdict'
$wRate = [double]($winrt.DecodeRate)
$oRate = [double]($opencv.DecodeRate)
if (($winrt.AvgMeanByte -as [int]) -le 2 -or ($opencv.AvgMeanByte -as [int]) -le 2) {
    Write-Host 'WARNING: frames look near-black (avg_mean_byte <= 2). Uncover/aim the camera at a lit QR and re-run; the comparison is meaningless otherwise.' -ForegroundColor Red
} elseif ($winrt.Decoded -eq '0' -and $opencv.Decoded -eq '0') {
    Write-Host 'Neither backend decoded a QR. Check the QR is sharp, fills enough of the frame, and is well-lit.' -ForegroundColor Yellow
} elseif ($wRate -ge ($oRate * 0.9)) {
    Write-Host ("PASS: WinRT decode rate ({0:P0}) is on par with OpenCV ({1:P0})." -f $wRate, $oRate) -ForegroundColor Green
    Write-Host 'The WinRT capture path is as effective at QR detection as OpenCV.'
} else {
    Write-Host ("WinRT decode rate ({0:P0}) is notably below OpenCV ({1:P0})." -f $wRate, $oRate) -ForegroundColor Yellow
    Write-Host 'Investigate frame quality: try matching resolution (--width/--height), check focus/exposure, and'
    Write-Host 'compare avg_mean_byte. The decoder is identical, so the gap is in captured image quality.'
}
