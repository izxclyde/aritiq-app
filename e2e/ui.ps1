# Shared helpers for Aritiq on-device E2E tests. Sourced by the Run-*.ps1 scripts.
# ponytail: uiautomator dump + XPath is enough for a Compose app this size; no Appium/Maestro.

$script:Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$script:Pkg  = "com.aritiq.calcnote"
$script:Activity = "$script:Pkg/.MainActivity"
$script:Serial = $env:ARITIQ_SERIAL
if (-not $script:Serial) { $script:Serial = (& $script:Adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1) }

$script:Pass = 0
$script:Fail = 0
$script:Failures = @()
$script:ShotDir = $PSScriptRoot
$script:UpdatePrompts = 0
$script:AnrDialogs = 0

# Args go through as an array: PowerShell would otherwise eat flags like -W as cmdlet params,
# and ValueFromRemainingArguments would glue a splatted array into one string.
function Adb {
    param([string[]]$AdbArgs)
    if ($script:Serial) { & $script:Adb -s $script:Serial @AdbArgs } else { & $script:Adb @AdbArgs }
}

function Sh { param([string]$Cmd) ; Adb @("shell", $Cmd) }

# ---------- UI hierarchy ----------

function Get-UiRaw {
    # /data/local/tmp, not /sdcard: this image has no writable /sdcard for the shell user.
    $remote = "/data/local/tmp/aritiq-ui.xml"
    $local = [System.IO.Path]::GetTempFileName()
    for ($try = 0; $try -lt 6; $try++) {
        $out = Sh "uiautomator dump --compressed $remote" 2>&1 | Out-String
        if ($out -notmatch "null root node" -and $out -notmatch "ERROR") {
            $pulled = Adb @("pull", $remote, $local) 2>&1 | Out-String
            $content = Get-Content $local -Raw -ErrorAction SilentlyContinue
            if ($content -and $content -match "<hierarchy") {
                Sh "rm -f $remote" | Out-Null
                Remove-Item $local -Force
                return [xml]$content
            }
        }
        Start-Sleep -Milliseconds 700
    }
    Sh "rm -f $remote" | Out-Null
    Remove-Item $local -Force -ErrorAction SilentlyContinue
    return $null
}

function Dismiss-Update-Dialog {
    # The app checks GitHub Releases on every Home entry and modal-blocks the screen when a newer
    # versionCode exists. Tests need it gone before they can read or assert anything.
    $ui = Get-UiRaw
    if ($ui -and (Get-Nodes $ui "//node[contains(@text,'Update available')]").Count -gt 0) {
        $script:UpdatePrompts++
        Tap-Text $ui "Later"
        Start-Sleep -Seconds 1
    }
}

# Every read goes through here, so the update dialog can never silently blank out an assertion.
function Get-Ui {
    $ui = Get-UiRaw
    if (-not $ui) { return $null }
    if ((Get-Nodes $ui "//node[contains(@text,'Update available')]").Count -gt 0) {
        $script:UpdatePrompts++
        Tap-Text $ui "Later"
        Start-Sleep -Seconds 1
        $ui = Get-UiRaw
    }
    # This AVD occasionally ANRs; the system dialog sits on top and hides the whole app tree.
    if ($ui -and (Get-Nodes $ui "//node[contains(@text,'Close app')]").Count -gt 0) {
        $script:AnrDialogs++
        $ui = Get-UiRaw
        $btn = Get-Nodes $ui "//node[@text='Wait']"
        if ($btn.Count -gt 0) { Tap-Coords (Get-Center $btn[0])[0] (Get-Center $btn[0])[1] } else { Back }
        Start-Sleep -Seconds 2
        $ui = Get-UiRaw
    }
    return $ui
}

function Get-Nodes {
    param($Ui, [string]$XPath)
    if (-not $Ui) { return @() }
    $nodes = Select-Xml -Xml $Ui -XPath $XPath -ErrorAction SilentlyContinue
    # leading comma: PowerShell unrolls single-element arrays on return, which breaks $n[0]
    if ($nodes) { return , @($nodes | ForEach-Object { $_.Node }) } else { return @() }
}

function Find-Desc { param($Ui, [string]$Text) ; Get-Nodes $Ui "//node[@content-desc='$Text']" }
function Find-Text { param($Ui, [string]$Text) ; Get-Nodes $Ui "//node[@text='$Text']" }function Find-TextLike { param($Ui, [string]$Pattern) ; Get-Nodes $Ui "//node[contains(@text,'$Pattern')]" }
# Compose text fields surface as class EditText/TextField with a hint/text
function Find-Field { param($Ui, [string]$Hint) ; Get-Nodes $Ui "//node[@class='android.widget.EditText' and @text='$Hint']" }

function Get-Center {
    param($Node)
    $m = [regex]::Match($Node.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $m.Success) { return $null }
    $x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
    $x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
    , @([int](($x1 + $x2) / 2), [int](($y1 + $y2) / 2))
}

function Tap-Desc {
    param($Ui, [string]$Text, [int]$TimeoutSec = 5)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $ui = Get-Ui
        $n = Find-Desc $ui $Text
        if ($n) {
            $c = Get-Center $n[0]
            Sh "input tap $($c[0]) $($c[1])" | Out-Null
            Start-Sleep -Milliseconds 500
            return $true
        }
        Start-Sleep -Milliseconds 400
    }
    return $false
}

function Tap-Text {
    param($Ui, [string]$Text, [int]$TimeoutSec = 5)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $ui = Get-Ui
        $n = Find-Text $ui $Text
        if ($n) {
            $c = Get-Center $n[0]
            Sh "input tap $($c[0]) $($c[1])" | Out-Null
            Start-Sleep -Milliseconds 500
            return $true
        }
        Start-Sleep -Milliseconds 400
    }
    return $false
}

function Tap-Coords { param([int]$X, [int]$Y) ; Sh "input tap $X $Y" | Out-Null ; Start-Sleep -Milliseconds 400 }

# ---------- text input ----------
# adb `input text` needs %s for space and cannot do newlines; use keyevent 66 for Enter.

function Type-Text {
    param([string]$Text)
    # single-quote the payload: adb shell re-runs it through sh, where * % ( ) would glob/expand
    $escaped = $Text -replace ' ', '%s'
    Sh "input text '$escaped'" | Out-Null
    Start-Sleep -Milliseconds 250
}

function Press-Enter { Sh "input keyevent 66" | Out-Null ; Start-Sleep -Milliseconds 200 }

function Back { Sh "input keyevent 4" | Out-Null ; Start-Sleep -Milliseconds 600 }

function Hide-Keyboard {
    # With the IME up, keyevent 4 is consumed by the IME and never reaches the app's BackHandler,
    # so navigation silently does nothing. Close it first.
    $ime = Sh "dumpsys input_method" | Out-String
    if ($ime -match "mIsInputViewShown=true" -or $ime -match "mInputShown=true") {
        Sh "input keyevent 4" | Out-Null
        Start-Sleep -Milliseconds 600
    }
}

# Back that actually navigates (hides the IME first).
function Back-Nav {
    Hide-Keyboard
    Sh "input keyevent 4" | Out-Null
    Start-Sleep -Seconds 1
    Dismiss-Update-Dialog
}

# Editor field geometry, read off the real hierarchy: the title BasicTextField sits in the top bar
# (~y147) and the body field just below it (~y301). Compose only exposes whichever field is
# populated as an EditText, so find-by-class is unreliable here.
function Get-TopBarY {
    $back = Get-Nodes (Get-Ui) "//node[@content-desc='Back & save']"
    if ($back.Count -gt 0) { return (Get-Center $back[0])[1] }
    return 148
}

function Tap-Title {
    Tap-Coords 540 (Get-TopBarY)
}

function Tap-Body {
    $top = Get-TopBarY
    $body = Get-Nodes (Get-Ui) "//node[@class='android.widget.EditText']" |
            Where-Object { (Get-Center $_)[1] -gt $top + 60 } |
            Select-Object -First 1
    if ($body) { $c = Get-Center $body } else { $c = @(540, $top + 153) }
    Tap-Coords $c[0] $c[1]
}

# ---------- assertions ----------

function Check {
    param([string]$Name, [bool]$Condition, [string]$Detail = "")
    if ($Condition) {
        $script:Pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $script:Fail++
        $script:Failures += "$Name $Detail"
        Write-Host "  FAIL  $Name $Detail" -ForegroundColor Red
    }
}

function Check-Text {
    # Substring match: Compose merges a composable's children into its parent's a11y node, so an
    # exact @text match only ever hits leaves. Absent apostrophes; test values don't contain one.
    param([string]$Name, [string]$Text, [switch]$Not)
    $ui = Get-Ui
    $hit = (Get-Nodes $ui "//node[contains(@text,'$Text')]").Count -gt 0
    Check $Name ($hit -ne $Not) "(text~$Text)"
}

function Check-Desc {
    param([string]$Name, [string]$Text, [switch]$Not)
    $ui = Get-Ui
    $hit = (Find-Desc $ui $Text).Count -gt 0
    Check $Name ($hit -ne $Not) "(desc='$Text')"
}

function Screenshot {
    param([string]$Name)
    $path = Join-Path $script:ShotDir "$Name.png"
    Adb exec-out screencap -p > $path 2>$null
    # adb exec-out is binary; PowerShell redirection mangles it, so use shell + pull
    if (-not (Test-Path $path) -or (Get-Item $path).Length -lt 2000) {
        $remote = "/data/local/tmp/aritiq-$Name.png"
        Sh "screencap -p $remote" | Out-Null
        Adb @("pull", $remote, $path) 2>&1 | Out-Null
        Sh "rm -f $remote" | Out-Null
    }
    Write-Host "  shot  $path"
}

function Dump-Ui {
    param([string]$Name)
    $ui = Get-Ui
    $path = Join-Path $script:ShotDir "$Name.xml"
    $ui.Save($path)
    Write-Host "  dump  $path"
}

# ---------- app control ----------

function Wake-Ready {
    # Two separate problems, both of which make `am start` fail with
    # "Activity class does not exist" / uiautomator return a null root node:
    #   1. the device is asleep  -> wake it
    #   2. the keyguard was never dismissed since boot -> PackageManager hides
    #      non-directBootAware components, so am start reports the activity missing
    Sh "input keyevent KEYCODE_WAKEUP" | Out-Null
    Start-Sleep -Seconds 1
    Sh "wm dismiss-keyguard" | Out-Null
    Start-Sleep -Seconds 1
    # fall back to an unlock swipe if dismiss-keyguard is ignored (no PIN set on AVD)
    if ((Sh "dumpsys window" | Out-String) -match "mDreamingLockscreen=true") {
        Sh "input keyevent 82" | Out-Null           # MENU dismisses a non-secure keyguard
        Start-Sleep -Milliseconds 500
        Sh "input swipe 540 1800 540 400 200" | Out-Null
        Start-Sleep -Seconds 1
    }
    $locked = (Sh "dumpsys window" | Out-String) -match "mDreamingLockscreen=true"
    if ($locked) { Write-Warning "device still locked; app launches will fail with type 3" }
}

function Dismiss-Update-Dialog {
    # The app checks GitHub Releases on launch; when a newer versionCode exists it
    # blocks the first screen. Tests need it gone.
    $ui = Get-Ui
    if ($ui -and (Get-Nodes $ui "//node[@text='Update available']").Count -gt 0) {
        Write-Host "  (dismissing 'Update available' dialog)"
        Tap-Text $ui "Later"
        Start-Sleep -Seconds 1
    }
}

function Launch-App {
    Wake-Ready
    Adb @("shell", "am", "start", "-W", "-n", $script:Activity) | Out-String
    Start-Sleep -Seconds 2
    Dismiss-Update-Dialog
}

function Stop-App { Adb @("shell", "am", "force-stop", $script:Pkg) | Out-Null ; Start-Sleep -Seconds 1 }

function Install-Apk {
    param([string]$Apk)
    Adb @("install", "-r", "-g", $Apk) | Out-String
}

function Clear-Data { Adb @("shell", "pm", "clear", $script:Pkg) | Out-Null ; Start-Sleep -Seconds 1 }

function Section { param([string]$Title) ; Write-Host "`n== $Title" -ForegroundColor Cyan }

function Summary {
    Write-Host "`n  'Update available' dialogs auto-dismissed: $script:UpdatePrompts" -ForegroundColor Yellow
    Write-Host "  emulator ANR dialogs dismissed: $script:AnrDialogs" -ForegroundColor Yellow
    Write-Host "---- $([math]::Round($script:Pass * 100.0 / [math]::Max($script:Pass + $script:Fail, 1)))%  pass=$script:Pass fail=$script:Fail" -ForegroundColor $(if ($script:Fail -eq 0) { "Green" } else { "Red" })
    if ($script:Fail -gt 0) {
        $script:Failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
        exit 1
    }
    exit 0
}
