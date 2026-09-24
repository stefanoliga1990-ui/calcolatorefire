param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "publish", "status", "cancel")]
    [string]$Action,

    [Parameter(Mandatory = $true)]
    [string]$RunId,

    [string]$ContentId,

    [string[]]$RelatedContentId = @()
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$publisher = Join-Path $PSScriptRoot "editorial/git_publication.py"
$arguments = @($publisher, "--root", $repoRoot, $Action, "--run-id", $RunId)

if ($Action -eq "start") {
    if ([string]::IsNullOrWhiteSpace($ContentId)) {
        Write-Error "ContentId è obbligatorio per l'azione start."
        exit 2
    }
    $arguments += @("--content-id", $ContentId)
    foreach ($relatedId in $RelatedContentId) {
        $arguments += @("--related-content-id", $relatedId)
    }
}

& python @arguments
exit $LASTEXITCODE
