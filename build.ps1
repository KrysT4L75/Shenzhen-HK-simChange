param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Push-Location $projectRoot
try {
$jdkDir = Join-Path $projectRoot 'tools\jdk\jdk-17.0.21+9'
$sdkPlatform = 'tools/platform/android-36/android.jar'
$sdkBuild = Join-Path $projectRoot 'tools\build-tools\android-16'
$javaExe = Join-Path $jdkDir 'bin\java.exe'
$javacExe = Join-Path $jdkDir 'bin\javac.exe'
$jarExe = Join-Path $jdkDir 'bin\jar.exe'
if (!(Test-Path -LiteralPath $javaExe) -or !(Test-Path -LiteralPath $sdkPlatform)) { throw 'Run setup-tools.ps1 first.' }
$buildDir = 'build/run-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$distDir = 'dist'
$classesDir = Join-Path $buildDir 'classes'
$generatedDir = Join-Path $buildDir 'generated'
$dexDir = Join-Path $buildDir 'dex'
New-Item -ItemType Directory -Force -Path $buildDir,$distDir,$classesDir,$generatedDir,$dexDir | Out-Null
function Check-Exit([string]$step) { if ($LASTEXITCODE -ne 0) { throw "$step failed ($LASTEXITCODE)." } }
if (!$SkipTests) {
    $testDir = Join-Path $buildDir 'tests'
    New-Item -ItemType Directory -Force -Path $testDir | Out-Null
    & $javacExe -encoding UTF-8 --release 8 -d $testDir (Join-Path $projectRoot 'app\src\main\java\cn\shenlink\guard\ReminderEngine.java') (Join-Path $projectRoot 'tests\ReminderEngineTest.java')
    Check-Exit 'Compile behavior tests'
    & $javaExe -cp $testDir cn.shenlink.guard.ReminderEngineTest | Tee-Object -FilePath (Join-Path $buildDir 'behavior-tests.txt')
    Check-Exit 'Behavior tests'
    & $javacExe -encoding UTF-8 --release 8 -d $testDir (Join-Path $projectRoot 'app\src\main\java\cn\shenlink\guard\ReminderEngine.java') (Join-Path $projectRoot 'app\src\main\java\cn\shenlink\guard\JourneyInference.java') (Join-Path $projectRoot 'tests\JourneyInferenceTest.java')
    Check-Exit 'Compile journey tests'
    & $javaExe -cp $testDir cn.shenlink.guard.JourneyInferenceTest | Tee-Object -FilePath (Join-Path $buildDir 'journey-tests.txt')
    Check-Exit 'Journey tests'
    if (Get-Command node -ErrorAction SilentlyContinue) {
        & node --test tests/domain.test.cjs | Tee-Object -FilePath (Join-Path $buildDir 'dashboard-tests.txt')
        Check-Exit 'Dashboard domain tests'
    } else { throw 'Node.js is required for dashboard tests. Install Node or use -SkipTests explicitly.' }
}
$compiledRes = Join-Path $buildDir 'resources.zip'
& (Join-Path $sdkBuild 'aapt2.exe') compile --dir app/src/main/res -o $compiledRes
Check-Exit 'Compile resources'
$unsigned = Join-Path $buildDir 'unsigned.apk'
& (Join-Path $sdkBuild 'aapt2.exe') link -I $sdkPlatform --manifest app/src/main/AndroidManifest.xml -A app/src/main/assets --java $generatedDir -o $unsigned $compiledRes
Check-Exit 'Link resources'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\main\java'),$generatedDir -Filter '*.java' -Recurse | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
$sourceList = Join-Path $buildDir 'sources.txt'
[IO.File]::WriteAllLines($sourceList,$sources,[Text.UTF8Encoding]::new($false))
& $javacExe '-J-Dfile.encoding=UTF-8' -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath $sdkPlatform -d $classesDir "@$sourceList"
Check-Exit 'Compile app'
$classJar = Join-Path $buildDir 'classes.jar'
& $jarExe --create --file $classJar -C $classesDir .
Check-Exit 'Package classes'
& $javaExe -cp (Join-Path $sdkBuild 'lib\d8.jar') com.android.tools.r8.D8 --release --min-api 26 --lib $sdkPlatform --output $dexDir $classJar
Check-Exit 'Compile Android bytecode'
& $jarExe --update --file $unsigned -C $dexDir classes.dex
Check-Exit 'Package APK'
$aligned = Join-Path $buildDir 'aligned.apk'
& (Join-Path $sdkBuild 'zipalign.exe') -f -p 4 $unsigned $aligned
Check-Exit 'Align APK'
$signingDir = Join-Path $projectRoot '.signing'
New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
$keyFile = Join-Path $signingDir 'personal-release.p12'
$passFile = Join-Path $signingDir 'password.txt'
if (!(Test-Path -LiteralPath $keyFile)) {
    if (!(Test-Path -LiteralPath $passFile)) {
        $randomBytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        $rng.GetBytes($randomBytes); $rng.Dispose()
        [IO.File]::WriteAllText($passFile,[Convert]::ToBase64String($randomBytes),[Text.UTF8Encoding]::new($false))
    }
    & (Join-Path $jdkDir 'bin\keytool.exe') -genkeypair -keystore $keyFile -storetype PKCS12 -storepass:file $passFile -alias local-release -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Guoguan Huanka Personal,O=Local,C=CN'
    Check-Exit 'Generate local signing key'
}
$apk = Join-Path $distDir '通勤助手-2.0.0.apk'
& $javaExe -jar (Join-Path $sdkBuild 'lib\apksigner.jar') sign --ks $keyFile --ks-key-alias local-release --ks-pass "file:$passFile" --out $apk $aligned
Check-Exit 'Sign APK'
& $javaExe -jar (Join-Path $sdkBuild 'lib\apksigner.jar') verify --verbose --print-certs $apk | Tee-Object -FilePath (Join-Path $distDir 'apk-signature.txt')
Check-Exit 'Verify APK signature'
& (Join-Path $sdkBuild 'zipalign.exe') -c -p 4 $apk
Check-Exit 'Verify alignment'
& (Join-Path $sdkBuild 'aapt2.exe') dump badging $apk | Set-Content -LiteralPath (Join-Path $distDir 'apk-metadata.txt') -Encoding utf8
Check-Exit 'Inspect package'
$checksum = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $distDir 'SHA256.txt'),"$checksum  通勤助手-2.0.0.apk`n",[Text.UTF8Encoding]::new($false))
Write-Output "APK ready: $apk"
Write-Output "Build evidence: $buildDir"
} finally { Pop-Location }

