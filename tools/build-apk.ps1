param([string]$SdkRoot = (Join-Path $PSScriptRoot '../.tools/android-sdk'))
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sdk = (Resolve-Path $SdkRoot).Path
$buildTools = Join-Path $sdk 'build-tools/36.0.0'
$androidJar = Join-Path $sdk 'platforms/android-36/android.jar'
$output = Join-Path $projectRoot 'app/build/manual'
New-Item -ItemType Directory -Force $output, "$output/classes", "$output/generated", "$output/dex" | Out-Null
function Assert-Exit([string]$Step) { if ($LASTEXITCODE -ne 0) { throw "$Step failed ($LASTEXITCODE)" } }

# The conventional Gradle build supplies this package via android.namespace.
[xml]$manifest = Get-Content -LiteralPath "$projectRoot/app/src/main/AndroidManifest.xml"
$manifest.manifest.SetAttribute('package', 'com.gladbox.driftscroll')
$manifest.Save("$output/AndroidManifest.xml")
& "$buildTools/aapt2.exe" compile --dir "$projectRoot/app/src/main/res" -o "$output/resources.zip"
Assert-Exit 'Resource compilation'
& "$buildTools/aapt2.exe" link -o "$output/unsigned.apk" -I $androidJar --manifest "$output/AndroidManifest.xml" --java "$output/generated" --min-sdk-version 26 --target-sdk-version 36 --version-code 3 --version-name 1.2 "$output/resources.zip"
Assert-Exit 'Resource linking'
$sourceFiles = @(Get-ChildItem "$projectRoot/app/src/main/java", "$output/generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -classpath $androidJar -d "$output/classes" @sourceFiles
Assert-Exit 'Java compilation'
$classFiles = @(Get-ChildItem "$output/classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
& "$buildTools/d8.bat" --lib $androidJar --min-api 26 --output "$output/dex" @classFiles
Assert-Exit 'DEX compilation'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open("$output/unsigned.apk", 'Update')
try {
    Get-ChildItem "$output/dex" -Filter '*.dex' | ForEach-Object {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $_.FullName, $_.Name) | Out-Null
    }
} finally { $archive.Dispose() }
& "$buildTools/zipalign.exe" -f -p 4 "$output/unsigned.apk" "$output/aligned.apk"
Assert-Exit 'APK alignment'
$keystore = Join-Path $projectRoot '.tools/debug.keystore'
if (!(Test-Path -LiteralPath $keystore)) {
    & keytool -genkeypair -keystore $keystore -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
    Assert-Exit 'Debug signing key generation'
}
$deliverable = Join-Path $projectRoot 'DriftScroll-debug.apk'
& "$buildTools/apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $deliverable "$output/aligned.apk"
Assert-Exit 'APK signing'
& "$buildTools/apksigner.bat" verify --verbose $deliverable
Assert-Exit 'APK signature verification'
Write-Output "APK ready: $deliverable"
