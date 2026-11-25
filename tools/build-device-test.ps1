$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sdk = Join-Path $root '.tools/android-sdk'
$bt = Join-Path $sdk 'build-tools/36.0.0'
$androidJar = Join-Path $sdk 'platforms/android-36/android.jar'
$output = Join-Path $root '.tools/device-test'
New-Item -ItemType Directory -Force $output, "$output/classes", "$output/dex" | Out-Null
function Assert-Exit { if ($LASTEXITCODE -ne 0) { throw "Build step failed: $LASTEXITCODE" } }
& "$bt/aapt2.exe" link -o "$output/unsigned.apk" -I $androidJar --manifest "$root/tests/device/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 36
Assert-Exit
& javac --release 17 -encoding UTF-8 -cp $androidJar -d "$output/classes" "$root/tests/device/ReadingActivity.java"
Assert-Exit
$classes = @(Get-ChildItem "$output/classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
& "$bt/d8.bat" --lib $androidJar --min-api 26 --output "$output/dex" @classes
Assert-Exit
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open("$output/unsigned.apk", 'Update')
try { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, "$output/dex/classes.dex", 'classes.dex') | Out-Null }
finally { $archive.Dispose() }
& "$bt/zipalign.exe" -f -p 4 "$output/unsigned.apk" "$output/aligned.apk"
Assert-Exit
& "$bt/apksigner.bat" sign --ks "$root/.tools/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$output/test.apk" "$output/aligned.apk"
Assert-Exit
