# Build and Install script for TCL 43S434
# Builds the custom Apple TV launcher with live weather and installs it on the TV:
# powershell -ExecutionPolicy Bypass -File .\build-my-tv.ps1

param(
    [string]$TvHost = "192.168.8.243"
)

$env:TV_HOST = $TvHost
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
$env:ANDROID_HOME = "C:\Users\karan\AppData\Local\Android\Sdk"

Set-Location "$PSScriptRoot\launcher"
Write-Host "Building launcher APK..."
.\gradlew.bat assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

Write-Host "Uploading and installing to TV ($TvHost)..."
$py = "$PSScriptRoot\tools\venv\Scripts\python.exe"
$apk = "$PSScriptRoot\launcher\app\build\outputs\apk\release\app-release.apk"
& $py -I "$PSScriptRoot\tools\b64push.py" $apk /data/local/tmp/tvhome.apk
& $py -I "$PSScriptRoot\tools\tv.py" "pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk"

Write-Host "Restarting Home..."
& $py -I "$PSScriptRoot\tools\tv.py" "am force-stop com.gathin.tvhome; input keyevent KEYCODE_HOME"
Write-Host "Done! Launcher updated."
