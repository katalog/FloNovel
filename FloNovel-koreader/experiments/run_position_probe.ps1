param([string]$Distribution = 'Ubuntu-24.04')
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$linuxPathOutput = & wsl -d $Distribution --exec wslpath -a $project
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the project in WSL.' }
$linuxProject = $linuxPathOutput.Trim()
& wsl -d $Distribution -- python3 "$linuxProject/experiments/run_position_probe.py" --prepare-only
if ($LASTEXITCODE -ne 0) { throw 'Fixture preparation failed.' }
& (Join-Path $PSScriptRoot 'run_kotlin_oracle.ps1')
& wsl -d $Distribution -- python3 "$linuxProject/experiments/run_position_probe.py" --checks-only --require-kotlin-oracle
if ($LASTEXITCODE -ne 0) { throw 'Position probe failed.' }
