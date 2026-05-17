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
Add-Type -AssemblyName System.IO.Compression.FileSystem

$IsWindows = $PSVersionTable.Platform -eq "Win32NT" -or $env:OS -eq "Windows_NT"
$Exe = if ($IsWindows) { ".exe" } else { "" }
$Bat = if ($IsWindows) { ".bat" } else { "" }

$Aapt2 = Join-Path $BuildTools "aapt2$Exe"
$Aidl = Join-Path $BuildTools "aidl$Exe"
$D8 = Join-Path $BuildTools "d8$Bat"
$Zipalign = Join-Path $BuildTools "zipalign$Exe"
$Apksigner = Join-Path $BuildTools "apksigner$Bat"
$JavaBin = Join-Path $JavaHome "bin"
$Javac = Join-Path $JavaBin "javac$Exe"
$Jar = Join-Path $JavaBin "jar$Exe"
$Keytool = Join-Path $JavaBin "keytool$Exe"

foreach ($Tool in @($Aapt2, $Aidl, $D8, $Zipalign, $Apksigner, $Javac, $Jar, $Keytool, $AndroidJar)) {
    if (!(Test-Path $Tool)) {
        throw "Missing required build tool: $Tool"
    }
}

function Assert-LastExitCode($Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

$VersionFile = Join-Path $Root "version.properties"
$VersionProperties = @{}
if (Test-Path $VersionFile) {
    Get-Content -Path $VersionFile | ForEach-Object {
        $Line = $_.Trim()
        if ($Line -and !$Line.StartsWith("#") -and $Line.Contains("=")) {
            $Parts = $Line.Split("=", 2)
            $VersionProperties[$Parts[0].Trim()] = $Parts[1].Trim()
        }
    }
}

$VersionName = if ($env:WUWA_VERSION_NAME) { $env:WUWA_VERSION_NAME } elseif ($VersionProperties["VERSION_NAME"]) { $VersionProperties["VERSION_NAME"] } else { "3.3.7" }
$VersionCode = if ($env:WUWA_VERSION_CODE) { $env:WUWA_VERSION_CODE } elseif ($VersionProperties["VERSION_CODE"]) { $VersionProperties["VERSION_CODE"] } else { "41" }
$PackageName = "com.acceleratorer.wuwavn"
$ShizukuVersion = "13.1.5"
$ShizukuApiSha256 = "4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b"
$ShizukuProviderSha256 = "b0f18cd9812464ec171c53cac93a819fe411718a3965c311f01eb4de265381b3"
$KotlinVersion = "2.0.21"
$KotlinCompilerSha256 = "0352c0a45bd22f80f6b26e485cd04da8047baa5de54865281fb9f89a4a7bcf2a"
$Out = Join-Path (Join-Path $Root "build") "manual-apk"
$DepsDir = Join-Path (Join-Path $Root "build") "deps"
$CompiledRes = Join-Path $Out "compiled-res.zip"
$Generated = Join-Path $Out "gen"
$GeneratedAidl = Join-Path $Out "aidl"
$Classes = Join-Path $Out "classes"
$ClassesJar = Join-Path $Out "classes.jar"
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
New-Item -ItemType Directory -Force -Path $Out, $Generated, $GeneratedAidl, $Classes, $Dex, $ReleaseDir, $SigningDir, $DepsDir | Out-Null

function Get-Sha256($Path) {
    return (Get-FileHash -Algorithm SHA256 -Path $Path).Hash.ToLower()
}

function Download-Dependency($Url, $Path, $ExpectedSha256) {
    if (!(Test-Path $Path) -or (Get-Sha256 $Path) -ne $ExpectedSha256) {
        Write-Host "Downloading $Url"
        Invoke-WebRequest -Uri $Url -OutFile $Path
    }

    $ActualSha256 = Get-Sha256 $Path
    if ($ActualSha256 -ne $ExpectedSha256) {
        throw "Dependency hash mismatch for $Path. Expected $ExpectedSha256 but got $ActualSha256"
    }
}

function Extract-AarClasses($AarPath, $Destination) {
    if (Test-Path $Destination) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    [System.IO.Compression.ZipFile]::ExtractToDirectory($AarPath, $Destination)
    $ClassesJar = Join-Path $Destination "classes.jar"
    if (!(Test-Path $ClassesJar)) {
        throw "AAR did not contain classes.jar: $AarPath"
    }
    return $ClassesJar
}

function Extract-ZipDependency($ZipPath, $Destination) {
    if (Test-Path $Destination) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $Destination)
}

$ApiAar = Join-Path $DepsDir "shizuku-api-$ShizukuVersion.aar"
$ProviderAar = Join-Path $DepsDir "shizuku-provider-$ShizukuVersion.aar"
Download-Dependency "https://repo.maven.apache.org/maven2/dev/rikka/shizuku/api/$ShizukuVersion/api-$ShizukuVersion.aar" $ApiAar $ShizukuApiSha256
Download-Dependency "https://repo.maven.apache.org/maven2/dev/rikka/shizuku/provider/$ShizukuVersion/provider-$ShizukuVersion.aar" $ProviderAar $ShizukuProviderSha256

$KotlinCompilerZip = Join-Path $DepsDir "kotlin-compiler-$KotlinVersion.zip"
$KotlinCompilerDir = Join-Path $DepsDir "kotlin-compiler-$KotlinVersion"
Download-Dependency "https://github.com/JetBrains/kotlin/releases/download/v$KotlinVersion/kotlin-compiler-$KotlinVersion.zip" $KotlinCompilerZip $KotlinCompilerSha256
if (!(Test-Path (Join-Path (Join-Path $KotlinCompilerDir "kotlinc") "bin"))) {
    Extract-ZipDependency $KotlinCompilerZip $KotlinCompilerDir
}
$Kotlinc = Join-Path (Join-Path (Join-Path $KotlinCompilerDir "kotlinc") "bin") "kotlinc$Bat"
if (!(Test-Path $Kotlinc)) {
    throw "Missing Kotlin compiler: $Kotlinc"
}
$KotlinLib = Join-Path (Join-Path $KotlinCompilerDir "kotlinc") "lib"
$KotlinStdlibJars = @(
    (Join-Path $KotlinLib "kotlin-stdlib.jar"),
    (Join-Path $KotlinLib "kotlin-stdlib-jdk7.jar"),
    (Join-Path $KotlinLib "kotlin-stdlib-jdk8.jar")
)
foreach ($JarPath in $KotlinStdlibJars) {
    if (!(Test-Path $JarPath)) {
        throw "Missing Kotlin runtime jar: $JarPath"
    }
}

$DependencyJars = @(
    (Extract-AarClasses $ApiAar (Join-Path $DepsDir "api")),
    (Extract-AarClasses $ProviderAar (Join-Path $DepsDir "provider"))
)
$RuntimeJars = $DependencyJars + $KotlinStdlibJars

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
$BuildValuesPath = Join-Path (Join-Path (Join-Path $Generated "com") "acceleratorer") "wuwavn"
New-Item -ItemType Directory -Force -Path $BuildValuesPath | Out-Null
@"
package com.acceleratorer.wuwavn;

final class BuildValues {
    public static final String VERSION_NAME = "$VersionName";
    public static final int VERSION_CODE = $VersionCode;

    private BuildValues() {
    }
}
"@ | Set-Content -Encoding ascii -Path (Join-Path $BuildValuesPath "BuildValues.java")

$AidlDir = Join-Path $MainDir "aidl"
if (Test-Path $AidlDir) {
    $AidlSources = Get-ChildItem -Path $AidlDir -Recurse -Filter "*.aidl"
    foreach ($AidlSource in $AidlSources) {
        & $Aidl --lang=java --min_sdk_version=30 -I $AidlDir -o $GeneratedAidl $AidlSource.FullName
        Assert-LastExitCode "aidl"
    }
}

$AppJavaDir = Join-Path $MainDir "java"
if (Test-Path $AppJavaDir) {
    $JavaSources += Get-ChildItem -Path $AppJavaDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
}
$JavaSources += Get-ChildItem -Path $Generated -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
$JavaSources += Get-ChildItem -Path $GeneratedAidl -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
$CompileClasspath = (@($AndroidJar) + $DependencyJars) -join [System.IO.Path]::PathSeparator
& $Javac -source 8 -target 8 -classpath $CompileClasspath -d $Classes $JavaSources
Assert-LastExitCode "javac"

$AppKotlinDir = Join-Path $MainDir "kotlin"
if (Test-Path $AppKotlinDir) {
    $KotlinSources = Get-ChildItem -Path $AppKotlinDir -Recurse -Filter "*.kt" | ForEach-Object { $_.FullName }
    if ($KotlinSources.Count -gt 0) {
        $KotlinClasspath = (@($Classes, $AndroidJar) + $RuntimeJars) -join [System.IO.Path]::PathSeparator
        $KotlinArgs = Join-Path $Out "kotlinc.args"
        $KotlinArgLines = @("-jvm-target", "1.8", "-cp", $KotlinClasspath, "-d", $Classes) + $KotlinSources
        [System.IO.File]::WriteAllText($KotlinArgs, ($KotlinArgLines -join [Environment]::NewLine) + [Environment]::NewLine)
        & $Kotlinc "@$KotlinArgs"
        Assert-LastExitCode "kotlinc"
    }
}

& $Jar cf $ClassesJar -C $Classes "."
Assert-LastExitCode "jar classes"

$D8Inputs = @("--min-api", "30", "--output", $Dex, $ClassesJar) + $RuntimeJars
& $D8 @D8Inputs
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
$HashLower = $Hash.Hash.ToLower()
"$HashLower  $(Split-Path $FinalApk -Leaf)" | Set-Content -Encoding ascii -Path (Join-Path $ReleaseDir "sha256.txt")

$RootUpdateJson = Join-Path $Root "update.json"
if (Test-Path $RootUpdateJson) {
    $RootManifest = Get-Content -Raw -Path $RootUpdateJson | ConvertFrom-Json
    $ReleaseManifest = [ordered]@{
        manifest_version = if ($RootManifest.manifest_version) { [int]$RootManifest.manifest_version } else { 3 }
        app = [ordered]@{
            version_name = $VersionName
            version_code = [int]$VersionCode
            supported_game_version = $RootManifest.app.supported_game_version
            minimum_game_version = $RootManifest.app.minimum_game_version
            apk_url = "https://github.com/Acceleratorer/WUWA-VN-Android/releases/download/v$VersionName/$ApkName"
            sha256 = $HashLower
            changelog = @($RootManifest.app.changelog)
            force_update = [bool]$RootManifest.app.force_update
        }
        game = $RootManifest.game
        patch = $RootManifest.patch
    }
    $ReleaseManifestJson = ($ReleaseManifest | ConvertTo-Json -Depth 8) + [Environment]::NewLine
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $ReleaseDir "update.json"), $ReleaseManifestJson, $Utf8NoBom)
}

Write-Host "Built $FinalApk"
Write-Host "SHA-256 $HashLower"
