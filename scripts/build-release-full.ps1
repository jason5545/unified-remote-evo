# PowerShell 腳本：完整模擬 GitHub Actions Release 流程
# 用法：.\scripts\build-release-full.ps1

Write-Host "🚀 完整 Release 建置流程（模擬 GitHub Actions）" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""

# 步驟 1: 檢查環境
Write-Host "📋 步驟 1: 檢查環境" -ForegroundColor Cyan
Write-Host ""

# 檢查 Java/keytool
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    # 嘗試使用 Android Studio JBR
    $javaHome = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $javaHome) {
        Write-Host "✅ 找到 Android Studio JBR" -ForegroundColor Green
        $env:JAVA_HOME = $javaHome
    } else {
        Write-Host "❌ 找不到 JAVA_HOME，請設定 JAVA_HOME 環境變數" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✅ JAVA_HOME: $javaHome" -ForegroundColor Green
}

$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    Write-Host "❌ 找不到 keytool.exe" -ForegroundColor Red
    exit 1
}

Write-Host "✅ keytool: $keytool" -ForegroundColor Green
Write-Host ""

# 步驟 2: 檢查並產生簽署金鑰
Write-Host "📋 步驟 2: 檢查並產生簽署金鑰" -ForegroundColor Cyan
Write-Host ""

$keystoreExists = Test-Path "app\release.keystore"
$propsExists = Test-Path "keystore.properties"

if ($keystoreExists -and $propsExists) {
    Write-Host "✅ 找到現有的簽署金鑰" -ForegroundColor Green
    Write-Host ""

    # 讀取現有設定
    $props = Get-Content "keystore.properties" | ConvertFrom-StringData
    $storePass = $props.storePassword
    $keyPass = $props.keyPassword
    $alias = $props.keyAlias

    Write-Host "使用現有設定：" -ForegroundColor Yellow
    Write-Host "  KEY_ALIAS: $alias" -ForegroundColor White
    Write-Host ""

} else {
    Write-Host "⚠️  未找到簽署金鑰，自動產生新的..." -ForegroundColor Yellow
    Write-Host ""

    # 產生隨機密碼（16 字元）
    $storePass = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 16 | ForEach-Object {[char]$_})
    $keyPass = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 16 | ForEach-Object {[char]$_})
    $alias = "unified-remote-evo"

    Write-Host "產生的資訊（請妥善保管）：" -ForegroundColor Green
    Write-Host "==================================" -ForegroundColor Green
    Write-Host "KEY_ALIAS=$alias" -ForegroundColor White
    Write-Host "KEYSTORE_PASSWORD=$storePass" -ForegroundColor White
    Write-Host "KEY_PASSWORD=$keyPass" -ForegroundColor White
    Write-Host "==================================" -ForegroundColor Green
    Write-Host ""

    # 產生 Keystore
    Write-Host "🔑 產生 Keystore..." -ForegroundColor Cyan

    $dname = "CN=Unified Remote Evo, OU=Development, O=Personal, L=Taipei, ST=Taiwan, C=TW"

    & $keytool -genkey -v `
        -keystore "app\release.keystore" `
        -alias $alias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -storepass $storePass `
        -keypass $keyPass `
        -dname $dname

    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Keystore 產生失敗" -ForegroundColor Red
        exit 1
    }

    Write-Host "✅ Keystore 產生成功" -ForegroundColor Green
    Write-Host ""

    # 轉換為 Base64（供參考）
    $bytes = [System.IO.File]::ReadAllBytes("app\release.keystore")
    $base64 = [System.Convert]::ToBase64String($bytes)

    Write-Host "KEYSTORE_BASE64（供 GitHub Secrets 使用）：" -ForegroundColor Yellow
    Write-Host "==================================" -ForegroundColor Yellow
    Write-Host $base64 -ForegroundColor White
    Write-Host "==================================" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "⚠️  請將以上 4 個值保存到安全的地方！" -ForegroundColor Red
    Write-Host ""

    # 儲存到環境變數（供後續步驟使用）
    $env:KEYSTORE_PASSWORD = $storePass
    $env:KEY_ALIAS = $alias
    $env:KEY_PASSWORD = $keyPass
}

# 步驟 3: 驗證 Keystore 有效性
Write-Host "📋 步驟 3: 驗證 Keystore 有效性" -ForegroundColor Cyan
Write-Host ""

Write-Host "🔍 驗證 Keystore..." -ForegroundColor Yellow

# 測試 KEYSTORE_PASSWORD
& $keytool -list -keystore "app\release.keystore" `
    -storepass $storePass `
    -alias $alias > $null 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Keystore 驗證失敗！(KEYSTORE_PASSWORD 或 KEY_ALIAS 錯誤)" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Step 1: KEYSTORE_PASSWORD 驗證成功" -ForegroundColor Green

# 測試 KEY_PASSWORD
Write-Host "🔍 驗證 KEY_PASSWORD..." -ForegroundColor Yellow

& $keytool -list -keystore "app\release.keystore" `
    -storepass $storePass `
    -alias $alias `
    -keypass $keyPass > $null 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ KEY_PASSWORD 驗證失敗！" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Step 2: KEY_PASSWORD 驗證成功" -ForegroundColor Green
Write-Host "✅ Keystore 完整驗證通過！別名: $alias" -ForegroundColor Green
Write-Host ""

# 顯示憑證資訊
& $keytool -list -keystore "app\release.keystore" `
    -storepass $storePass `
    -alias $alias | Select-String -Pattern "Creation date|建立日期"

Write-Host ""

# 步驟 4: 建立 keystore.properties
Write-Host "📋 步驟 4: 建立 keystore.properties" -ForegroundColor Cyan
Write-Host ""

$properties = @"
storeFile=release.keystore
storePassword=$storePass
keyAlias=$alias
keyPassword=$keyPass
"@

$properties | Out-File -Encoding ASCII -FilePath "keystore.properties"

Write-Host "✅ 已建立 keystore.properties" -ForegroundColor Green
Write-Host ""

# 步驟 5: 編譯 Release APK
Write-Host "📋 步驟 5: 編譯 Release APK" -ForegroundColor Cyan
Write-Host ""

Write-Host "🔨 執行 ./gradlew.bat assembleRelease（這可能需要幾分鐘）..." -ForegroundColor Yellow
Write-Host ""

& .\gradlew.bat assembleRelease

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ 編譯失敗" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ 編譯成功" -ForegroundColor Green
Write-Host ""

# 步驟 6: 取得版本號
Write-Host "📋 步驟 6: 取得版本號" -ForegroundColor Cyan
Write-Host ""

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$version = "dev-$timestamp"

Write-Host "版本號: $version" -ForegroundColor White
Write-Host ""

# 步驟 7: 重新命名 APK
Write-Host "📋 步驟 7: 重新命名 APK" -ForegroundColor Cyan
Write-Host ""

$sourcePath = "app\build\outputs\apk\release\app-release.apk"
$destPath = "unified-remote-evo-$version.apk"

if (Test-Path $sourcePath) {
    Copy-Item $sourcePath $destPath
    Write-Host "✅ APK 已複製到: $destPath" -ForegroundColor Green
} else {
    Write-Host "⚠️  找不到原始 APK: $sourcePath" -ForegroundColor Yellow
}

Write-Host ""

# 步驟 8: 顯示 APK 資訊
Write-Host "📋 步驟 8: APK 資訊" -ForegroundColor Cyan
Write-Host ""

if (Test-Path $destPath) {
    $apkSize = (Get-Item $destPath).Length / 1MB
    Write-Host "APK 位置：" -ForegroundColor Green
    Write-Host "  $(Resolve-Path $destPath)" -ForegroundColor White
    Write-Host ""
    Write-Host "APK 大小：" -ForegroundColor Green
    Write-Host "  $([math]::Round($apkSize, 2)) MB" -ForegroundColor White
} else {
    Write-Host "⚠️  找不到 APK 檔案" -ForegroundColor Yellow
}

Write-Host ""

# 步驟 9: 清理（可選）
Write-Host "📋 步驟 9: 清理" -ForegroundColor Cyan
Write-Host ""

$cleanup = Read-Host "是否清理簽署金鑰？(y/n，建議輸入 n 保留以供下次使用)"

if ($cleanup -eq 'y' -or $cleanup -eq 'Y') {
    Remove-Item "app\release.keystore" -ErrorAction SilentlyContinue
    Remove-Item "keystore.properties" -ErrorAction SilentlyContinue
    Write-Host "✅ 已清理簽署金鑰" -ForegroundColor Green
} else {
    Write-Host "✅ 保留簽署金鑰供下次使用" -ForegroundColor Green
}

Write-Host ""
Write-Host "🎉 完整建置流程完成！" -ForegroundColor Green
Write-Host ""
