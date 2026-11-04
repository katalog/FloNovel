$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path 'test-results', 'bin' | Out-Null
    $events = & go test -count=1 -json ./...
    $testExit = $LASTEXITCODE
    $events | Set-Content -LiteralPath 'test-results/results.jsonl' -Encoding utf8
    $tests = @($events | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object {
        $_.Test -and $_.Action -in @('pass', 'fail', 'skip')
    })
    $allTests = $tests
    $tests = @($allTests | Where-Object {
        $testName = $_.Test
        -not @($allTests | Where-Object { $_.Test.StartsWith($testName + '/') }).Count
    })
    $failed = @($tests | Where-Object Action -eq 'fail').Count
    $skipped = @($tests | Where-Object Action -eq 'skip').Count
    $document = [System.Xml.XmlDocument]::new()
    $suite = $document.CreateElement('testsuite')
    $suite.SetAttribute('name', 'text-splitter')
    $suite.SetAttribute('tests', [string]$tests.Count)
    $suite.SetAttribute('failures', [string]$failed)
    $suite.SetAttribute('skipped', [string]$skipped)
    foreach ($test in $tests) {
        $case = $document.CreateElement('testcase')
        $case.SetAttribute('name', $test.Test)
        $case.SetAttribute('time', [string]$test.Elapsed)
        if ($test.Action -eq 'fail') { $null = $case.AppendChild($document.CreateElement('failure')) }
        if ($test.Action -eq 'skip') { $null = $case.AppendChild($document.CreateElement('skipped')) }
        $null = $suite.AppendChild($case)
    }
    $null = $document.AppendChild($suite)
    $report = Join-Path $PSScriptRoot 'test-results/results.xml'
    $document.Save($report)
    [xml]$verified = Get-Content -LiteralPath $report -Raw
    Write-Host "Executed $($verified.testsuite.tests) tests; failures=$($verified.testsuite.failures); skipped=$($verified.testsuite.skipped)"
    if ($testExit -ne 0 -or $tests.Count -eq 0 -or $failed -ne 0) { throw 'Tests failed. See test-results/results.jsonl.' }
    & go vet ./...
    if ($LASTEXITCODE -ne 0) { throw 'go vet failed.' }
    & go build -trimpath -ldflags '-s -w -H=windowsgui' -o bin/TextSplitter.exe .
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    Write-Host 'Built bin/TextSplitter.exe'
} finally {
    Pop-Location
}
