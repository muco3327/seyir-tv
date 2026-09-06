param([ValidateSet('Start','Status','Install','Test','Capture','Stop')][string]$Action='Status')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb=Join-Path $sdk 'platform-tools\adb.exe'
$serial='emulator-5580'
$testRoot=Join-Path $projectRoot '.local\avd'
switch($Action){
 'Start' {
    New-Item -ItemType Directory -Force (Join-Path $testRoot 'SeyirTest.avd') | Out-Null
    $config=Get-Content (Join-Path $env:USERPROFILE '.android\avd\Medium_Phone.avd\config.ini')
    $updates=@{'AvdId'='SeyirTest';'avd.ini.displayname'='Seyir TV Test';'hw.lcd.width'='1280';'hw.lcd.height'='720';'hw.lcd.density'='160';'hw.ramSize'='2048';'hw.cpu.ncore'='2';'hw.keyboard'='yes';'hw.mainKeys'='yes';'showDeviceFrame'='no';'hw.initialOrientation'='landscape'}
    $lines=@(); foreach($line in $config){$key=($line -split '=',2)[0];if($updates.ContainsKey($key)){$lines+=($key+'='+$updates[$key]);$updates.Remove($key)}else{$lines+=$line}}
    foreach($key in $updates.Keys){$lines+=($key+'='+$updates[$key])}
    $lines | Set-Content (Join-Path $testRoot 'SeyirTest.avd\config.ini')
    @('avd.ini.encoding=UTF-8',('path='+ (Join-Path $testRoot 'SeyirTest.avd')),'target=android-36.1') | Set-Content (Join-Path $testRoot 'SeyirTest.ini')
    $env:ANDROID_AVD_HOME=$testRoot
    $emu=Start-Process -FilePath (Join-Path $sdk 'emulator\emulator.exe') -ArgumentList @('-avd','SeyirTest','-port','5580','-no-window','-no-audio','-no-snapshot','-gpu','swiftshader_indirect') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $projectRoot '.local\emulator.log') -RedirectStandardError (Join-Path $projectRoot '.local\emulator-error.log') -PassThru
    $emu.WaitForExit()
 }
 'Status' { & $adb devices -l; & $adb -s $serial shell getprop sys.boot_completed; Get-Content (Join-Path $projectRoot '.local\emulator-error.log') -Tail 6 -ErrorAction SilentlyContinue }
 'Install' {
    & $adb -s $serial install -r (Join-Path $projectRoot 'dist\Seyir-TV-0.1.8.apk')
    if($LASTEXITCODE -ne 0){throw 'APK kurulamadı'}
    & $adb -s $serial shell am start -n tv.seyir.app/.MainActivity
 }
 'Test' {
    & $adb -s $serial install -r (Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
    if($LASTEXITCODE -ne 0){throw 'Test APK kurulamadı'}
    & $adb -s $serial shell am instrument -w tv.seyir.app.test/android.test.InstrumentationTestRunner
 }
 'Capture' {
    & $adb -s $serial shell screencap -p /sdcard/seyir-test.png
    & $adb -s $serial pull /sdcard/seyir-test.png (Join-Path $projectRoot 'dist\seyir-test.png')
    & $adb -s $serial shell uiautomator dump /sdcard/seyir-ui.xml
    & $adb -s $serial pull /sdcard/seyir-ui.xml (Join-Path $projectRoot 'dist\seyir-ui.xml')
    & $adb -s $serial logcat -d -s AndroidRuntime:E
 }
 'Stop' { & $adb -s $serial emu kill }
}
