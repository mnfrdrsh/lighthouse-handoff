# pack-extension.ps1
# Simple packaging script for Lighthouse Handoff
# Run this from the project root to produce a clean zip suitable for
# "Load unpacked" or for Chrome Web Store upload (after adding screenshots etc.).

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dist = Join-Path $root "dist"
$zipName = "lighthouse-handoff.zip"
$zipPath = Join-Path $dist $zipName

# Clean previous
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Path $dist | Out-Null

# Files/folders to include (runtime + public documentation for end users)
$include = @(
    "manifest.json",
    "sidepanel.html",
    "sidepanel.css",
    "sidepanel.js",
    "options.html",
    "options.css",
    "options.js",
    "background.js",
    "report-builder.js",
    "report.html",
    "report.css",
    "report.js",
    "utils\",
    "api\",
    "icons\",
    "README.md",
    "GETTING-STARTED.md",
    "PRIVACY.md",
    "LICENSE",
    "CHANGELOG.md"
)

# Copy files
foreach ($item in $include) {
    $src = Join-Path $root $item
    $dst = Join-Path $dist $item
    if (Test-Path $src) {
        if ((Get-Item $src).PSIsContainer) {
            Copy-Item $src $dst -Recurse -Force
        } else {
            Copy-Item $src $dst -Force
        }
    }
}

# Remove dev-only files from the package (keep only what an end-user needs)
$devFiles = @(
    (Join-Path $dist "icons\ICONS-README.txt"),
    (Join-Path $dist "icons\logo-source.png"),
    (Join-Path $dist "icons\logo-icon-symbol.png"),
    (Join-Path $dist "icons\logo.png"),
    (Join-Path $dist "pack-extension.ps1"),
    (Join-Path $dist "IMPLEMENTATION-PLAN.md"),
    (Join-Path $dist "TASKS.md"),
    (Join-Path $dist "project-details.md")
)

foreach ($f in $devFiles) {
    if (Test-Path $f) { Remove-Item $f -Force }
}

# Create zip
Compress-Archive -Path (Join-Path $dist "*") -DestinationPath $zipPath -Force

Write-Host "Created clean package: $zipPath"
Write-Host ""
Write-Host "For 'Load unpacked' (friends, testers, GitHub releases):"
Write-Host "  1. Unzip the archive."
Write-Host "  2. Go to chrome://extensions → enable Developer mode → Load unpacked → select the unzipped folder."
Write-Host ""
Write-Host "For Chrome Web Store (real 'anyone can install' distribution):"
Write-Host "  1. Go to the Chrome Web Store developer dashboard."
Write-Host "  2. Upload the zip as a new item (or update)."
Write-Host "  3. Fill in store listing (copy/adapt from README.md), add screenshots (take 1280x800 or 640x400 shots of the side panel in action), and link to PRIVACY.md."
Write-Host "  4. Submit for review."
Write-Host ""
Write-Host "Repo: https://github.com/mnfrdrsh/lighthouse-handoff"
Write-Host "After publishing to the store, update the 'Install' section in README.md and GETTING-STARTED.md with the real store link."
Write-Host ""
Write-Host "The packaged zip intentionally excludes dev-only files (old plans, test checklists, source logo files, etc.)."
