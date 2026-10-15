param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$build = Join-Path $project 'android\build\standalone'
$source = Join-Path $project 'android\app\src\main'
$platform = Join-Path $Sdk 'platforms\android-35\android.jar'
$buildTools = Join-Path $Sdk 'build-tools\35.0.0'
foreach ($required in @($platform, "$buildTools\aapt.exe", "$buildTools\d8.bat", "$Jdk\bin\javac.exe")) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing tool: $required" }
}
$env:JAVA_HOME = $Jdk
New-Item -ItemType Directory -Force $build, "$build\classes", "$build\dex", "$project\dist" | Out-Null
function Check-Exit([string]$Step) { if ($LASTEXITCODE -ne 0) { throw "$Step failed: $LASTEXITCODE" } }
& "$buildTools\aapt.exe" package -f -M "$source\AndroidManifest.xml" -S "$source\res" -I $platform -F "$build\resources.apk" --version-code 9 --version-name 0.3.0
Check-Exit 'Resource packaging'
$javaSources = @(Get-ChildItem "$source\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$Jdk\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $platform -d "$build\classes" @javaSources
Check-Exit 'Java compilation'
& "$Jdk\bin\jar.exe" cf "$build\classes.jar" -C "$build\classes" .
Check-Exit 'Class archive'
& "$buildTools\d8.bat" --lib $platform --min-api 23 --output "$build\dex" "$build\classes.jar"
Check-Exit 'DEX compilation'
Copy-Item -LiteralPath "$build\resources.apk" -Destination "$build\unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkArchive = [IO.Compression.ZipFile]::Open("$build\unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($dexFile in Get-ChildItem "$build\dex" -Filter 'classes*.dex') {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkArchive, $dexFile.FullName, $dexFile.Name) | Out-Null
    }
} finally { $apkArchive.Dispose() }
& "$buildTools\zipalign.exe" -f 4 "$build\unsigned.apk" "$build\aligned.apk"
Check-Exit 'ZIP alignment'
$keystore = "$build\debug.keystore"
if (!(Test-Path -LiteralPath $keystore)) {
    & "$Jdk\bin\keytool.exe" -genkeypair -keystore $keystore -storepass android -keypass android -alias androiddebugkey -dname 'CN=LibrePavlok Local Debug,O=Local,C=DE' -keyalg RSA -keysize 2048 -validity 10000
    Check-Exit 'Debug key generation'
}
$output = "$project\dist\LibrePavlok-debug.apk"
& "$buildTools\apksigner.bat" sign --ks $keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $output "$build\aligned.apk"
Check-Exit 'APK signing'
& "$buildTools\apksigner.bat" verify --verbose $output
Check-Exit 'Signature verification'
$apkHash = Get-FileHash -Algorithm SHA256 $output
[IO.File]::WriteAllText("$output.sha256", "$($apkHash.Hash.ToLowerInvariant())  LibrePavlok-debug.apk`n", (New-Object System.Text.UTF8Encoding($false)))
$apkHash
