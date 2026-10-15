param([string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$output = Join-Path $project 'android\build\protocol-tests'
New-Item -ItemType Directory -Force $output | Out-Null
$logicSources = @('LegacyProtocol','PlayCountdown','TodoItem','PulsePlan') | ForEach-Object { "$project\android\app\src\main\java\org\librepavlok\offline\$_.java" }
$testSources = @(Get-ChildItem "$project\android\tests\org\librepavlok\offline" -Filter '*Test.java' | ForEach-Object FullName)
& "$Jdk\bin\javac.exe" -encoding UTF-8 -d $output @logicSources @testSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$Jdk\bin\java.exe" -ea -cp $output org.librepavlok.offline.LegacyProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Protocol tests failed' }
& "$Jdk\bin\java.exe" -ea -cp $output org.librepavlok.offline.PlayCountdownTest
if ($LASTEXITCODE -ne 0) { throw 'Countdown tests failed' }
& "$Jdk\bin\java.exe" -ea -cp $output org.librepavlok.offline.TodoItemTest
if ($LASTEXITCODE -ne 0) { throw 'To-do tests failed' }
& "$Jdk\bin\java.exe" -ea -cp $output org.librepavlok.offline.PulsePlanTest
if ($LASTEXITCODE -ne 0) { throw 'Pulse timing tests failed' }
