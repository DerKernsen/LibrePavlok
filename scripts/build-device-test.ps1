param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk", [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$testSource = Join-Path $project 'android\device-tests'
$testBuild = Join-Path $project 'android\build\device-tests'
$platform = Join-Path $Sdk 'platforms\android-35\android.jar'
$buildTools = Join-Path $Sdk 'build-tools\35.0.0'
$env:JAVA_HOME = $Jdk
New-Item -ItemType Directory -Force $testBuild, "$testBuild\classes", "$testBuild\dex" | Out-Null
function Check-Exit([string]$Step) { if ($LASTEXITCODE -ne 0) { throw "$Step failed: $LASTEXITCODE" } }
& "$buildTools\aapt.exe" package -f -M "$testSource\AndroidManifest.xml" -I $platform -F "$testBuild\resources.apk"
Check-Exit 'Test resource packaging'
$testJavaSources = @(Get-ChildItem -LiteralPath $testSource -Filter '*.java' | ForEach-Object FullName)
& "$Jdk\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $platform -d "$testBuild\classes" @testJavaSources
Check-Exit 'Test compilation'
& "$Jdk\bin\jar.exe" cf "$testBuild\classes.jar" -C "$testBuild\classes" .
Check-Exit 'Test class archive'
& "$buildTools\d8.bat" --lib $platform --min-api 23 --output "$testBuild\dex" "$testBuild\classes.jar"
Check-Exit 'Test DEX'
Copy-Item -LiteralPath "$testBuild\resources.apk" -Destination "$testBuild\unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$testZip = [IO.Compression.ZipFile]::Open("$testBuild\unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($testZip, "$testBuild\dex\classes.dex", 'classes.dex') | Out-Null } finally { $testZip.Dispose() }
& "$buildTools\zipalign.exe" -f 4 "$testBuild\unsigned.apk" "$testBuild\aligned.apk"
Check-Exit 'Test alignment'
& "$buildTools\apksigner.bat" sign --ks "$project\android\build\standalone\debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$testBuild\device-tests.apk" "$testBuild\aligned.apk"
Check-Exit 'Test signing'
& "$buildTools\apksigner.bat" verify "$testBuild\device-tests.apk"
Check-Exit 'Test signature'
