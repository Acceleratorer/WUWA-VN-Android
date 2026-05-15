$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$Sdk = if ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} else {
    Join-Path $Root ".android-sdk"
}
$BuildTools = Join-Path (Join-Path $Sdk "build-tools") "36.0.0"
$AndroidJar = Join-Path (Join-Path (Join-Path $Sdk "platforms") "android-36") "android.jar"
$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\PROGRA~2\Android\openjdk\jdk-21.0.8" }
$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome "bin") + ";" + $env:PATH

$IsWindows = $PSVersionTable.Platform -eq "Win32NT" -or $env:OS -eq "Windows_NT"
$Exe = if ($IsWindows) { ".exe" } else { "" }
$Bat = if ($IsWindows) { ".bat" } else { "" }

$Aapt2 = Join-Path $BuildTools "aapt2$Exe"
$D8 = Join-Path $BuildTools "d8$Bat"
$Zipalign = Join-Path $BuildTools "zipalign$Exe"
$Apksigner = Join-Path $BuildTools "apksigner$Bat"
$JavaBin = Join-Path $JavaHome "bin"
$Javac = Join-Path $JavaBin "javac$Exe"
$Jar = Join-Path $JavaBin "jar$Exe"
$Keytool = Join-Path $JavaBin "keytool$Exe"

foreach ($Tool in @($Aapt2, $D8, $Zipalign, $Apksigner, $Javac, $Jar, $Keytool, $AndroidJar)) {
    if (!(Test-Path $Tool)) {
        throw "Missing required build tool: $Tool"
    }
}

function Assert-LastExitCode($Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

$VersionName = if ($env:WUWA_VERSION_NAME) { $env:WUWA_VERSION_NAME } else { "2.0.0" }
$VersionCode = if ($env:WUWA_VERSION_CODE) { $env:WUWA_VERSION_CODE } else { "22" }
$PackageName = "com.acceleratorer.wuwavn"
$Out = Join-Path (Join-Path $Root "build") "manual-apk"
$CompiledRes = Join-Path $Out "compiled-res.zip"
$Generated = Join-Path $Out "gen"
$Classes = Join-Path $Out "classes"
$Dex = Join-Path $Out "dex"
$BaseApk = Join-Path $Out "base.apk"
$UnsignedApk = Join-Path $Out "unsigned.apk"
$AlignedApk = Join-Path $Out "aligned.apk"
$ReleaseDir = Join-Path $Root "release"
$SigningDir = Join-Path $Root ".signing"
$ApkName = if ($env:WUWA_APK_NAME) { $env:WUWA_APK_NAME } else { "WUWA-VN-v$VersionName-release.apk" }
$FinalApk = Join-Path $ReleaseDir $ApkName
$Keystore = Join-Path $SigningDir "wuwa-vn-release.jks"

$StorePass = if ($env:WUWA_KEYSTORE_PASSWORD) { $env:WUWA_KEYSTORE_PASSWORD } else { "changeit-wuwa-vn-local" }
$KeyAlias = if ($env:WUWA_KEY_ALIAS) { $env:WUWA_KEY_ALIAS } else { "wuwa-vn-release" }
$KeyPass = if ($env:WUWA_KEY_PASSWORD) { $env:WUWA_KEY_PASSWORD } else { $StorePass }

if (Test-Path $Out) {
    Remove-Item -LiteralPath $Out -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $Out, $Generated, $Classes, $Dex, $ReleaseDir, $SigningDir | Out-Null

$MainDir = Join-Path (Join-Path (Join-Path $Root "app") "src") "main"
& $Aapt2 compile --dir (Join-Path $MainDir "res") -o $CompiledRes
Assert-LastExitCode "aapt2 compile"
& $Aapt2 link `
    -o $BaseApk `
    -I $AndroidJar `
    --manifest (Join-Path $MainDir "AndroidManifest.xml") `
    -R $CompiledRes `
    --java $Generated `
    --custom-package $PackageName `
    --min-sdk-version 30 `
    --target-sdk-version 36 `
    --version-code $VersionCode `
    --version-name $VersionName `
    --auto-add-overlay
Assert-LastExitCode "aapt2 link"

$JavaSources = @()
$JavaSources += Get-ChildItem -Path (Join-Path $MainDir "java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
$JavaSources += Get-ChildItem -Path $Generated -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $Javac -source 8 -target 8 -classpath $AndroidJar -d $Classes $JavaSources
Assert-LastExitCode "javac"

$ClassFiles = Get-ChildItem -Path $Classes -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
& $D8 --min-api 30 --output $Dex $ClassFiles
Assert-LastExitCode "d8"

Copy-Item -LiteralPath $BaseApk -Destination $UnsignedApk -Force
& $Jar uf $UnsignedApk -C $Dex "classes.dex"
Assert-LastExitCode "jar"
& $Zipalign -f 4 $UnsignedApk $AlignedApk
Assert-LastExitCode "zipalign"

if (!(Test-Path $Keystore)) {
    & $Keytool -genkeypair `
        -v `
        -keystore $Keystore `
        -storepass $StorePass `
        -alias $KeyAlias `
        -keypass $KeyPass `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=WUWA VN,O=Acceleratorer,C=VN"
    Assert-LastExitCode "keytool"
}

& $Apksigner sign `
    --ks $Keystore `
    --ks-key-alias $KeyAlias `
    --ks-pass "pass:$StorePass" `
    --key-pass "pass:$KeyPass" `
    --v4-signing-enabled false `
    --out $FinalApk `
    $AlignedApk
Assert-LastExitCode "apksigner sign"

& $Apksigner verify --print-certs $FinalApk
Assert-LastExitCode "apksigner verify"

$Hash = Get-FileHash -Algorithm SHA256 -Path $FinalApk
"$($Hash.Hash.ToLower())  $(Split-Path $FinalApk -Leaf)" | Set-Content -Encoding ascii -Path (Join-Path $ReleaseDir "sha256.txt")

Write-Host "Built $FinalApk"
Write-Host "SHA-256 $($Hash.Hash.ToLower())"
