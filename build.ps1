# SS2A-Monitor Build Script
$env:JAVA_HOME = "D:\HtmlHelper\build-env\jdk17\jdk-17.0.20+8"
$env:GRADLE_USER_HOME = "D:\HtmlHelper\build-env\gradle"
$env:ANDROID_HOME = "D:\HtmlHelper\build-env\android-sdk"

Write-Host ">>> Building SS2A-Monitor Debug APK with D:\HtmlHelper toolchain..." -ForegroundColor Cyan
& ".\gradlew.bat" :app:assembleDebug --offline

if ($LASTEXITCODE -eq 0) {
    Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "SS2A-Monitor-v1.9.6.apk" -Force
    Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "D:\HtmlHelper\apk\SS2A-Monitor-v1.9.6.apk" -Force
    Write-Host ">>> Build Success! APK copied to SS2A-Monitor-v1.9.6.apk and D:\HtmlHelper\apk\" -ForegroundColor Green
} else {
    Write-Host ">>> Build Failed. Please check build logs." -ForegroundColor Red
}
