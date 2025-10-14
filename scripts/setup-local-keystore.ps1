# PowerShell 腳本：設定本機 keystore
# 用法：.\scripts\setup-local-keystore.ps1

Write-Host "🔑 設定本機 Release 建置環境" -ForegroundColor Green
Write-Host ""

# 檢查是否已有 keystore
if (Test-Path "app\release.keystore") {
    Write-Host "⚠️  發現現有的 keystore 檔案" -ForegroundColor Yellow
    $overwrite = Read-Host "是否覆蓋？(y/n)"
    if ($overwrite -ne 'y' -and $overwrite -ne 'Y') {
        Write-Host "取消設定" -ForegroundColor Red
        exit 0
    }
}

Write-Host "請從 GitHub Actions 日誌或 Secrets 中取得以下資訊：" -ForegroundColor Cyan
Write-Host ""

# 輸入 Base64
Write-Host "1. KEYSTORE_BASE64（Base64 字串，可能很長）：" -ForegroundColor Yellow
$base64 = Read-Host "請貼上"

# 輸入密碼
Write-Host ""
Write-Host "2. KEYSTORE_PASSWORD：" -ForegroundColor Yellow
$storePass = Read-Host "請輸入"

Write-Host ""
Write-Host "3. KEY_ALIAS：" -ForegroundColor Yellow
$alias = Read-Host "請輸入"

Write-Host ""
Write-Host "4. KEY_PASSWORD：" -ForegroundColor Yellow
$keyPass = Read-Host "請輸入"

Write-Host ""
Write-Host "🔄 正在設定..." -ForegroundColor Green

try {
    # 解碼 Base64 並寫入 keystore 檔案
    $bytes = [System.Convert]::FromBase64String($base64)
    [System.IO.File]::WriteAllBytes("app\release.keystore", $bytes)

    Write-Host "✅ 已建立 app\release.keystore" -ForegroundColor Green

    # 建立 keystore.properties
    $properties = @"
storeFile=release.keystore
storePassword=$storePass
keyAlias=$alias
keyPassword=$keyPass
"@

    $properties | Out-File -Encoding ASCII -FilePath "keystore.properties"

    Write-Host "✅ 已建立 keystore.properties" -ForegroundColor Green

    # 驗證 keystore
    Write-Host ""
    Write-Host "🔍 驗證 keystore..." -ForegroundColor Cyan

    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    $keytool = "$env:JAVA_HOME\bin\keytool.exe"

    if (-not (Test-Path $keytool)) {
        Write-Host "⚠️  找不到 keytool，請手動驗證" -ForegroundColor Yellow
    } else {
        & $keytool -list -keystore "app\release.keystore" -storepass $storePass -alias $alias

        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "✅ Keystore 驗證成功！" -ForegroundColor Green
        } else {
            Write-Host ""
            Write-Host "❌ Keystore 驗證失敗，請檢查輸入的資訊" -ForegroundColor Red
            exit 1
        }
    }

    Write-Host ""
    Write-Host "🎉 設定完成！" -ForegroundColor Green
    Write-Host ""
    Write-Host "現在可以執行：" -ForegroundColor Cyan
    Write-Host "  .\gradlew.bat assembleRelease" -ForegroundColor White
    Write-Host ""
    Write-Host "APK 位置：" -ForegroundColor Cyan
    Write-Host "  app\build\outputs\apk\release\app-release.apk" -ForegroundColor White

} catch {
    Write-Host ""
    Write-Host "❌ 設定失敗：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
