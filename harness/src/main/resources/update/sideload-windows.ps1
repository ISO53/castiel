# Sideloads a downloaded castiel bundle: extracts it, waits for the running app
# to exit, then swaps the whole install directory. Writes update-ok / update-error
# markers plus update-failure.log into the work dir so the next launch reports it.
#
# Args come from the Java spawner: -AppPid -InstallDir -Archive -WorkDir -Mode -Version.
param(
	[int]$AppPid,
	[string]$InstallDir,
	[string]$Archive,
	[string]$WorkDir,
	[string]$Mode,
	[string]$Version
)
$ErrorActionPreference = 'Stop'

# Expand-Archive renders a progress bar; after FreeConsole that needs a console
# buffer that no longer exists (Win32 error 0x6). It is also faster without it.
$ProgressPreference = 'SilentlyContinue'

# Never hold the install dir as the working directory; Windows refuses renames then.
Set-Location $env:TEMP

# Markers and logs must be UTF-8 without BOM, or the Java reader sees a BOM prefix.
$Utf8 = [Text.UTF8Encoding]::new($false)
$LogFile = Join-Path $WorkDir 'sideload.log'

function Log([string]$Line) {
	Add-Content -Path $LogFile -Value "$(Get-Date -Format o) $Line" -Encoding utf8
}

function Fail([string]$Step, [object]$Error) {
	# Full error record: the message alone can be empty (corrupt zip FileFormatException).
	$Report = "step: $Step`nerror: $Error`n"
	# Snapshot every process whose path is under the install dir; it names the blocker.
	$Lockers = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
		Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($InstallDir, [StringComparison]::OrdinalIgnoreCase) }
	if ($Lockers) {
		$Report += "processes under the install dir:`n"
		$Report += ($Lockers | ForEach-Object { "$($_.ProcessId) $($_.ExecutablePath)" }) -join "`n"
	} else {
		$Report += 'processes under the install dir: none detected'
	}
	[IO.File]::WriteAllText((Join-Path $WorkDir 'update-failure.log'), $Report, $Utf8)
	[IO.File]::WriteAllText((Join-Path $WorkDir 'update-error'), 'The update could not be applied.', $Utf8)
	Log "failed at step $Step"
	if ($Mode -eq 'session') {
		Write-Host "Update failed. See $WorkDir\update-failure.log"
	}
}

if ($Mode -eq 'detached') {
	# Cut the tie to the app console so closing its window cannot kill this script.
	Add-Type -Namespace Native -Name Kernel32 -MemberDefinition '[DllImport("kernel32.dll")] public static extern bool FreeConsole();'
	[Native.Kernel32]::FreeConsole() | Out-Null
}

Log "started: app pid $AppPid, mode $Mode, target version $Version"
try {
	$NewDir = "$InstallDir.new"
	$OldDir = "$InstallDir.old"
	Remove-Item -LiteralPath $NewDir -Recurse -Force -ErrorAction SilentlyContinue
	Remove-Item -LiteralPath $OldDir -Recurse -Force -ErrorAction SilentlyContinue

	Log "extracting $Archive"
	Expand-Archive -LiteralPath $Archive -DestinationPath $NewDir -Force
	if (-not (Test-Path -LiteralPath (Join-Path $NewDir 'castiel'))) {
		throw 'the inner castiel folder is missing from the archive'
	}

	Log "waiting for app pid $AppPid to exit"
	while (Get-Process -Id $AppPid -ErrorAction SilentlyContinue) {
		Start-Sleep -Milliseconds 500
	}

	# Swap with retries: a straggler process inside the install dir can block the rename.
	$Swapped = $false
	for ($Attempt = 1; $Attempt -le 60; $Attempt++) {
		try {
			Log "swap attempt $Attempt"
			if ((Test-Path -LiteralPath $InstallDir) -and -not (Test-Path -LiteralPath $OldDir)) {
				Move-Item -LiteralPath $InstallDir -Destination $OldDir
			}
			Move-Item -LiteralPath (Join-Path $NewDir 'castiel') -Destination $InstallDir
			$Swapped = $true
			break
		} catch {
			Start-Sleep -Seconds 1
		}
	}
	if (-not $Swapped) {
		# Put the old build back so the app does not vanish from disk.
		if (-not (Test-Path -LiteralPath $InstallDir) -and (Test-Path -LiteralPath $OldDir)) {
			Move-Item -LiteralPath $OldDir -Destination $InstallDir
		}
		throw 'the install directory rename stayed blocked after 60 attempts'
	}

	[IO.File]::WriteAllText((Join-Path $WorkDir 'update-ok'), $Version, $Utf8)
	Log "updated to $Version"
	Remove-Item -LiteralPath $OldDir -Recurse -Force -ErrorAction SilentlyContinue
	Remove-Item -LiteralPath $NewDir -Recurse -Force -ErrorAction SilentlyContinue
	# The ~95 MB archive has no use after the swap.
	Remove-Item -LiteralPath $Archive -Force -ErrorAction SilentlyContinue
	Remove-Item -LiteralPath (Join-Path $WorkDir 'sideload.pid') -Force -ErrorAction SilentlyContinue
	if ($Mode -eq 'session') {
		Write-Host "castiel was updated to v$Version. Starting it again..."
		Start-Process -FilePath (Join-Path $InstallDir 'castiel.exe')
	}
} catch {
	Fail 'sideloading the new bundle' $_
}
