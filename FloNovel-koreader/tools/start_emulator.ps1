param([switch]$Jit, [string]$Profile = 'desktop')
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'run_emulator.py'
$linuxPath = & wsl.exe --exec wslpath -u $scriptPath
if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the WSL launcher path.' }
$arguments = @('--exec', 'python3', $linuxPath.Trim(), '--profile', $Profile)
if ($Jit) { $arguments += '--jit' }
& wsl.exe @arguments
exit $LASTEXITCODE
