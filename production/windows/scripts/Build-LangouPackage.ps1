[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ReleasePublicKeyBase64,

    [string]$Configuration = "Release"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$artifactRoot = Join-Path $repoRoot "artifacts\langou"
$payload = Join-Path $artifactRoot "payload"
$assistantPublish = Join-Path $artifactRoot "assistant"
$installerOutput = Join-Path $artifactRoot "installer"
$nsisRuntime = Join-Path $repoRoot "output\assistant-runtime"

if ((Split-Path -Leaf $artifactRoot) -ne "langou" -or
    -not $artifactRoot.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean an unexpected artifact path: $artifactRoot"
}

if (Test-Path $artifactRoot) {
    Remove-Item $artifactRoot -Recurse -Force
}
New-Item $payload -ItemType Directory -Force | Out-Null
New-Item $assistantPublish -ItemType Directory -Force | Out-Null
New-Item $installerOutput -ItemType Directory -Force | Out-Null
New-Item $nsisRuntime -ItemType Directory -Force | Out-Null

$requiredNativeFiles = @(
    "WeaselServer.exe",
    "WeaselDeployer.exe",
    "WeaselSetup.exe",
    "weasel.dll",
    "weaselx64.dll",
    "weasel.ime",
    "weaselx64.ime",
    "rime.dll"
)

foreach ($fileName in $requiredNativeFiles) {
    $source = Join-Path $repoRoot "output\$fileName"
    if (-not (Test-Path $source -PathType Leaf)) {
        throw "Required native build output is missing: $source"
    }
    Copy-Item $source (Join-Path $payload $fileName)
}

$dataSource = Join-Path $repoRoot "output\data"
if (-not (Test-Path $dataSource -PathType Container)) {
    throw "Bundled RIME data is missing: $dataSource"
}
Copy-Item $dataSource (Join-Path $payload "data") -Recurse
Copy-Item (Join-Path $repoRoot "rime-data\*") `
    (Join-Path $payload "data") `
    -Recurse `
    -Force
Copy-Item (Join-Path $repoRoot "LICENSE.txt") `
    (Join-Path $payload "LICENSE-RIME-WEASEL.txt")

dotnet restore (Join-Path $repoRoot "LangouAssistant.Tests\LangouAssistant.Tests.csproj") `
    --locked-mode `
    --nologo
if ($LASTEXITCODE -ne 0) {
    throw "Managed test dependency restore failed."
}
dotnet test (Join-Path $repoRoot "LangouAssistant.Tests\LangouAssistant.Tests.csproj") `
    --configuration $Configuration `
    --no-restore `
    --nologo
if ($LASTEXITCODE -ne 0) {
    throw "Managed tests failed."
}

dotnet restore (Join-Path $repoRoot "LangouAssistant\LangouAssistant.csproj") `
    --locked-mode `
    --nologo
if ($LASTEXITCODE -ne 0) {
    throw "Assistant dependency restore failed."
}
dotnet publish (Join-Path $repoRoot "LangouAssistant\LangouAssistant.csproj") `
    --configuration $Configuration `
    --runtime win-x64 `
    --self-contained true `
    --output $assistantPublish `
    -p:LANGOU_RELEASE_PUBLIC_KEY_BASE64=$ReleasePublicKeyBase64 `
    --no-restore `
    --nologo
if ($LASTEXITCODE -ne 0) {
    throw "Assistant publish failed."
}
Copy-Item (Join-Path $assistantPublish "*") $payload -Recurse -Force
Copy-Item (Join-Path $assistantPublish "*") $nsisRuntime -Recurse -Force

if (Get-ChildItem $payload -Recurse -File |
    Where-Object { $_.Name -like "*WinSparkle*" }) {
    throw "Retired WinSparkle files must never enter the Langou payload."
}
if (-not (Test-Path (Join-Path $nsisRuntime "LangouAssistant.exe") -PathType Leaf)) {
    throw "Assistant runtime was not staged for NSIS packaging."
}

$nsisCompiler = (Get-Command "makensis.exe" -ErrorAction SilentlyContinue).Source
if ([string]::IsNullOrWhiteSpace($nsisCompiler)) {
    $nsisCandidates = @()
    if (-not [string]::IsNullOrWhiteSpace(${env:ProgramFiles(x86)})) {
        $nsisCandidates += Join-Path ${env:ProgramFiles(x86)} "NSIS\makensis.exe"
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ProgramFiles)) {
        $nsisCandidates += Join-Path $env:ProgramFiles "NSIS\makensis.exe"
    }
    $nsisCompiler = $nsisCandidates |
        Where-Object { Test-Path $_ -PathType Leaf } |
        Select-Object -First 1
}
if ([string]::IsNullOrWhiteSpace($nsisCompiler)) {
    throw "NSIS compiler was not found on PATH or in the standard Program Files directories."
}

& $nsisCompiler `
    /DWEASEL_VERSION=1.0.0 `
    /DWEASEL_BUILD=0 `
    /DPRODUCT_VERSION=1.0.0.0 `
    (Join-Path $repoRoot "output\install.nsi")
if ($LASTEXITCODE -ne 0) {
    throw "EXE installer build failed."
}

$exe = Get-ChildItem (Join-Path $repoRoot "output\archives") `
    -Filter "langou-ime-windows-x64-v1.0.0.exe" `
    -Recurse |
    Select-Object -First 1
if ($null -eq $exe) {
    throw "Expected EXE installer was not produced."
}

$hash = Get-FileHash $exe.FullName -Algorithm SHA256
$hashLine = "$($hash.Hash.ToLowerInvariant())  $($exe.Name)"
Set-Content (Join-Path $artifactRoot "$($exe.Name).sha256") `
    $hashLine `
    -Encoding ascii
Copy-Item $exe.FullName $installerOutput -Force

Write-Host "Internal unsigned RC: $($exe.FullName)"
Write-Host "SHA-256: $($hash.Hash.ToLowerInvariant())"
