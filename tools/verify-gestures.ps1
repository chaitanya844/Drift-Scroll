$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    javac -d .tools/test-classes app/src/main/java/com/gladbox/driftscroll/ScrollPlan.java tests/ScrollPlanTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Gesture test compilation failed' }
    java -cp .tools/test-classes ScrollPlanTest
    if ($LASTEXITCODE -ne 0) { throw 'Gesture tests failed' }
} finally { Pop-Location }
