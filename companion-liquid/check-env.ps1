Write-Host "--- Java Version ---"
java -version

Write-Host "`n--- JAVA_HOME ---"
Write-Host $env:JAVA_HOME

Write-Host "`n--- Gradle Version ---"
.\gradlew.bat --version

$javaVersion = java -version 2>&1 | Select-Object -First 1
if ($javaVersion -notmatch 'version "21') {
    Write-Host "`nWARNING: Java version is not 21. Please switch to JDK 21 LTS." -ForegroundColor Yellow
} else {
    Write-Host "`nJava version 21 detected. Environment looks good." -ForegroundColor Green
}
