param([switch]$Test, [switch]$LiveTest)

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $javac = 'javac'
    $jar = 'jar'
    $java = 'java'
    if ($env:JAVA_HOME) {
        $javac = Join-Path $env:JAVA_HOME 'bin/javac.exe'
        $jar = Join-Path $env:JAVA_HOME 'bin/jar.exe'
        $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    }
    # JDK 8 writes its version to stderr; Windows PowerShell 5 treats that
    # as an error record, so capture it without stopping the build.
    $ErrorActionPreference = 'Continue'
    $version = (& $javac -version 2>&1 | Out-String)
    $versionExitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($versionExitCode -ne 0 -or $version -notmatch 'javac ') {
        throw 'Cannot run javac. Install a JDK and set JAVA_HOME or PATH.'
    }
    $target = @('--release', '8')
    if ($version -match 'javac 1\.8\.') { $target = @('-source', '8', '-target', '8') }
    New-Item -ItemType Directory -Force class, build | Out-Null
    & $javac -g -encoding UTF-8 @target -d class -sourcepath src src/org/gophie/Gophie.java
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    & $jar '-J-Djava.io.tmpdir=build' cfe build/Gophie.jar org.gophie.Gophie -C class org -C . res
    if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
    if ($Test -or $LiveTest) {
        New-Item -ItemType Directory -Force class/test | Out-Null
        & $javac -encoding UTF-8 @target -cp class -d class/test test/org/gophie/net/GopherEncodingTest.java test/org/gophie/ui/HistoryMouseListenerTest.java
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
        $testArgs = @()
        if ($LiveTest) { $testArgs = @('--live') }
        & $java '-Djava.awt.headless=true' -cp 'class;class/test' org.gophie.net.GopherEncodingTest @testArgs
        if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
        & $java '-Djava.awt.headless=true' -cp 'class;class/test;.' org.gophie.ui.HistoryMouseListenerTest
        if ($LASTEXITCODE -ne 0) { throw 'Mouse history tests failed.' }
    }
    Write-Output 'Built build/Gophie.jar (Java 8 compatible).'
} finally {
    Pop-Location
}
