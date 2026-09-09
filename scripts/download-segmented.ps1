param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][string]$Output,
    [Parameter(Mandatory = $true)][long]$Length,
    [Parameter(Mandatory = $true)][string]$Sha256,
    [ValidateRange(1, 16)][int]$Segments = 8
)

$ErrorActionPreference = 'Stop'
$partRoot = "$Output.parts"
New-Item -ItemType Directory -Force -Path $partRoot | Out-Null
$chunkSize = [math]::Ceiling($Length / $Segments)
$ranges = for ($index = 0; $index -lt $Segments; $index++) {
    $start = [long]($index * $chunkSize)
    $end = [long][math]::Min($Length - 1, (($index + 1) * $chunkSize) - 1)
    [PSCustomObject]@{ Index = $index; Start = $start; End = $end }
}

$downloads = foreach ($range in $ranges) {
    $part = Join-Path $partRoot ("part-{0:D2}" -f $range.Index)
    $expected = $range.End - $range.Start + 1
    if ((Test-Path -LiteralPath $part) -and (Get-Item -LiteralPath $part).Length -eq $expected) {
        continue
    }
    $arguments = @(
        '-L', '--http1.1', '--fail', '--silent', '--show-error',
        '--retry', '20', '--retry-all-errors', '--retry-delay', '2',
        '--range', "$($range.Start)-$($range.End)", '--output', $part, $Url
    )
    [PSCustomObject]@{
        Range = $range
        Part = $part
        Expected = $expected
        Process = Start-Process -FilePath 'curl.exe' -ArgumentList $arguments -WindowStyle Hidden -PassThru
    }
}

foreach ($download in $downloads) {
    $download.Process.WaitForExit()
    if ($download.Process.ExitCode -ne 0) {
        throw "Segment $($download.Range.Index) failed with exit code $($download.Process.ExitCode)"
    }
    if ((Get-Item -LiteralPath $download.Part).Length -ne $download.Expected) {
        throw "Segment $($download.Range.Index) has an unexpected length"
    }
    Write-Host "Segment $($download.Range.Index + 1)/$Segments complete"
}

$destination = [System.IO.File]::Open($Output, [System.IO.FileMode]::Create)
try {
    foreach ($range in $ranges) {
        $part = Join-Path $partRoot ("part-{0:D2}" -f $range.Index)
        $source = [System.IO.File]::OpenRead($part)
        try { $source.CopyTo($destination) } finally { $source.Dispose() }
    }
} finally {
    $destination.Dispose()
}

$actualHash = (Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash
if ($actualHash -ne $Sha256) {
    throw "SHA-256 mismatch. Expected $Sha256 but got $actualHash"
}
Write-Host "Downloaded and verified: $Output"
