$ErrorActionPreference = "Stop"

$bundledPython = Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
if (Test-Path -LiteralPath $bundledPython) {
    & $bundledPython "$PSScriptRoot\verify_golden_reference.py"
    exit $LASTEXITCODE
}

$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if ($pythonCommand -and $pythonCommand.Source -notlike "*\WindowsApps\python.exe") {
    & $pythonCommand.Source "$PSScriptRoot\verify_golden_reference.py"
    exit $LASTEXITCODE
}

throw "Python non trovato. Installa Python 3 oppure esegui lo script in un ambiente Codex con runtime incluso."
