param([switch]$Check)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$gradleBin = Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.13-bin') -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
if (-not $gradleBin) { throw 'Gradle 8.13 bulunamadı. Android Studio ile projeyi açın.' }
Push-Location $projectRoot
try {
    $tasks = @('assembleDebug')
    if($Check) { $tasks += @('testDebugUnitTest','lintDebug','assembleDebugAndroidTest') }
    & $gradleBin @tasks --console=plain --no-daemon --no-problems-report
    if($LASTEXITCODE -ne 0) { throw 'Derleme başarısız.' }
    New-Item -ItemType Directory -Path (Join-Path $projectRoot 'dist') -Force | Out-Null
    Copy-Item (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk') (Join-Path $projectRoot 'dist\Seyir-TV-0.1.9.apk')
} finally { Pop-Location }
