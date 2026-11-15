# Restore script for TCL 43S434
# Run this anytime to re-enable stock apps and stock launcher:
# powershell -ExecutionPolicy Bypass -File .\restore-my-tv.ps1

param(
    [string]$TvHost = "192.168.8.243"
)

$env:TV_HOST = $TvHost
$py = "$PSScriptRoot\tools\venv\Scripts\python.exe"

Write-Host "Restoring packages on TV ($TvHost)..."

$packages = @(
    'com.google.android.tvlauncher',
    'com.tcl.bi',
    'com.tcl.xian.StartandroidService',
    'com.google.android.feedback',
    'android.autoinstalls.config.tcl.device',
    'com.google.android.partnersetup',
    'com.google.android.onetimeinitializer',
    'com.tcl.esticker',
    'com.tcl.messagebox',
    'com.tcl.waterfall.overseas',
    'com.tcl.tv.tclhome_passive',
    'com.tcl.tv.tclhome_master',
    'com.tcl.overseasappshow',
    'com.google.android.tvrecommendations',
    'com.google.android.syncadapters.calendar',
    'com.tcl.usercenter',
    'com.tcl.guard',
    'com.tcl.smartalexa',
    'com.tcl.assistant',
    'com.tcl.miracast',
    'com.tcl.versionUpdateApp',
    'com.google.android.backdrop',
    'com.google.android.tungsten.setupwraith'
)

foreach ($pkg in $packages) {
    Write-Host "Enabling $pkg..."
    & $py -I "$PSScriptRoot\tools\tv.py" "pm enable $pkg" | Out-Null
}

# Reset appops restrictions
& $py -I "$PSScriptRoot\tools\tv.py" "cmd appops set com.google.android.videos RUN_ANY_IN_BACKGROUND allow" | Out-Null
& $py -I "$PSScriptRoot\tools\tv.py" "cmd appops set com.google.android.youtube.tvmusic RUN_ANY_IN_BACKGROUND allow" | Out-Null
& $py -I "$PSScriptRoot\tools\tv.py" "cmd appops set com.google.android.play.games RUN_ANY_IN_BACKGROUND allow" | Out-Null

# Set stock launcher default
& $py -I "$PSScriptRoot\tools\tv.py" "cmd package set-home-activity com.google.android.tvlauncher/.MainActivity" | Out-Null
& $py -I "$PSScriptRoot\tools\tv.py" "settings put secure screensaver_components com.google.android.backdrop/.Backdrop" | Out-Null

Write-Host "Restore complete! Rebooting TV..."
& $py -I "$PSScriptRoot\tools\tv.py" --reboot
