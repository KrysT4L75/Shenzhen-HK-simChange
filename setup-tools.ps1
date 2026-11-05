$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$toolRoot = Join-Path $PSScriptRoot 'tools'
$downloads = Join-Path $toolRoot 'downloads'
New-Item -ItemType Directory -Force -Path $downloads | Out-Null
# Official vendor downloads pinned by their published checksums. No system-wide installation.
$packages = @(
    @{Name='jdk';Url='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip';Algorithm='SHA256';Hash='98518ecf554465057ee5509462631deeee374bc368c12a411d225cd29381f84b'},
    @{Name='platform';Url='https://dl.google.com/android/repository/platform-36_r02.zip';Algorithm='SHA1';Hash='2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576'},
    @{Name='build-tools';Url='https://dl.google.com/android/repository/build-tools_r36_windows.zip';Algorithm='SHA1';Hash='f16ccffd34de8790dede813a6c7d8e2c11a27b50'}
)
foreach ($package in $packages) {
    $archive = Join-Path $downloads ($package.Name + '.zip')
    if (!(Test-Path -LiteralPath $archive)) { Invoke-WebRequest -UseBasicParsing -Uri $package.Url -OutFile $archive }
    if ((Get-FileHash -LiteralPath $archive -Algorithm $package.Algorithm).Hash.ToLowerInvariant() -ne $package.Hash) { throw ('Checksum mismatch: ' + $package.Name) }
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $toolRoot $package.Name) -Force
    Write-Output ($package.Name + ' verified and extracted')
}
