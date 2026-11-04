param(
    [ValidateSet('FixtureOracle.kt', 'ChapterOracle.kt', 'MarkerOracle.kt', 'ContractOracle.kt')][string]$SourceName = 'FixtureOracle.kt',
    [ValidateSet('FixtureOracleKt', 'ChapterOracleKt', 'MarkerOracleKt', 'ContractOracleKt')][string]$EntryPoint = 'FixtureOracleKt'
)
$ErrorActionPreference = 'Stop'
if (([IO.Path]::GetFileNameWithoutExtension($SourceName) + 'Kt') -ne $EntryPoint) {
    throw 'Oracle source and entry point must match.'
}
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repository = (Resolve-Path (Join-Path $project '..')).Path
$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$cache = Join-Path $gradleHome 'caches/modules-2/files-2.1'
$modules = @(
    'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.21',
    'org.jetbrains.kotlin/kotlin-stdlib/2.0.21',
    'org.jetbrains.kotlin/kotlin-script-runtime/2.0.21',
    'org.jetbrains.kotlin/kotlin-reflect/1.6.10',
    'org.jetbrains.kotlin/kotlin-daemon-embeddable/2.0.21',
    'org.jetbrains.intellij.deps/trove4j',
    'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm',
    'org.jetbrains/annotations',
    'org.json/json/20240303',
    'com.github.albfernandez/juniversalchardet/2.5.0'
)
$jars = foreach ($module in $modules) {
    $modulePath = Join-Path $cache $module
    if (Test-Path -LiteralPath $modulePath) {
        Get-ChildItem -LiteralPath $modulePath -Recurse -Filter '*.jar' |
            Where-Object { $_.Name -notmatch '-sources|-javadoc' } | Select-Object -ExpandProperty FullName
    }
}
$desktopClasses = Join-Path $repository 'FloNovel-desktop/build/classes/kotlin/main'
if (-not (Test-Path (Join-Path $desktopClasses 'com/moonkata/flonovel/desktop/reader/ChapterJumpNavigator.class'))) {
    throw 'Build the existing Desktop classes before running the oracle.'
}
$output = Join-Path $project '.work/results/kotlin-oracle-classes'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$compilerClasspath = @($jars) -join [IO.Path]::PathSeparator
$applicationJars = @($jars | Where-Object { [IO.Path]::GetFileName($_) -match '^(kotlin-stdlib-2\.0\.21|json-20240303|juniversalchardet-2\.5\.0|annotations-[0-9]).*\.jar$' })
$classpath = (@($desktopClasses) + $applicationJars) -join [IO.Path]::PathSeparator
& java -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $classpath -d $output (Join-Path $PSScriptRoot $SourceName)
if ($LASTEXITCODE -ne 0) { throw 'Kotlin oracle compilation failed.' }
& java -cp "$output$([IO.Path]::PathSeparator)$classpath" $EntryPoint $project
if ($LASTEXITCODE -ne 0) { throw 'Kotlin oracle execution failed.' }
