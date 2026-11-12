<#
Verifies a freshly built APK's reader bytecode with ART on a connected device.
No APK installation, app launch, test APK, or access to app data is performed.
For release builds, supply the mapping.txt produced by that same Gradle build.

Example (after :app:assembleRelease):
  ./tools/verify-reader-dex.ps1 -Apk app/build/official/outputs/apk/release/app-arm64-v8a-release-unsigned.apk -Mapping app/build/official/outputs/mapping/release/mapping.txt
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Mapping,
    [string]$Serial,
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$OutputDirectory = '.local/reader-dex-verification'
)
$ErrorActionPreference = 'Stop'
function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE" }
}
if (-not $Sdk) { throw 'Set ANDROID_HOME or pass -Sdk.' }
if (-not $Jdk) { throw 'Set JAVA_HOME to a JDK or pass -Jdk.' }
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$buildTools = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'No stable Android build-tools found.' }
$facades = @('ReaderScreenKt', 'EpubSpreadContentKt', 'RasterReaderScreenKt',
    'EpubWebViewReaderKt', 'ReaderOrientationContentKt', 'EpubWebViewReaderOrientationKt',
    'MarkdownNativeReaderOrientationKt', 'ContinuousScrollReaderOrientationKt') |
    ForEach-Object { "com.huangder.lumibooks.ui.reader.$_" }
if ($Mapping) {
    $mappingPath = (Resolve-Path -LiteralPath $Mapping).Path
    $names = @{}
    $mapId = $null
    foreach ($line in [System.IO.File]::ReadLines($mappingPath)) {
        if ($line -match '^# pg_map_id: (\S+)$') { $mapId = $Matches[1] }
        if ($line -match '^(\S+) -> (\S+):$' -and $Matches[1] -in $facades) {
            $names[$Matches[1]] = $Matches[2]
        }
    }
    if (-not $mapId) { throw 'R8 map ID missing from mapping.txt.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    $apkMapIds = @()
    try {
        foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' }) {
            $stream = $entry.Open()
            $memory = [System.IO.MemoryStream]::new()
            try {
                $stream.CopyTo($memory)
                $dex = [System.Text.Encoding]::ASCII.GetString($memory.ToArray())
                foreach ($marker in [regex]::Matches($dex, '~~R8\{[^\x00]*?"pg-map-id":"([^"]+)"')) {
                    $apkMapIds += $marker.Groups[1].Value
                }
            } finally {
                $stream.Dispose()
                $memory.Dispose()
            }
        }
    } finally { $archive.Dispose() }
    if ($mapId -notin $apkMapIds -or @($apkMapIds | Where-Object { $_ -ne $mapId }).Count -gt 0) {
        throw 'APK and mapping.txt R8 IDs do not match; use outputs from the same fresh build.'
    }
    Write-Output "Matched R8 map ID: $mapId"
    foreach ($facade in $facades) {
        if (-not $names.ContainsKey($facade)) { throw "Reader class missing from mapping: $facade" }
    }
    $facades = @($facades | ForEach-Object { $names[$_] })
}
$adbArgs = @()
if ($Serial) { $adbArgs = @('-s', $Serial) }
Invoke-Checked $adb ($adbArgs + @('get-state'))
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputPath = (Resolve-Path -LiteralPath $OutputDirectory).Path
$classes = Join-Path $outputPath 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$source = Join-Path $PSScriptRoot 'reader-dex-verifier/ReaderDexVerifier.java'
Invoke-Checked (Join-Path $Jdk 'bin/javac.exe') @('--release', '8', '-d', $classes, $source)
Invoke-Checked (Join-Path $buildTools.FullName 'd8.bat') @('--min-api', '26', '--output', $outputPath, (Join-Path $classes 'ReaderDexVerifier.class'))
$remoteBase = '/data/local/tmp/lumi-reader-dex-' + [guid]::NewGuid().ToString('N')
$remoteApk = $remoteBase + '.apk'
$remoteProbe = $remoteBase + '.dex'
try {
    Invoke-Checked $adb ($adbArgs + @('push', $apkPath, $remoteApk))
    Invoke-Checked $adb ($adbArgs + @('push', (Join-Path $outputPath 'classes.dex'), $remoteProbe))
    $result = & $adb @adbArgs shell "CLASSPATH=${remoteProbe}:${remoteApk} app_process /system/bin ReaderDexVerifier $($facades -join ' ')" 2>&1
    $exitCode = $LASTEXITCODE
    $result | Tee-Object -FilePath (Join-Path $outputPath 'art-verification.log')
    if ($exitCode -ne 0 -or 'READER_DEX_VERIFIED' -notin $result) {
        throw 'ART reader verification failed; see art-verification.log.'
    }
} finally {
    & $adb @adbArgs shell rm -f $remoteApk $remoteProbe
}
