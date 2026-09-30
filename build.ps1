param(
    [switch]$Test,
    [switch]$Run,
    [switch]$UiSmoke
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Find-JavaTool([string]$Name) {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\$Name.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $tool = Get-Command $Name -ErrorAction SilentlyContinue
    if ($tool) { return $tool.Source }
    throw "Cannot find $Name. Install a JDK (17 or later) and set JAVA_HOME or add its bin directory to PATH."
}

$javac = Find-JavaTool 'javac'
$java = Find-JavaTool 'java'
$output = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$sources = Get-ChildItem -Path 'src', 'test' -Filter '*.java' -Recurse |
    ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
$sourceList = Join-Path $output 'sources.txt'
[System.IO.File]::WriteAllLines($sourceList, $sources, [System.Text.UTF8Encoding]::new($false))

& $javac --release 17 -encoding UTF-8 -Xlint:all -d $output "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw "Compilation failed (exit $LASTEXITCODE)." }
Write-Host 'Compiled LukeFish.'

if ($Test) {
    & $java -ea '-Djava.awt.headless=true' -cp $output chess.ChessTests
    if ($LASTEXITCODE -ne 0) { throw "Tests failed (exit $LASTEXITCODE)." }
}
if ($UiSmoke) {
    & $java -ea -cp $output chess.UiSmokeTest
    if ($LASTEXITCODE -ne 0) { throw "UI smoke check failed (exit $LASTEXITCODE)." }
}
if ($Run) {
    & $java -ea -cp $output chess.Main
    if ($LASTEXITCODE -ne 0) { throw "LukeFish exited with code $LASTEXITCODE." }
}
