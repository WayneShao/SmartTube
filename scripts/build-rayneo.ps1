param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [switch]$SkipTests,
    [switch]$DebugApk
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Pass -JavaHome with a JDK 17 directory.'
}
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not (Test-Path (Join-Path $AndroidSdk 'platforms/android-34/android.jar'))) {
    throw 'Android SDK platform 34 is required. Pass -AndroidSdk.'
}
if (-not $DebugApk -and -not (Test-Path (Join-Path $repoRoot 'keystore.properties'))) {
    throw 'Release requires local keystore.properties. See docs/rayneo/README.md; use -DebugApk for a disposable debug build.'
}
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
Push-Location $repoRoot
try {
    & git submodule update --init --recursive
    if ($LASTEXITCODE -ne 0) { throw 'Submodule initialization failed.' }
    $tasks = @()
    if (-not $SkipTests) {
        $tasks += ':common:testStfdroidDebugUnitTest'
        $tasks += ':smarttubetv:testStfdroidDebugUnitTest'
    }
    $tasks += $(if ($DebugApk) { ':smarttubetv:assembleStfdroidDebug' } else { ':smarttubetv:assembleStfdroidRelease' })
    & ./gradlew.bat @tasks '-Prayneo' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build or tests failed.' }
    $buildType = if ($DebugApk) { 'debug' } else { 'release' }
    $metadata = Get-Content "smarttubetv/build/outputs/apk/stfdroid/$buildType/output-metadata.json" -Raw | ConvertFrom-Json
    $arm64 = @($metadata.elements | Where-Object { $_.filters.value -contains 'arm64-v8a' })
    if ($arm64.Count -ne 1) { throw 'Expected exactly one arm64 output in build metadata.' }
    $apks = @(Get-Item (Join-Path "smarttubetv/build/outputs/apk/stfdroid/$buildType" $arm64[0].outputFile))
    if ($apks.Count -ne 1) { throw 'Expected exactly one arm64 APK.' }
    $apk = $apks[0]
    $output = New-Item -ItemType Directory -Force 'releases/rayneo-v3'
    Copy-Item $apk.FullName $output.FullName -Force
    Get-FileHash (Join-Path $output.FullName $apk.Name) -Algorithm SHA256
} finally { Pop-Location }
