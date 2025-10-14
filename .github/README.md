# GitHub Actions 自動化發佈說明

本目錄包含 GitHub Actions workflows，用於自動編譯、簽署並發佈 Release APK。

## 📁 檔案結構

```
.github/
├── workflows/
│   └── release.yml          # 自動編譯和發佈 workflow
├── SETUP_SECRETS.md         # GitHub Secrets 設定指南（重要！）
└── README.md                # 本檔案
```

## 🚀 快速開始

### 第一次使用

1. **建立 Keystore**（如果還沒有）
   ```bash
   keytool -genkey -v \
     -keystore release.keystore \
     -alias unified-remote-evo \
     -keyalg RSA \
     -keysize 2048 \
     -validity 10000
   ```

2. **設定 GitHub Secrets**

   詳細步驟請參閱 [SETUP_SECRETS.md](./SETUP_SECRETS.md)

   需要設定 4 個 Secrets：
   - `KEYSTORE_BASE64` - Keystore 的 Base64 編碼
   - `KEYSTORE_PASSWORD` - Keystore 密碼
   - `KEY_ALIAS` - 金鑰別名
   - `KEY_PASSWORD` - 金鑰密碼

3. **推送版本 Tag 觸發發佈**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

### 發佈新版本

每次要發佈新版本時：

```bash
# 1. 確認程式碼已提交
git add .
git commit -m "準備發佈 v1.0.0"
git push

# 2. 建立並推送 tag
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions 會自動：
- ✅ 編譯 Release APK
- ✅ 使用 Keystore 簽署
- ✅ 縮減 APK 大小（R8 + ProGuard）
- ✅ 建立 GitHub Release
- ✅ 上傳已簽署的 APK

## 🔧 Workflow 功能

### 自動觸發條件

1. **推送版本 Tag** (`v*`)
   - 範例：`v1.0.0`, `v2.1.5-beta`
   - 會自動建立 GitHub Release

2. **手動觸發**
   - 前往 GitHub Actions 頁面
   - 選擇「Build and Release APK」
   - 點擊「Run workflow」

### 產生的檔案

**APK 檔案命名**：
- Tag 發佈：`unified-remote-evo-v1.0.0.apk`
- 手動編譯：`unified-remote-evo-dev-20251014-143025.apk`

**下載位置**：
- GitHub Release 頁面（Tag 發佈時）
- GitHub Actions Artifacts（所有編譯）

## 📊 APK 資訊

| 項目 | 數值 |
|------|------|
| **最小 Android 版本** | Android 5.0 (API 21) |
| **目標 Android 版本** | Android 14 (API 34) |
| **APK 大小** | ~4.2 MB (Release) |
| **簽署方式** | APK Signature Scheme v1+v2+v3 |
| **最佳化** | R8 + ProGuard |

## 🔒 安全性

### 已實作的安全措施

✅ **Keystore 保護**
- Keystore 使用 Base64 編碼存放在 GitHub Secrets
- 編譯後自動清理（不會殘留在 runner）
- `.gitignore` 防止誤上傳

✅ **密碼保護**
- 所有密碼存放在 GitHub Secrets（加密）
- 編譯日誌中不會顯示密碼
- 使用完畢立即清理

✅ **存取控制**
- 只有有權限的人員可以查看/修改 Secrets
- GitHub Token 使用最小權限原則

### 注意事項

⚠️ **絕對不要**：
- ❌ 將 Keystore 檔案提交到 Git
- ❌ 將密碼寫在程式碼或設定檔中
- ❌ 在公開場合分享 Keystore 或密碼
- ❌ 使用弱密碼

✅ **應該做的**：
- ✅ 妥善保管 Keystore 備份
- ✅ 使用強密碼
- ✅ 定期檢查 GitHub Secrets 設定
- ✅ 限制誰可以執行 Actions

## 🛠️ 自訂設定

### 修改版本號

編輯 `app/build.gradle.kts`：

```kotlin
defaultConfig {
    versionCode = 2      // 遞增版本號
    versionName = "1.1"  // 更新版本名稱
}
```

### 修改簽署設定

如果需要更換 Keystore：

1. 更新 GitHub Secrets（4 個）
2. 推送變更
3. 重新執行 workflow

### 修改 Workflow

編輯 `workflows/release.yml` 來自訂：
- 觸發條件
- 編譯步驟
- APK 命名規則
- Release 設定

## 📝 版本標記規範

建議使用語意化版本號（Semantic Versioning）：

```
v主版本.次版本.修訂版本[-預發佈]

範例：
v1.0.0         # 正式版
v1.1.0         # 新增功能
v1.1.1         # 修正 bug
v2.0.0-beta    # 測試版
v2.0.0-rc1     # Release Candidate
```

## 🐛 疑難排解

### 編譯失敗

1. **檢查 Secrets 設定**
   - 前往 Settings → Secrets → Actions
   - 確認 4 個 Secrets 都已設定

2. **檢查 Keystore**
   - 確認 Base64 編碼無誤
   - 確認密碼正確
   - 確認別名正確

3. **查看編譯日誌**
   - 前往 Actions → 失敗的 workflow
   - 展開「Build Release APK」步驟
   - 查看錯誤訊息

### 簽署失敗

常見錯誤：
- `Invalid keystore format` → Base64 編碼錯誤
- `Wrong password` → 密碼錯誤
- `Alias not found` → 別名錯誤

解決方法：參閱 [SETUP_SECRETS.md](./SETUP_SECRETS.md)

## 📚 相關資源

- [GitHub Actions 文件](https://docs.github.com/en/actions)
- [Android 簽署文件](https://developer.android.com/studio/publish/app-signing)
- [ProGuard 文件](https://www.guardsquare.com/manual/home)

---

**維護者**：Jason
**最後更新**：2025-10-14
