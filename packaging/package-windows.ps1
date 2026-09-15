# Builds the castiel desktop bundle for Windows: a self-contained app-image
# (castiel.exe + private bundled runtime, no installer, no system Java) zipped
# for direct use. Output lands in packaging/dist/.
#
# Usage:  .\package-windows.ps1 [-SkipUiBuild] [-SkipMaven] [-SkipArchive]
param(
	[switch]$SkipUiBuild,
	[switch]$SkipMaven,
	[switch]$SkipArchive
)
$ErrorActionPreference = 'Stop'
$packagingDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $packagingDir

# --- locate a JDK that ships jpackage (JAVA_HOME wins, then common installs) ---
function Find-Jpackage {
	$candidates = @()
	if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\jpackage.exe') }
	$candidates += Get-ChildItem 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
		ForEach-Object { Join-Path $_.FullName 'bin\jpackage.exe' }
	foreach ($candidate in $candidates) {
		if ($candidate -and (Test-Path $candidate)) { return $candidate }
	}
	throw 'jpackage.exe not found; install a full JDK 25+ or set JAVA_HOME.'
}
$jpackage = Find-Jpackage
Write-Host "Using $jpackage"

# --- 1. frontend (the built dist is embedded into the jar by Maven) ------------
if (-not $SkipUiBuild) {
	Push-Location (Join-Path $repoRoot 'frontend')
	if (-not (Test-Path node_modules)) {
		npm install
		if ($LASTEXITCODE) { exit $LASTEXITCODE }
	}
	npm run build
	if ($LASTEXITCODE) { exit $LASTEXITCODE }
	Pop-Location
}

# --- 2. harness fat jar ---------------------------------------------------------
Push-Location (Join-Path $repoRoot 'harness')
if (-not $SkipMaven) {
	mvn clean package '-DskipTests'
	if ($LASTEXITCODE) { exit $LASTEXITCODE }
}
[xml]$pom = Get-Content pom.xml
$version = $pom.project.version
Pop-Location
if (-not $version) { throw 'Could not read <version> from harness/pom.xml.' }
Write-Host "Packaging castiel $version"

# --- 3. stage the fat jar (jpackage copies everything under --input) ------------
$stage = Join-Path $packagingDir 'stage'
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $stage | Out-Null
Copy-Item (Join-Path $repoRoot "harness\target\castiel-$version.jar") $stage

# --- 4. app-image -----------------------------------------------------------------
# No --main-class on purpose: the Spring Boot fat jar must boot through its
# manifest Main-Class (the JarLauncher), which unpacks BOOT-INF; passing the app
# class directly fails with ClassNotFoundException.
# --win-console keeps stdout (the logs) visible; castiel is a console app.
$imageDir = Join-Path $packagingDir 'dist\castiel'
Remove-Item (Join-Path $packagingDir 'dist') -Recurse -Force -ErrorAction SilentlyContinue
& $jpackage `
	--type app-image `
	--name castiel `
	--app-version $version `
	--input $stage `
	--main-jar "castiel-$version.jar" `
	--win-console `
	--icon "$packagingDir\icons\castiel.ico" `
	--dest "$packagingDir\dist"
if ($LASTEXITCODE) { exit $LASTEXITCODE }
Write-Host "App image: $imageDir"

# --- 5. zip -----------------------------------------------------------------------
if (-not $SkipArchive) {
	$zip = Join-Path $packagingDir 'dist\castiel-windows-x64.zip'
	Compress-Archive -Path $imageDir -DestinationPath $zip -Force
	Write-Host "Created $zip"
}

# --- 6. clean up --------------------------------------------------------------------
Remove-Item $stage -Recurse -Force
