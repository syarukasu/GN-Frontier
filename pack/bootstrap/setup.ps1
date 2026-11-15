param(
    [string]$GameDir = (Split-Path -Parent $PSScriptRoot),
    [string]$Manifest = (Join-Path $PSScriptRoot 'downloads.json'),
    [switch]$VerifyOnly,
    [switch]$InstallOnly
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($GameDir).TrimEnd([IO.Path]::DirectorySeparatorChar)
if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw 'Instance/server directory does not exist' }
$rootPrefix = $root + [IO.Path]::DirectorySeparatorChar
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Resolve-PackPath([string]$Relative) {
    if (-not $Relative -or $Relative.Contains('\') -or $Relative.Contains(':') -or $Relative.Contains([char]0) -or (($Relative -split '/') | Where-Object { $_ -in @('', '.', '..') })) {
        throw "Unsafe relative path: $Relative"
    }
    $target = [IO.Path]::GetFullPath((Join-Path $root $Relative))
    if (-not $target.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Path escapes instance' }
    $current = $target
    while ($current -ne $root) {
        if (Test-Path -LiteralPath $current) {
            if ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Linked destination: $Relative" }
        }
        $current = [IO.Path]::GetDirectoryName($current)
    }
    return $target
}

function Test-PinnedFile([string]$Path, $Entry) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    if ((Get-Item -LiteralPath $Path).Length -ne $Entry.size) { return $false }
    return ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -eq $Entry.sha256)
}

function Get-PinnedFile($Entry) {
    $target = Resolve-PackPath $Entry.path
    if (Test-PinnedFile $target $Entry) { return 'verified' }
    if ($VerifyOnly) { throw "Missing or changed: $($Entry.path)" }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
    $lastFailure = ''
    foreach ($url in $Entry.urls) {
        $temporary = Join-Path ([IO.Path]::GetDirectoryName($target)) ('.gn-' + [Guid]::NewGuid().ToString('N') + '.part')
        $backup = $null
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $temporary -TimeoutSec 90 -Headers @{'User-Agent'='GN-Frontier-Setup/1'}
            if (-not (Test-PinnedFile $temporary $Entry)) { throw 'Downloaded hash/size differs' }
            if (Test-Path -LiteralPath $target) {
                $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffffffZ')
                $backup = Resolve-PackPath ('GN-Frontier-Setup/backups/' + $stamp + '/' + $Entry.path)
                [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($backup)) | Out-Null
                Move-Item -LiteralPath $target -Destination $backup
            }
            Move-Item -LiteralPath $temporary -Destination $target
            return 'downloaded'
        } catch {
            $lastFailure = $_.Exception.Message
            if ($backup -and (Test-Path -LiteralPath $backup) -and -not (Test-Path -LiteralPath $target)) {
                Move-Item -LiteralPath $backup -Destination $target
            }
        } finally {
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
        }
    }
    throw "Cannot fetch $($Entry.path): $lastFailure"
}

$data = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
if ($data.schema -ne 1 -or $data.kind -notin @('client-extras','server')) { throw 'Unsupported download manifest' }
$all = @($data.files)
if ($data.loader) { $all += $data.loader }
$seen = @{}
foreach ($entry in $all) {
    $null = Resolve-PackPath $entry.path
    if ($seen.ContainsKey($entry.path)) { throw 'Duplicate download path' }
    $seen[$entry.path] = $true
    if ($entry.sha256 -cnotmatch '^[0-9a-f]{64}$' -or $entry.size -lt 1) { throw 'Invalid pinned file hash/size' }
    if (-not $entry.urls -or @($entry.urls | Where-Object { $_ -notmatch '^https://' -or $_.Contains(' ') }).Count) { throw 'Encoded HTTPS URLs are required' }
}
$verified = 0
$downloaded = 0
foreach ($entry in $data.files) {
    $state = Get-PinnedFile $entry
    if ($state -eq 'verified') { $verified++ } else { $downloaded++ }
    Write-Host "[$($verified+$downloaded)/$($data.files.Count)] $state`: $($entry.path)"
}
if ($VerifyOnly) { Write-Host "VERIFIED $verified"; exit 0 }
$receipt = Resolve-PackPath 'GN-Frontier-Setup/installed-files.json'
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($receipt)) | Out-Null
$records = @($data.files | ForEach-Object { @{path=$_.path;sha256=$_.sha256} })
@{version=$data.version;kind=$data.kind;files=$records} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $receipt -Encoding UTF8
if ($data.kind -eq 'client-extras') { Write-Host 'External dependencies ready. Start the game from your launcher.'; exit 0 }

$java = if ($env:GN_JAVA) { $env:GN_JAVA } elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$versionInfo = New-Object Diagnostics.ProcessStartInfo
$versionInfo.FileName = $java
$versionInfo.Arguments = '-version'
$versionInfo.UseShellExecute = $false
$versionInfo.CreateNoWindow = $true
$versionInfo.RedirectStandardError = $true
$versionInfo.RedirectStandardOutput = $true
$probe = [Diagnostics.Process]::Start($versionInfo)
$versionText = $probe.StandardError.ReadToEnd() + $probe.StandardOutput.ReadToEnd()
$probe.WaitForExit()
if ($probe.ExitCode -ne 0 -or $versionText -notmatch 'version "17[.]') { throw 'Java 17 is required. Set GN_JAVA to its java.exe.' }
$forgeArgs = 'libraries/net/minecraftforge/forge/' + $data.minecraft + '-' + $data.forge + '/win_args.txt'
Push-Location -LiteralPath $root
try {
    if (-not (Test-Path -LiteralPath (Resolve-PackPath $forgeArgs))) {
        $null = Get-PinnedFile $data.loader
        & $java -jar (Resolve-PackPath $data.loader.path) --installServer
        if ($LASTEXITCODE -ne 0) { throw 'Forge installation failed' }
    }
    if (-not (Test-Path -LiteralPath (Resolve-PackPath $forgeArgs))) { throw 'Forge launch arguments missing' }
    if ($InstallOnly) { Write-Host 'Server installed; not started.'; exit 0 }
    $eulaPath = Join-Path $root 'eula.txt'
    if (-not (Test-Path -LiteralPath $eulaPath) -or (Get-Content -LiteralPath $eulaPath -Raw) -notmatch '(?m)^\s*eula\s*=\s*true\s*$') {
        throw 'Read https://www.minecraft.net/eula and set eula=true in eula.txt if you agree.'
    }
    & $java '@user_jvm_args.txt' ('@' + $forgeArgs) nogui
    exit $LASTEXITCODE
} finally { Pop-Location }
