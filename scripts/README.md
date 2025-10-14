# 本機建置 Release APK 指南

本目錄包含本機建置 Release APK 的腳本。

## 📋 前置需求

- ✅ Android Studio 或 JDK 17+
- ✅ Android SDK
- ✅ (可選) 從 GitHub Actions 取得的 keystore 資訊

## 🚀 快速開始

### 方式 A：完整自動流程（推薦）⭐

**完全模擬 GitHub Actions，自動產生 keystore 並建置**

**Windows (PowerShell)：**
```powershell
.\scripts\build-release-full.ps1
```

**Linux/Mac (Bash)：**
```bash
./scripts/build-release-full.sh
```

**腳本會自動：**
1. ✅ 檢查環境 (JAVA_HOME, keytool)
2. ✅ 自動產生 keystore 和隨機密碼（如果不存在）
3. ✅ 驗證 keystore 有效性（兩階段：KEYSTORE_PASSWORD + KEY_PASSWORD）
4. ✅ 建立 keystore.properties
5. ✅ 編譯 Release APK
6. ✅ 重新命名 APK（加上時間戳）
7. ✅ 顯示 APK 資訊
8. ✅ 詢問是否清理 keystore（建議保留供下次使用）

**輸出：**
- `unified-remote-evo-dev-20251014-143025.apk`
- 完整的 keystore 資訊（供 GitHub Secrets 使用）

---

### 方式 B：使用現有 Keystore

### 步驟 1：設定 Keystore

**Windows (PowerShell)：**
```powershell
.\scripts\setup-local-keystore.ps1
```

**Linux/Mac (Bash)：**
```bash
./scripts/setup-local-keystore.sh
```

腳本會要求您輸入 4 個值（從 GitHub Actions 日誌或 Secrets 取得）：
1. `KEYSTORE_BASE64` - Base64 編碼的 keystore（**包括結尾的 = 符號**）
2. `KEYSTORE_PASSWORD` - Keystore 密碼
3. `KEY_ALIAS` - 金鑰別名
4. `KEY_PASSWORD` - 金鑰密碼

腳本會自動：
- 解碼 Base64 並建立 `app/release.keystore`
- 建立 `keystore.properties` 設定檔
- 驗證 keystore 有效性

### 步驟 2：建置 Release APK

**手動建置：**
```bash
# Windows
.\gradlew.bat assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

**使用腳本（僅 Windows）：**
```powershell
.\scripts\build-release.ps1
```

腳本會自動：
- 檢查 keystore 設定檔
- 清理舊的建置
- 建置並簽署 Release APK
- 顯示 APK 資訊

### 步驟 3：取得 APK

建置完成後，APK 位於：
```
app/build/outputs/apk/release/app-release.apk
```

## 📝 手動設定（進階）

如果不想使用腳本，可以手動設定：

### 1. 建立 `keystore.properties`（根目錄）

```properties
storeFile=release.keystore
storePassword=你的KEYSTORE_PASSWORD
keyAlias=你的KEY_ALIAS
keyPassword=你的KEY_PASSWORD
```

**注意**：`storeFile` 使用相對於 `app/` 目錄的路徑。

### 2. 建立 `app/release.keystore`

**Windows (PowerShell)：**
```powershell
$base64 = "你的KEYSTORE_BASE64"
$bytes = [System.Convert]::FromBase64String($base64)
[System.IO.File]::WriteAllBytes("app\release.keystore", $bytes)
```

**Linux/Mac (Bash)：**
```bash
echo "你的KEYSTORE_BASE64" | base64 -d > app/release.keystore
```

### 3. 驗證 Keystore

```bash
keytool -list -keystore app/release.keystore \
    -storepass "你的KEYSTORE_PASSWORD" \
    -alias "你的KEY_ALIAS"
```

### 4. 建置

```bash
./gradlew assembleRelease
```

## 🔒 安全注意事項

⚠️ **重要：不要提交以下檔案到 Git！**

`.gitignore` 已經包含這些規則：
```gitignore
*.keystore
keystore.properties
keystore.base64
release.keystore
```

確保這些檔案保持在 `.gitignore` 中，避免洩漏簽署金鑰。

## 🐛 常見問題

### Q: 執行腳本時提示「無法載入檔案」（PowerShell）

**A:** 需要設定執行原則：
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

### Q: Base64 解碼失敗

**A:** 確認：
- 複製了完整的 Base64 字串（包括開頭和結尾）
- 包括結尾的 `=` 或 `==` 符號
- 沒有換行符或空格

### Q: Keystore 驗證失敗

**A:** 檢查：
- `KEYSTORE_PASSWORD` 是否正確
- `KEY_ALIAS` 是否正確
- Base64 是否完整解碼

### Q: 簽署失敗「Given final block not properly padded」

**A:** 這表示 `KEY_PASSWORD` 不正確，請檢查：
- 從 GitHub Secrets 複製正確的 `KEY_PASSWORD`
- 確認沒有多餘的空格或換行

### Q: 找不到 keytool

**A:** 設定 JAVA_HOME：
```powershell
# Windows (Android Studio JBR)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# 或使用已安裝的 JDK
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
```

## 📊 建置資訊

- **最小 Android 版本**：Android 5.0 (API 21)
- **目標 Android 版本**：Android 14 (API 34)
- **預期 APK 大小**：約 4.2 MB（Release 版本，已啟用 R8 + ProGuard）
- **簽署方式**：APK Signature Scheme v1+v2+v3

## 🔗 相關連結

- [GitHub Actions Workflow](.github/workflows/release.yml)
- [Build Configuration](../app/build.gradle.kts)
- [ProGuard Rules](../app/proguard-rules.pro)
