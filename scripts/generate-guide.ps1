param(
    [Parameter(ParameterSetName = "Single", Mandatory = $true)]
    [string]$Manifest,

    [Parameter(ParameterSetName = "Single")]
    [string]$Body,

    [Parameter(ParameterSetName = "All", Mandatory = $true)]
    [switch]$All,

    [switch]$CheckOnly
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$generator = Join-Path $PSScriptRoot "editorial/generate_guide.py"
$arguments = @($generator)

if ($All) {
    $arguments += "--all"
} else {
    $arguments += @("--manifest", $Manifest)
    if ($Body) {
        $arguments += @("--body", $Body)
    }
}

if ($CheckOnly) {
    $arguments += "--check-only"
}

$arguments += @("--root", $repoRoot)
& python @arguments
exit $LASTEXITCODE
