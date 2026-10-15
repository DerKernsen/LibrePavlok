param([string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$output = Join-Path $project 'android\build\protocol-tests'
New-Item -ItemType Directory -Force $output | Out-Null
& "$Jdk\bin\javac.exe" -encoding UTF-8 -d $output "$project\android\app\src\main\java\org\librepavlok\offline\LegacyProtocol.java" "$project\android\app\src\main\java\org\librepavlok\offline\PlayCountdown.java" "$project\android\app\src\main\java\org\librepavlok\offline\TodoItem.java" "$project\android\tests\org\librepavlok\offline\LegacyProtocolTest.java" "$project\android\tests\org\librepavlok\offline\PlayCountdownTest.java" "$project\android\tests\org\librepavlok\offline\TodoItemTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$Jdk\bin\java.exe" -ea -cp $output org.librepavlok.offline.LegacyProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Protocol tests failed' }
& "$Jdk\bin\java.exe" -ea -cp $output org.librepavlok.offline.PlayCountdownTest
if ($LASTEXITCODE -ne 0) { throw 'Countdown tests failed' }
& "$Jdk\bin\java.exe" -ea -cp $output org.librepavlok.offline.TodoItemTest
if ($LASTEXITCODE -ne 0) { throw 'To-do tests failed' }
