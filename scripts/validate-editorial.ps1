param(
    [ValidateSet("development", "publication")]
    [string]$Mode = "development",

    [switch]$IncludeTests
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$validator = Join-Path $PSScriptRoot "editorial/validate_editorial.py"

& python $validator --root $repoRoot --mode $Mode
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if ($IncludeTests) {
    & python -m unittest "$PSScriptRoot/editorial/test_generate_guide.py" "$PSScriptRoot/editorial/test_validate_editorial.py"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    & (Join-Path $repoRoot "mvnw.cmd") test
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

exit 0
