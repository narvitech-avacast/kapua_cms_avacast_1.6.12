param(
    [string]$Destination = "background-image-migration-bundle"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$destinationRoot = [System.IO.Path]::GetFullPath((Join-Path $root $Destination))

$files = @(
    "background-image-migration.md",
    "kapua-console-patch/admin/signage-panel.html",
    "kapua-1.6.12/kapua-1.6.12/console/module/device/src/main/java/org/eclipse/kapua/app/console/module/device/client/device/signage/DeviceTabSignage.java",
    "kapua-1.6.12/kapua-1.6.12/console/module/device/src/main/java/org/eclipse/kapua/app/console/module/device/client/device/signage/DeviceTabSignageDescriptor.java",
    "kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/main/java/org/eclipse/kapua/app/api/resources/v1/resources/DeviceManagementDigitalSignage.java",
    "kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/main/java/org/eclipse/kapua/app/api/resources/v1/resources/SignageMediaLocation.java",
    "kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/main/java/org/eclipse/kapua/app/api/resources/v1/resources/SignagePlaylistResourceNormalizer.java",
    "kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/main/java/org/eclipse/kapua/app/api/resources/v1/resources/SignageFileRepos.java",
    "kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/main/java/org/eclipse/kapua/app/api/resources/v1/resources/LegacySignagePlaybackCompatibility.java",
    "kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/test/java/org/eclipse/kapua/app/api/resources/v1/resources/SignageMediaLocationTest.java",
    "kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/test/java/org/eclipse/kapua/app/api/resources/v1/resources/SignagePlaylistResourceNormalizerTest.java",
    "kapua-1.6.12/kapua-1.6.12/rest-api/resources/src/test/java/org/eclipse/kapua/app/api/resources/v1/resources/LegacySignagePlaybackCompatibilityTest.java"
)

foreach ($relativePath in $files) {
    $source = Join-Path $root $relativePath
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Missing migration source: $relativePath"
    }

    $target = Join-Path $destinationRoot $relativePath
    $targetDirectory = Split-Path -Parent $target
    New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -Force
}

Write-Host "Exported $($files.Count) files to:"
Write-Host $destinationRoot
