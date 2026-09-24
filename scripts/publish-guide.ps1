param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("run-id", "start", "heartbeat", "checkpoint", "publish", "status", "cancel")]
    [string]$Action,

    [string]$RunId,

    [string]$ContentId,

    [string[]]$RelatedContentId = @(),

    [string]$OwnerToken,

    [ValidateSet("research", "drafting", "generation", "validation")]
    [string]$Phase,

    [string[]]$SourceId = @(),

    [string[]]$Check = @(),

    [switch]$ManualRecovery
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$publisher = Join-Path $PSScriptRoot "editorial/git_publication.py"
$arguments = @($publisher, "--root", $repoRoot, $Action)

if ($Action -ne "run-id") {
    if ([string]::IsNullOrWhiteSpace($RunId)) {
        Write-Error "RunId è obbligatorio per l'azione $Action."
        exit 2
    }
    $arguments += @("--run-id", $RunId)
}

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

if ($Action -in @("heartbeat", "checkpoint", "publish", "cancel")) {
    if ([string]::IsNullOrWhiteSpace($OwnerToken)) {
        Write-Error "OwnerToken è obbligatorio per l'azione $Action."
        exit 2
    }
    $arguments += @("--owner-token", $OwnerToken)
}

if ($Action -eq "checkpoint") {
    if ([string]::IsNullOrWhiteSpace($Phase)) {
        Write-Error "Phase è obbligatoria per l'azione checkpoint."
        exit 2
    }
    $arguments += @("--phase", $Phase)
    foreach ($id in $SourceId) {
        $arguments += @("--source-id", $id)
    }
    foreach ($checkName in $Check) {
        $arguments += @("--check", $checkName)
    }
}

if ($ManualRecovery) {
    if ($Action -notin @("publish", "cancel")) {
        Write-Error "ManualRecovery è consentito soltanto con publish o cancel."
        exit 2
    }
    $arguments += "--manual-recovery"
}

& python @arguments
exit $LASTEXITCODE
