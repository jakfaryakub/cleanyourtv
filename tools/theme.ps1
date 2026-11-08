# Windows version of theme.sh: push a theme folder (config.json, and optionally a wallpaper.jpg, a
# wallpapers/ collection, fonts/ or icons/) to the launcher on the TV and restart Home. See themes/README.md for the keys.
#
# Usage: tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
#        tools\theme.ps1 -TvHost 192.168.1.50 -Reset
#
# Theme slots, as in theme.sh (docs/PLAN.md, Phase 3):
#        tools\theme.ps1 -Slot <slug> themes\aurora     # push into themes/<slug>/ on the TV, without switching to it
#        tools\theme.ps1 -Use <slug|legacy|builtin>     # switch: a slot, the theme pushed without -Slot, or config.xml
#        tools\theme.ps1 -List                          # the themes on the TV, their size, and the one in use
#        tools\theme.ps1 -Delete <slug>                 # remove a slot (not the one in use)
# Same slugs and limits as theme.sh (tools/theme_slots.py). A plain push and -Reset take the TV off any slot.
#
# One argument, and -Reset takes none: both together are refused rather than one of them dropped (below).
# Exit status: 0 the theme is on the TV, or the reset ran; 1 the theme could not be pushed, or Home did not
# restart after it (a failure of the shared helper stops this script with that status); 2 the run stopped
# before the theme was up and nothing was sent - the command line is not one theme folder or -Reset, the
# folder has no config.json or one the launcher would drop, a wallpaper name b64push.py refuses, no TV host
# was given, or tools\venv is missing. 1 is a run that reached the push, so a theme this script cannot read
# answers 2, as theme.sh does, instead of stopping with a PowerShell error record.
param(
    [Parameter(Position = 0)][string]$Theme,
    [switch]$Reset,
    [string]$Slot,
    [string]$Use,
    [switch]$List,
    [string]$Delete,
    [string]$TvHost = $env:TV_HOST
)
# The slot forms, each alone on its command line ($Theme only with -Slot). The slug rule is the launcher's.
$slotForms = @($Slot, $Use, $Delete | Where-Object { $_ }).Count + [int][bool]$List
if ($slotForms -gt 1 -or ($slotForms -and $Reset) -or (($Use -or $Delete -or $List) -and $Theme) -or ($Slot -and -not $Theme)) {
    [Console]::Error.WriteLine('usage: theme.ps1 -Slot <slug> <theme folder> | -Use <slug|legacy|builtin> | -List | -Delete <slug>. Nothing was sent to the TV.')
    exit 2
}
function Test-Slug($name) { $name -cmatch '^[a-z0-9][a-z0-9-]{0,31}$' -and $name -ne 'legacy' -and $name -ne 'builtin' }
foreach ($name in @($Slot, $Delete | Where-Object { $_ })) {
    if (-not (Test-Slug $name)) {
        [Console]::Error.WriteLine("unsupported slot name: $name (lower-case letters, digits and '-', at most 32, starting with a letter or digit, and not legacy or builtin). Nothing was sent to the TV.")
        exit 2
    }
}
if ($Use -and $Use -ne 'legacy' -and $Use -ne 'builtin' -and -not (Test-Slug $Use)) {
    [Console]::Error.WriteLine("theme.ps1 -Use takes a slug, legacy or builtin, so $Use is not one. Nothing was sent to the TV.")
    exit 2
}
# theme.sh judges its own command line here, and this is the same judgement: -Reset removes the theme from the
# TV and a folder pushes it, so `-Reset themes\aurora` used to take the reset branch and throw the folder away
# (the theme the reader named was removed instead of pushed), while `themes\aurora -Reset` pushed the theme and
# ignored the reset. Nothing is sent for either.
if ($Theme -and $Reset) {
    [Console]::Error.WriteLine("theme.ps1 takes one argument, a theme folder or -Reset: $Theme would not have been used, and -Reset removes the theme from the TV while a folder pushes it, so the two cannot share a command line. Nothing was sent to the TV.")
    exit 2
}
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) {
    # The same status theme.sh answers, for the same reason: a missing address is a run that never started, not
    # a theme that could not be pushed, and 1 is what the header keeps for the push that ran and failed.
    [Console]::Error.WriteLine('pass -TvHost <tv ip> or set $env:TV_HOST. Nothing was sent to the TV.')
    exit 2
}
$env:TV_HOST = $TvHost
# Resolve the theme folder against the caller's directory before moving into tools\.
# A folder that is not there keeps the path as typed: Resolve-Path answers a missing path with its own error
# record and a script line number, where theme.sh stops at "no config.json in <typed path>" - a message about
# the folder the reader named, not about the cmdlet.
if ($Theme -and (Test-Path -LiteralPath $Theme)) { $Theme = (Resolve-Path -LiteralPath $Theme).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) {
    # As in theme.sh: the venv is on this computer, so nothing was sent and this is a run that stopped before the
    # theme was up, not a push that ran and failed.
    [Console]::Error.WriteLine('run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt. Nothing was sent to the TV.')
    exit 2
}
$pkg = 'com.gathin.tvhome'
# Runs tools\tv.py or b64push.py and stops on a non-zero exit: 'Stop' only covers cmdlets, so a failed
# native call would otherwise fall through to "Theme ... is on the TV".
function Invoke-Tv {
    $out = & $py -I @args
    if ($LASTEXITCODE -ne 0) { throw "$($args[0]) $($args[1]) failed (exit code $LASTEXITCODE); the theme was not fully applied" }
    $out
}
$dir = "/sdcard/Android/data/$pkg/files"
$revision = [guid]::NewGuid().ToString('N')
# One shared recovery path verifies the helpers after Home restarts.

# Some firmware (TCL G07, Android 11) locks the shell out of Android/data. Files are staged in
# /data/local/tmp and copied in, or written through the launcher's ThemeProvider when that is refused.
# The provider has to be tried first: on Android 14 the shell can write into Android/data, so cp "succeeds",
# but the file it creates belongs to the shell (uid 2000) and the launcher then gets EACCES opening it, which
# silently ignores the theme whenever the file did not exist yet - a fresh install, or after a reinstall.
# A provider write creates the file as the launcher, and it cannot truncate a file the shell owns either, so
# remove our copy first. cp stays as the fallback for firmware without the provider.
$uri = "content://$pkg.theme"
$tmp = '/data/local/tmp/cyt-theme'
# -Slot pushes the same files into themes/<slug>/; theme_revision and active_theme stay at the root, and nothing
# makes the slot folder from the shell (the provider creates it with the first file), as in theme.sh.
$rootDir = $dir
$rootUri = $uri
if ($Slot) { $dir = "$rootDir/themes/$Slot"; $uri = "$rootUri/themes/$Slot" }
$query = "content query --uri $rootUri/themes"
# A file's staging path; wallpapers/a.jpg is staged as $tmp-wallpapers-a.jpg.
function Stage($name) { "$tmp-$($name -replace '/', '-')" }
# Android 8 and 9 have no `content write`: they print "[ERROR] Unsupported operation: write" but exit 0, so
# that message counts as a failure and cp runs. So does "Error while accessing provider", which `content` prints
# when the launcher has no provider or gets EACCES.
# A folder the shell makes is the shell's (drwxrws--- shell ext_data_rw), and the provider, which runs as the
# launcher, then gets EACCES writing into it. Nothing makes wallpapers/ up front: the provider creates it with the
# first picture, and only the cp fallback, where the shell does the writing, runs mkdir.
function Put($name) { $t = Stage $name; $d = "$dir/$name" -replace '/[^/]*$', ''; "rm -f $dir/$name 2>/dev/null; { o=`$(content write --uri $uri/$name < $t 2>&1) && case `"`$o`" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac; } || { mkdir -p $d 2>/dev/null; cp $t $dir/$name 2>/dev/null; }; rm -f $t" }
function Drop($name) { "rm -f $dir/$name 2>/dev/null; content delete --uri $uri/$name 2>/dev/null" }

# The collection a theme can push. A push empties it before writing the theme's own pictures, and -Reset has to
# empty it too: the launcher's wallpaper picker lists that folder itself, and an on-TV pick of one of its
# pictures is read back from it, so a reset that left the pictures behind still shows the theme it just removed.
# Both paths use this one string (the same one theme.sh has).
# The theme's fonts (config.json "font"), emptied on every push and -Reset, as in theme.sh.
$clearFonts = "rm -f $dir/fonts/*.[tT][tT][fF] $dir/fonts/*.[oO][tT][fF] 2>/dev/null; content delete --uri $uri/fonts 2>/dev/null"
# The theme's app logos (icons/<package>.png), emptied on every push and -Reset, as in theme.sh.
$clearIcons = "rm -f $dir/icons/*.[pP][nN][gG] 2>/dev/null; content delete --uri $uri/icons 2>/dev/null"
$clearWallpapers = "rm -f $dir/wallpapers/*.[jJ][pP][gG] $dir/wallpapers/*.[jJ][pP][eE][gG] $dir/wallpapers/*.[pP][nN][gG] $dir/wallpapers/*.[wW][eE][bB][pP] 2>/dev/null; content delete --uri $uri/wallpapers 2>/dev/null"

if ($List) {
    Invoke-Tv tv.py $query | & $py -I theme_slots.py list
    exit 0
}
if ($Use) {
    $why = Invoke-Tv tv.py $query | & $py -I theme_slots.py use $Use
    if ($LASTEXITCODE) { [Console]::Error.WriteLine("$why Nothing was changed on the TV."); exit 2 }
    Invoke-Tv tv.py "printf '%s' '$Use' > $tmp-active_theme; $(Put 'active_theme')" | Out-Null
    & $py -I theme_restart.py
    if ($LASTEXITCODE) { throw 'Home restart recovery failed' }
    Write-Host "Home now uses $Use. Undo with: tools\theme.ps1 -Use <slug|legacy|builtin>"
    exit 0
}
if ($Delete) {
    $why = Invoke-Tv tv.py $query | & $py -I theme_slots.py delete $Delete
    if ($LASTEXITCODE) { [Console]::Error.WriteLine("$why Nothing was changed on the TV."); exit 2 }
    Invoke-Tv tv.py "content delete --uri $rootUri/themes/$Delete 2>/dev/null; rm -rf $rootDir/themes/$Delete 2>/dev/null" | Out-Null
    Write-Host "Slot $Delete is removed from the TV."
    exit 0
}
if ($Reset) {
    Invoke-Tv tv.py "mkdir -p $dir 2>/dev/null; $(Drop 'active_theme'); $(Drop 'config.json'); $(Drop 'wallpaper.jpg'); $(Drop 'wallpaper.webp'); $(Drop 'wallpapers/captions.txt'); $clearWallpapers; $clearFonts; $clearIcons; printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision')" | Out-Null
    & $py -I theme_restart.py
    if ($LASTEXITCODE) { throw 'Home restart recovery failed' }
    Write-Host 'reset'
    exit 0
}
if (-not $Theme) { [Console]::Error.WriteLine('usage: theme.ps1 <theme folder> | -Reset'); exit 2 }
$config = Join-Path $Theme 'config.json'
# The three refusals below send nothing, so they answer 2 like theme.sh's - not 1, the status of a push that
# ran and failed - and in this script's own words instead of a PowerShell error record with a script line.
if (-not (Test-Path -LiteralPath $config)) {
    [Console]::Error.WriteLine("no config.json in $Theme. Nothing was sent to the TV.")
    exit 2
}
try { $themeJson = Get-Content $config -Raw | ConvertFrom-Json }
catch {
    [Console]::Error.WriteLine("$config is not valid JSON, so there is no theme to push (themes/README.md says what the file holds). Nothing was sent to the TV.")
    exit 2
}
# Config.read() answers "no overlay" for anything that is not a JSON object, so an array, a number or a
# string is a theme the launcher drops - and it replaces the theme the set had with the built-in config.
# Judged here, before anything is pushed, so "is on the TV" cannot mean the opposite.
if ($themeJson -isnot [System.Management.Automation.PSCustomObject]) {
    [Console]::Error.WriteLine("$config is not a JSON object of theme keys, so the launcher would ignore it and the TV would go back to its built-in config (themes/README.md). Nothing was sent to the TV.")
    exit 2
}
# Every collection name is checked before anything changes on the TV, as in theme.sh: a name refused halfway
# through the push left the new config.json and part of the collection behind. b64push.py refuses '..' in a path.
$wallpapers = Join-Path $Theme 'wallpapers'
$images = @()
if (Test-Path $wallpapers -PathType Container) {
    $images = @(Get-ChildItem $wallpapers -File | Where-Object { $_.Extension -match '^\.(jpg|jpeg|png|webp)$' })
    foreach ($image in $images) {
        if ($image.Name -notmatch '^[A-Za-z0-9_.-]+$' -or $image.Name.Contains('..')) {
            [Console]::Error.WriteLine("unsupported wallpaper filename: $($image.Name) (b64push.py takes letters, digits, '.', '_' and '-', with no '..' in the name). Nothing was sent to the TV.")
            exit 2
        }
    }
}
# The one font the theme names (config.json "font"), as in theme.sh: only that file is sent, none without the key,
# and a refused name or a missing file stops the run before anything is sent.
$fonts = @()
$fontKey = ''
if ($themeJson.PSObject.Properties.Name -contains 'font' -and $themeJson.font -is [string]) { $fontKey = $themeJson.font.Trim() }
if ($fontKey) {
    if ($fontKey -notmatch '^[A-Za-z0-9_.-]+\.(ttf|otf)$' -or $fontKey.Contains('..') -or $fontKey.StartsWith('.')) {
        [Console]::Error.WriteLine("unsupported font filename: $fontKey (config.json ""font"": a .ttf or .otf named with letters, digits, '.', '_' and '-', with no '..' in the name). Nothing was sent to the TV.")
        exit 2
    }
    $fontFile = Join-Path (Join-Path $Theme 'fonts') $fontKey
    if (-not (Test-Path $fontFile -PathType Leaf)) {
        [Console]::Error.WriteLine("config.json names the font $fontKey, but $fontFile is not there. Nothing was sent to the TV.")
        exit 2
    }
    $fonts = @(Get-Item $fontFile)
}
# Every app logo in icons/, named after its package, as in theme.sh: a refused name stops the run before anything is sent.
$iconsDir = Join-Path $Theme 'icons'
$icons = @()
if (Test-Path $iconsDir -PathType Container) {
    $icons = @(Get-ChildItem $iconsDir -File)
    foreach ($icon in $icons) {
        if ($icon.Name -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+\.(png)$') {
            [Console]::Error.WriteLine("unsupported logo filename: icons/$($icon.Name) (an app logo is a .png named after the app's package, e.g. com.netflix.ninja.png). Nothing was sent to the TV.")
            exit 2
        }
    }
}

if ($Slot) {
    $answer = (@(Invoke-Tv tv.py $query) + '--df--' + @(Invoke-Tv tv.py 'df -k /sdcard')) -join "`n"
    $fontName = if ($fonts.Count) { $fonts[0].Name } else { '' }
    $why = $answer | & $py -I theme_slots.py check $Theme $Slot $fontName
    if ($LASTEXITCODE) { [Console]::Error.WriteLine("$why Nothing was sent to the TV."); exit 2 }
}
# Tolerated, as in theme.sh: a TV that locks Android/data still takes the files through ThemeProvider.
Invoke-Tv tv.py "mkdir -p $rootDir 2>/dev/null; true" | Out-Null
Invoke-Tv tv.py --push $config "$tmp-config.json" | Out-Null
Invoke-Tv tv.py (Put 'config.json')
$wall = Join-Path $Theme 'wallpaper.jpg'
if (Test-Path $wall) { Invoke-Tv b64push.py $wall "$tmp-wallpaper.jpg"; Invoke-Tv tv.py (Put 'wallpaper.jpg') } else { Invoke-Tv tv.py (Drop 'wallpaper.jpg') | Out-Null }
# The animated wallpaper (wallpaper_animated), as theme.sh sends it.
$webp = Join-Path $Theme 'wallpaper.webp'
if (Test-Path $webp) { Invoke-Tv b64push.py $webp "$tmp-wallpaper.webp"; Invoke-Tv tv.py (Put 'wallpaper.webp') } else { Invoke-Tv tv.py (Drop 'wallpaper.webp') | Out-Null }
Invoke-Tv tv.py $clearWallpapers | Out-Null
foreach ($image in $images) {
    Invoke-Tv b64push.py $image.FullName (Stage "wallpapers/$($image.Name)")
    Invoke-Tv tv.py (Put "wallpapers/$($image.Name)") | Out-Null
}
Invoke-Tv tv.py $clearFonts | Out-Null
foreach ($font in $fonts) {
    Invoke-Tv b64push.py $font.FullName (Stage "fonts/$($font.Name)")
    Invoke-Tv tv.py (Put "fonts/$($font.Name)") | Out-Null
}
Invoke-Tv tv.py $clearIcons | Out-Null
foreach ($icon in $icons) {
    Invoke-Tv b64push.py $icon.FullName (Stage "icons/$($icon.Name)")
    Invoke-Tv tv.py (Put "icons/$($icon.Name)") | Out-Null
}
# The collection's names (wallpaper_captions); a theme without them leaves no stale names behind.
$captions = Join-Path $Theme 'wallpapers\captions.txt'
if (Test-Path $captions -PathType Leaf) {
    Invoke-Tv b64push.py $captions (Stage 'wallpapers/captions.txt')
    Invoke-Tv tv.py (Put 'wallpapers/captions.txt') | Out-Null
} else {
    Invoke-Tv tv.py (Drop 'wallpapers/captions.txt') | Out-Null
}
$dir = $rootDir
$uri = $rootUri
# A plain push is the theme the reader wants to see, so it also takes the TV off any slot.
if (-not $Slot) { Invoke-Tv tv.py (Drop 'active_theme') | Out-Null }
Invoke-Tv tv.py "printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision')" | Out-Null
& $py -I theme_restart.py
if ($LASTEXITCODE) { throw 'Home restart recovery failed' }
if ($Slot) {
    Write-Host "Theme $(Split-Path $Theme -Leaf) is on the TV in slot $Slot. Switch to it in Launcher settings, or with: tools\theme.ps1 -Use $Slot"
    exit 0
}
Write-Host 'applied'
Write-Host "Theme $(Split-Path $Theme -Leaf) is on the TV. Undo with: tools\theme.ps1 -TvHost $TvHost -Reset"
