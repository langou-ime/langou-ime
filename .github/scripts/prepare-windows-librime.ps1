[CmdletBinding()]
param(
    [ValidateRange(1, 20)]
    [int]$MaximumAttempts = 8
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$windowsRoot = Join-Path $repositoryRoot "production\windows"
$archiveRoot = Join-Path $windowsRoot "deps\librime-1.13.1"
$temporaryRoot = if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    [System.IO.Path]::GetTempPath()
} else {
    $env:RUNNER_TEMP
}
$extractRoot = Join-Path $temporaryRoot "langou-librime-1.13.1"
$releaseRoot = "https://github.com/rime/librime/releases/download/1.13.1"

$assets = @(
    @{
        Name = "rime-1c23358-Windows-msvc-x64.7z"
        Sha256 = "05fcf8cc2d058a0186dd9f04d6e021ad41687db50dc81e85cf655dfabfdf0009"
        ExtractDirectory = "runtime-x64"
    },
    @{
        Name = "rime-1c23358-Windows-msvc-x86.7z"
        Sha256 = "22cb6288a5b30fd47e63ea56a5e0620c7198dbb178570da148cc33e4b589147f"
        ExtractDirectory = "runtime-x86"
    },
    @{
        Name = "rime-deps-1c23358-Windows-msvc-x64.7z"
        Sha256 = "3ede059e6c1f4cdd5843ced3205f76666b706e5f55ccf8e56e2d04791a376ff6"
        ExtractDirectory = "deps-x64"
    }
)

New-Item $archiveRoot -ItemType Directory -Force | Out-Null
if (Test-Path $extractRoot) {
    Remove-Item $extractRoot -Recurse -Force
}
New-Item $extractRoot -ItemType Directory -Force | Out-Null

function Test-ArchiveHash {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )

    if (-not (Test-Path $Path -PathType Leaf)) {
        return $false
    }
    $actual = (Get-FileHash $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    return $actual -eq $ExpectedSha256
}

function Get-PinnedAsset {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Asset
    )

    $archivePath = Join-Path $archiveRoot $Asset.Name
    if (Test-ArchiveHash -Path $archivePath -ExpectedSha256 $Asset.Sha256) {
        Write-Host "Using verified cached asset $($Asset.Name)."
        return $archivePath
    }
    Remove-Item $archivePath -Force -ErrorAction SilentlyContinue

    $partialPath = "$archivePath.part"
    for ($attempt = 1; $attempt -le $MaximumAttempts; $attempt++) {
        try {
            Remove-Item $partialPath -Force -ErrorAction SilentlyContinue
            Invoke-WebRequest `
                -Uri "$releaseRoot/$($Asset.Name)" `
                -OutFile $partialPath `
                -MaximumRedirection 10
            if (-not (Test-ArchiveHash -Path $partialPath -ExpectedSha256 $Asset.Sha256)) {
                throw "SHA-256 mismatch for $($Asset.Name)."
            }
            Move-Item $partialPath $archivePath -Force
            return $archivePath
        }
        catch {
            Remove-Item $partialPath -Force -ErrorAction SilentlyContinue
            if ($attempt -eq $MaximumAttempts) {
                throw "Unable to download and verify $($Asset.Name) after $MaximumAttempts attempts: $_"
            }
            Start-Sleep -Seconds ([Math]::Min(2 * $attempt, 15))
        }
    }

    throw "Unable to prepare $($Asset.Name)."
}

$sevenZip = (Get-Command "7z.exe" -ErrorAction Stop).Source
foreach ($asset in $assets) {
    $archivePath = Get-PinnedAsset -Asset $asset
    $destination = Join-Path $extractRoot $asset.ExtractDirectory
    New-Item $destination -ItemType Directory -Force | Out-Null
    & $sevenZip x $archivePath "-o$destination" -y | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "7-Zip failed to extract $($asset.Name)."
    }
}

$runtimeX64 = Join-Path $extractRoot "runtime-x64\dist"
$runtimeX86 = Join-Path $extractRoot "runtime-x86\dist"
$depsX64 = Join-Path $extractRoot "deps-x64"

@(
    "include",
    "lib",
    "lib64",
    "output",
    "output\Win32",
    "output\data\opencc"
) | ForEach-Object {
    New-Item (Join-Path $windowsRoot $_) -ItemType Directory -Force | Out-Null
}

Copy-Item (Join-Path $runtimeX64 "include\rime_*.h") (Join-Path $windowsRoot "include") -Force
Copy-Item (Join-Path $runtimeX64 "lib\rime.lib") (Join-Path $windowsRoot "lib64\rime.lib") -Force
Copy-Item (Join-Path $runtimeX64 "lib\rime.dll") (Join-Path $windowsRoot "output\rime.dll") -Force
Copy-Item (Join-Path $runtimeX86 "lib\rime.lib") (Join-Path $windowsRoot "lib\rime.lib") -Force
Copy-Item (Join-Path $runtimeX86 "lib\rime.dll") (Join-Path $windowsRoot "output\Win32\rime.dll") -Force
Copy-Item (Join-Path $depsX64 "share\opencc\*") (Join-Path $windowsRoot "output\data\opencc") -Recurse -Force

$requiredFiles = @(
    "include\rime_api.h",
    "include\rime_levers_api.h",
    "lib\rime.lib",
    "lib64\rime.lib",
    "output\rime.dll",
    "output\Win32\rime.dll",
    "output\data\opencc\TSCharacters.ocd2"
)
$missingFiles = $requiredFiles | Where-Object {
    $path = Join-Path $windowsRoot $_
    -not (Test-Path $path -PathType Leaf) -or (Get-Item $path).Length -eq 0
}
if ($missingFiles) {
    throw "Pinned librime preparation is incomplete: $($missingFiles -join ', ')"
}

Write-Host "Prepared hash-pinned librime 1.13.1 development binaries."
