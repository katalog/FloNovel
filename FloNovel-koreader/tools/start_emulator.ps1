param([switch]$Jit, [string]$Profile = 'desktop', [ValidateRange(0, 100)][int]$CpuPercent = 5)
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'run_emulator.py'
$linuxPath = & wsl.exe --exec wslpath -u $scriptPath
if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the WSL launcher path.' }
$arguments = @('--exec', 'python3', $linuxPath.Trim(), '--profile', $Profile, '--cpu-percent', $CpuPercent)
if ($Jit) { $arguments += '--jit' }
& wsl.exe @arguments
exit $LASTEXITCODE
