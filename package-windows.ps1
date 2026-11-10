param([string]$Launch4jHome = $env:LAUNCH4J_HOME)

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not $Launch4jHome) {
        $Launch4jHome = Join-Path $PSScriptRoot 'class/tools/launch4j-3.50/launch4j'
    }
    $launch4jJar = Join-Path $Launch4jHome 'launch4j.jar'
    if (-not (Test-Path -LiteralPath $launch4jJar)) {
        throw 'Extract Launch4j 3.50 for Windows and pass its directory with -Launch4jHome.'
    }
    & (Join-Path $PSScriptRoot 'build.ps1')
    New-Item -ItemType Directory -Force release/windows | Out-Null
    $java = 'java'
    if ($env:JAVA_HOME) { $java = Join-Path $env:JAVA_HOME 'bin/java.exe' }
    $toolClasspath = $launch4jJar + ';' + (Join-Path $Launch4jHome 'lib/*')
    & $java -cp $toolClasspath net.sf.launch4j.Main build/gophie-launch4j-config.xml
    if ($LASTEXITCODE -ne 0) { throw 'Windows EXE packaging failed.' }
    Write-Output 'Built release/windows/Gophie.exe (requires installed Java 8 or newer).'
} finally {
    Pop-Location
}
