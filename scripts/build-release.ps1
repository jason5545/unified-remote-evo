# PowerShell 腳本：建置 Release APK
# 用法：.\scripts\build-release.ps1

Write-Host "🚀 建置 Release APK" -ForegroundColor Green
Write-Host ""

# 檢查 keystore.properties
if (-not (Test-Path "keystore.properties")) {
    Write-Host "❌ 找不到 keystore.properties" -ForegroundColor Red
    Write-Host ""
    Write-Host "請先執行：" -ForegroundColor Yellow
    Write-Host "  .\scripts\setup-local-keystore.ps1" -ForegroundColor White
    Write-Host ""
    Write-Host "或手動建立 keystore.properties 檔案" -ForegroundColor Yellow
    exit 1
}

# 檢查 keystore 檔案
if (-not (Test-Path "app\release.keystore")) {
    Write-Host "❌ 找不到 app\release.keystore" -ForegroundColor Red
    Write-Host ""
    Write-Host "請先執行：" -ForegroundColor Yellow
    Write-Host "  .\scripts\setup-local-keystore.ps1" -ForegroundColor White
    exit 1
}

Write-Host "✅ 找到 keystore 設定檔" -ForegroundColor Green
Write-Host ""

# 清理舊的建置
Write-Host "🧹 清理舊的建置..." -ForegroundColor Cyan
& .\gradlew.bat clean

# 建置 Release APK
Write-Host ""
Write-Host "🔨 建置 Release APK（這可能需要幾分鐘）..." -ForegroundColor Cyan
& .\gradlew.bat assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "🎉 建置成功！" -ForegroundColor Green
    Write-Host ""
    Write-Host "APK 位置：" -ForegroundColor Cyan
    Write-Host "  app\build\outputs\apk\release\app-release.apk" -ForegroundColor White

    # 顯示 APK 資訊
    $apkPath = "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $apkPath) {
        $apkSize = (Get-Item $apkPath).Length / 1MB
        Write-Host ""
        Write-Host "APK 大小：" -ForegroundColor Cyan
        Write-Host "  $([math]::Round($apkSize, 2)) MB" -ForegroundColor White
    }
} else {
    Write-Host ""
    Write-Host "❌ 建置失敗" -ForegroundColor Red
    Write-Host ""
    Write-Host "請檢查錯誤訊息" -ForegroundColor Yellow
    exit 1
}
