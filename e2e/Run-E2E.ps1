# Aritiq on-device E2E: launch, calculator, save, search, pin, folders, settings, persistence, delete.
# Usage: .\e2e\Run-E2E.ps1 [-Apk <path>]
#   Needs a booted, UNLOCKED emulator/device. Set ARITIQ_SERIAL to pick one, else auto-detected.
#   ponytail: Compose merges the a11y tree (a note's body text absorbs its own readout), so every
#   assertion here matches substrings against @text, and field focus is geometry-based. No
#   Appium/Maestro needed for an app this size.

param([string]$Apk = "")

. "$PSScriptRoot\ui.ps1"

$debugDir = Join-Path (Split-Path $PSScriptRoot -Parent) "composeApp\build\outputs\apk\debug"
if (-not $Apk) {
    # The APK name carries the version (Aritiq-<versionName>-debug.apk) and that version now moves
    # with conventional commits, so glob the newest build instead of pinning one filename.
    $found = Get-ChildItem (Join-Path $debugDir "Aritiq-*-debug.apk") -ErrorAction SilentlyContinue
    if (-not $found) { throw "No debug APK in $debugDir - run .\gradlew :composeApp:assembleDebug first." }
    $Apk = ($found | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

Write-Host "device : $script:Serial"
Write-Host "apk    : $Apk"

if (Test-Path $Apk) { Install-Apk $Apk | Out-Null }
Clear-Data
Launch-App | Out-Null

# The readout is its own Text ("= 46.75"); the status bar shows the same number after a sigma.
# Assert on "= <n>" so the check can't be satisfied by the status bar alone.
function Check-Sum { param([string]$Name, [string]$Expected) ; Check-Text $Name "= $Expected" }

# ---------------------------------------------------------------- launch
Section "Launch"
Stop-App
$sw = [Diagnostics.Stopwatch]::StartNew()
Launch-App | Out-Null
$sw.Stop()
$appPid = (Adb @("shell", "pidof", $script:Pkg) | Out-String).Trim()
Check "app process alive" ($appPid -ne "") "(pid=$appPid)"
Write-Host ("  cold start: {0} ms" -f $sw.ElapsedMilliseconds)

Check-Text "home title" "Aritiq"
Check-Text "empty state" "No notes yet"
Check-Desc "FAB create note" "Create note"
Check-Desc "view options" "View options"
Check-Desc "settings" "Settings"
Screenshot "01-home-empty"

# ---------------------------------------------------------------- calculator
Section "Calculator (note 1: plain sum)"
Check "opened editor" (Tap-Desc (Get-Ui) "Create note")
Start-Sleep -Seconds 1
Check-Desc "editor back&save" "Back & save"
Check-Desc "editor delete" "Delete"
Check-Desc "editor folder picker" "Assign folder"
Check-Desc "no export on unsaved note" "Export" -Not

Tap-Title
Type-Text "Calc"
Tap-Body
Type-Text "Milk 12.5";  Press-Enter
Type-Text "Bread 30";   Press-Enter
Type-Text "Coffee 4.25"; Press-Enter
Type-Text "total"
Start-Sleep -Seconds 1

Check-Sum "sum readout 46.75" "46.75"
# sigma is built from its code point so the script stays pure ASCII on disk
Check-Text "status bar carries sum" "$([char]0x03A3) 46.75"
Check-Text "word/char stats" "w"
Screenshot "02-editor-total"

# typing another line must move the total: 46.75 + 3.25 = 50
Press-Enter
Type-Text "Cheese 3.25"
Press-Enter
Type-Text "total"
Start-Sleep -Seconds 1
Check-Sum "readout recomputes to 50" "50"

Section "Calculator (note 2: percent + precedence)"
Back-Nav
Check-Text "back on home" "Aritiq"
Check "opened 2nd editor" (Tap-Desc (Get-Ui) "Create note")
Start-Sleep -Seconds 1
Tap-Body
# a fresh editor must not inherit the previous note's text
$ui = Get-Ui
$leaked = Get-Nodes $ui "//node[contains(@text,'Milk 12.5')]"
Check "new note is empty" ($leaked.Count -eq 0) "(previous note text leaked)"
Type-Text "200 + 15%"; Press-Enter
Type-Text "total"
Start-Sleep -Seconds 1
Check-Sum "percent-of semantics 230" "230"

# ---------------------------------------------------------------- save
Section "Back saves both notes"
Back-Nav
Start-Sleep -Seconds 1
Check-Text "note 1 listed" "Calc"
Check-Text "empty state gone" "No notes yet" -Not
Screenshot "03-home-with-notes"

# ---------------------------------------------------------------- search
Section "Search"
$ui = Get-Ui
$f = Get-Nodes $ui "//node[@class='android.widget.EditText']" | Select-Object -First 1
Check "search field found" ($f.Count -gt 0)
if ($f.Count -gt 0) {
    $c = Get-Center $f[0]
    Tap-Coords $c[0] $c[1]
    Type-Text "Cal"
    Start-Sleep -Seconds 1
    Check-Text "search finds Calc" "Calc"
    Check "clear button appears" (Tap-Desc (Get-Ui) "Clear")
    Start-Sleep -Seconds 1
    Check-Text "clear restores list" "Calc"

    $ui = Get-Ui
    $f = Get-Nodes $ui "//node[@class='android.widget.EditText']" | Select-Object -First 1
    $c = Get-Center $f[0]
    Tap-Coords $c[0] $c[1]
    Type-Text "zzzznomatch"
    Start-Sleep -Seconds 1
    Check-Text "no-results state" "No results"
    Check "clear after miss" (Tap-Desc (Get-Ui) "Clear")
    Start-Sleep -Milliseconds 800
}

# ---------------------------------------------------------------- pin
Section "Pin"
Check "pin control found" (Tap-Desc (Get-Ui) "Pin")
Start-Sleep -Seconds 1
Check-Desc "now offers Unpin" "Unpin"
Screenshot "04-pinned"

# ---------------------------------------------------------------- folders
Section "Folders"
Check "view menu opened" (Tap-Desc (Get-Ui) "View options")
Start-Sleep -Milliseconds 800
$ui = Get-Ui
Check-Text "view mode in menu" "Detailed list"
Check-Text "sort in menu" "Newest first"
Check-Text "manage folders in menu" "Manage folders"
Check "manage folders tapped" (Tap-Text (Get-Ui) "Manage folders")
Start-Sleep -Seconds 1
Check-Text "folders screen" "Folders"
# A fixed "Locked" folder is created on first launch, so the list is never empty.
Check-Text "locked folder present" "Locked"
Check-Desc "create folder FAB" "Create folder"
Check "create folder tapped" (Tap-Desc (Get-Ui) "Create folder")
Start-Sleep -Seconds 1
Check-Text "new folder dialog" "New folder"
$ui = Get-Ui
$f = Get-Nodes $ui "//node[@class='android.widget.EditText']" | Select-Object -First 1
if ($f.Count -gt 0) { $c = Get-Center $f[0] ; Tap-Coords $c[0] $c[1] ; Type-Text "Bills" }
Check "create confirmed" (Tap-Text (Get-Ui) "Create")
Start-Sleep -Seconds 1
Check-Text "folder listed" "Bills"
Screenshot "05-folder-created"
Back-Nav
Start-Sleep -Seconds 1
Check-Text "back on home" "Aritiq"
Check-Text "folder chip on home" "Bills"

# ---------------------------------------------------------------- settings
Section "Settings"
Check "settings opened" (Tap-Desc (Get-Ui) "Settings")
Start-Sleep -Seconds 1
Check-Desc "settings has back" "Back"
$ui = Get-Ui
Check "settings exposes export" ((Get-Nodes $ui "//node[contains(@text,'Export')]").Count -gt 0)
Check "settings exposes import" ((Get-Nodes $ui "//node[contains(@text,'Import')]").Count -gt 0)
Screenshot "06-settings"
Back-Nav
Start-Sleep -Seconds 1
Check-Text "returned home" "Aritiq"

# ---------------------------------------------------------------- persistence
Section "Persistence across restart"
Stop-App
Launch-App | Out-Null
Start-Sleep -Seconds 2
Check-Text "note survives restart" "Calc"
Check-Desc "still pinned" "Unpin"
Check-Text "folder chip survives" "Bills"

# ---------------------------------------------------------------- delete
Section "Delete note (via editor)"
$ui = Get-Ui
$t = Get-Nodes $ui "//node[@text='Calc']"
Check "note row found" ($t.Count -gt 0)
if ($t.Count -gt 0) { $c = Get-Center $t[0] ; Tap-Coords $c[0] $c[1] }
Start-Sleep -Seconds 1
Check-Desc "reopened existing note" "Export"
Check "delete tapped" (Tap-Desc (Get-Ui) "Delete")
Start-Sleep -Seconds 1
Check-Text "delete confirm dialog" "Delete note?"
Check "delete confirmed" (Tap-Text (Get-Ui) "Delete")
Start-Sleep -Seconds 2
Check-Text "note gone" "Calc" -Not
Check-Text "other note remains" "200 + 15%"
Screenshot "07-after-delete"

Summary
