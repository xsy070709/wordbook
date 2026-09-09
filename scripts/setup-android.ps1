param(
    [switch]$AcceptAndroidSdkLicense,
    [switch]$SkipAndroidStudio
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not $AcceptAndroidSdkLicense) {
    throw 'Android SDK components require accepting the Android SDK license. Re-run with -AcceptAndroidSdkLicense after reviewing https://developer.android.com/studio/terms.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.tools'
$downloadRoot = Join-Path $toolsRoot 'downloads'
$jdkRoot = Join-Path $toolsRoot 'jdk-17'
$sdkRoot = Join-Path $toolsRoot 'android-sdk'
$studioRoot = Join-Path $toolsRoot 'android-studio'

New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null
New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

function Get-AndExpandZip {
    param([string]$Url, [string]$Archive, [string]$Destination)
    if (Test-Path -LiteralPath $Archive) {
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($Archive)
            $zip.Dispose()
        } catch {
            Write-Host "Removing incomplete archive: $Archive"
            Remove-Item -LiteralPath $Archive
        }
    }
    if (-not (Test-Path -LiteralPath $Archive)) {
        Write-Host "Downloading $Url"
        Invoke-WebRequest -Uri $Url -OutFile $Archive
    }
    if (-not (Test-Path -LiteralPath $Destination)) {
        New-Item -ItemType Directory -Force -Path $Destination | Out-Null
        Expand-Archive -LiteralPath $Archive -DestinationPath $Destination -Force
    }
}

if (-not $SkipAndroidStudio) {
    $studioArchive = Join-Path $downloadRoot 'android-studio.zip'
    if (-not (Test-Path -LiteralPath (Join-Path $studioRoot 'bin\studio64.exe'))) {
        if (Test-Path -LiteralPath $studioArchive) {
            try {
                $zip = [System.IO.Compression.ZipFile]::OpenRead($studioArchive)
                $zip.Dispose()
            } catch {
                Write-Host "Removing incomplete archive: $studioArchive"
                Remove-Item -LiteralPath $studioArchive
            }
        }
        if (-not (Test-Path -LiteralPath $studioArchive)) {
            Write-Host 'Downloading Android Studio Quail 4 (2026.1.4)'
            Invoke-WebRequest `
                -Uri 'https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.1.4.7/android-studio-quail4-windows.zip' `
                -OutFile $studioArchive
        }
        Expand-Archive -LiteralPath $studioArchive -DestinationPath $toolsRoot -Force
    }
    $javaHome = Join-Path $studioRoot 'jbr'
} else {
    $jdkArchive = Join-Path $downloadRoot 'jdk17.zip'
    if ((Test-Path -LiteralPath $jdkArchive) -and (Get-Item -LiteralPath $jdkArchive).Length -eq 0) {
        Remove-Item -LiteralPath $jdkArchive
    }
    Get-AndExpandZip `
        -Url 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' `
        -Archive $jdkArchive `
        -Destination $jdkRoot
    $javaHome = (Get-ChildItem -LiteralPath $jdkRoot -Directory | Select-Object -First 1).FullName
}

$commandLineArchive = Join-Path $downloadRoot 'commandlinetools.zip'
$commandLineExtract = Join-Path $toolsRoot 'commandline-extract'
Get-AndExpandZip `
    -Url 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' `
    -Archive $commandLineArchive `
    -Destination $commandLineExtract

$commandLineTarget = Join-Path $sdkRoot 'cmdline-tools\latest'
if (-not (Test-Path -LiteralPath $commandLineTarget)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $commandLineTarget) | Out-Null
    Move-Item -LiteralPath (Join-Path $commandLineExtract 'cmdline-tools') -Destination $commandLineTarget
}

$env:JAVA_HOME = $javaHome
$sdkManager = Join-Path $commandLineTarget 'bin\sdkmanager.bat'
1..20 | ForEach-Object { 'y' } | & $sdkManager --sdk_root=$sdkRoot --licenses | Out-Host
& $sdkManager --sdk_root=$sdkRoot 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'

$gradleArchive = Join-Path $downloadRoot 'gradle-8.8-bin.zip'
$gradleRoot = Join-Path $toolsRoot 'gradle'
Get-AndExpandZip `
    -Url 'https://services.gradle.org/distributions/gradle-8.8-bin.zip' `
    -Archive $gradleArchive `
    -Destination $gradleRoot
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'gradlew.bat'))) {
    $wrapperProject = Join-Path $toolsRoot 'wrapper-bootstrap'
    New-Item -ItemType Directory -Force -Path $wrapperProject | Out-Null
    Set-Content -LiteralPath (Join-Path $wrapperProject 'settings.gradle.kts') -Encoding UTF8 -Value "rootProject.name = `"wrapper-bootstrap`""
    Set-Content -LiteralPath (Join-Path $wrapperProject 'build.gradle.kts') -Encoding UTF8 -Value ''
    & (Join-Path $gradleRoot 'gradle-8.8\bin\gradle.bat') -p $wrapperProject wrapper --gradle-version 8.8 --distribution-type bin
    Copy-Item -LiteralPath (Join-Path $wrapperProject 'gradlew') -Destination $projectRoot
    Copy-Item -LiteralPath (Join-Path $wrapperProject 'gradlew.bat') -Destination $projectRoot
    New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'gradle\wrapper') | Out-Null
    Copy-Item -LiteralPath (Join-Path $wrapperProject 'gradle\wrapper\gradle-wrapper.jar') -Destination (Join-Path $projectRoot 'gradle\wrapper')
    Copy-Item -LiteralPath (Join-Path $wrapperProject 'gradle\wrapper\gradle-wrapper.properties') -Destination (Join-Path $projectRoot 'gradle\wrapper')
}

$sdkProperty = $sdkRoot.Replace('\', '\\')
Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Encoding UTF8 -Value "sdk.dir=$sdkProperty"

Write-Host ''
Write-Host "JAVA_HOME=$javaHome"
Write-Host "ANDROID_SDK_ROOT=$sdkRoot"
if (-not $SkipAndroidStudio) {
    Write-Host "Android Studio: $(Join-Path $studioRoot 'bin\studio64.exe')"
}
