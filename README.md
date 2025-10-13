# Unified Remote Evo - 進化版遙控 APP

> **♿ 輔助技術專案 | Assistive Technology Project**
>
> 專為**身心障礙使用者**設計的無障礙遙控 APP。
> 針對**單指操作**優化，提供大按鈕、簡化介面、多模式連線支援。

**雙系統三模式遙控解決方案**，專為 **輔助功能需求** 與 **長時間穩定連線** 設計。

## 📢 重要聲明

### ⚠️ 安全警告

**Unified Remote Server 存在已知的 RCE（遠端程式碼執行）漏洞！**

- 🔴 **不要在公開網路上暴露 Unified Remote Server**
- 🔴 **不要使用預設密碼或弱密碼**
- ✅ **建議僅在可信任的私有網路中使用**（如 Tailscale VPN）
- ✅ **建議設定強密碼並定期更換**
- ⚠️ **官方已停止維護，安全漏洞不會修補**

**風險說明**：
- 攻擊者可能透過漏洞在您的 PC 上執行任意程式碼
- 建議優先使用 **BLE 模式**（EmulStick），無需網路連線，更安全
- 如必須使用 TCP 模式，請**僅在 Tailscale 等加密 VPN 環境中使用**

### 關於本專案

**專案定位**：
- ✅ **輔助技術工具**：專為身心障礙使用者設計的無障礙遙控客戶端
- ✅ **相容性實作**：支援 Unified Remote Server 與 EmulStick 接收器的相容協定
- ✅ **延續產品生命**：原廠產品已停更，本專案提供無障礙優化的現代化方案
- ✅ **完全重寫**：使用 Kotlin + Jetpack Compose，不包含任何原版程式碼

**與官方的關係**：
- ❌ **非官方授權**：本專案並非 Unified Remote 或 EmulStick 官方產品
- ❌ **非替代品**：不宣稱「官方」或「替代」，僅為無障礙相容性實作
- ✅ **輔助技術**：開發目的為改善身心障礙者的遙控操作體驗
- ✅ **個人使用**：主要供學習、研究與個人輔助使用，非營利專案

**為什麼開發此專案？**
1. **輔助功能需求**：專為**身心障礙使用者**設計（僅單指可操作）
2. **官方已停更**：Unified Remote Server 自 2022 年後未更新，缺乏無障礙優化
3. **無障礙設計**：大按鈕、單指操作、簡化介面、長時間穩定連線
4. **整合多種模式**：TCP、藍牙、BLE 三合一解決方案，適應不同使用情境

## 📡 三種連線模式

### 系統 1：Unified Remote（網路遙控器）
需要在 PC 安裝 Unified Remote Server。

> **⚠️ 安全提醒**：Unified Remote Server 存在 RCE 漏洞，**僅限在 Tailscale 等可信任的私有 VPN 中使用**！

**模式 1-A：TCP/IP 連線** ✅
- 透過 **TCP/IP** 連接到 Unified Remote Server
- ⚠️ **安全要求**：**僅在 Tailscale/VPN 等加密私有網路中使用**
- 🔐 設定強密碼，不要暴露在公開網路
- 已實作並測試

**模式 1-B：藍牙連線** ⚠️
- 透過 **傳統藍牙（RFCOMM）** 連接到 Unified Remote Server
- 適用於 **近距離無網路**環境
- 使用與 TCP 相同的封包協定
- 已實作，待測試

### 系統 2：EmulStick（接收器模式）✅
無需伺服器軟體，即插即用。

> **📦 硬體需求：需購買 EmulStick 接收器**
>
> 本專案僅提供軟體客戶端，**EmulStick 接收器需另行購買**。
>
> 官方網站：[https://www.emulstick.com/](https://www.emulstick.com/)
>
> 如果您覺得 EmulStick 產品有用，請支持原廠購買正版硬體。

**模式 2：BLE HID 連線**
- 透過 **藍牙 BLE** 連接到 EmulStick 硬體接收器
- 接收器模擬為標準 **HID 裝置**（鍵盤/滑鼠）
- 使用 HID over GATT 協定
- 已實作並測試

**🎮 XInput 模式**（2025-10 新增）
- 在 BLE 模式下切換為 **Xbox 360 控制器**
- 完整虛擬手把 UI（雙搖桿、按鈕、扳機、D-Pad）
- 支援遊戲操作（Steam Big Picture、XInput 遊戲、模擬器）
- 一鍵切換回鍵盤/滑鼠模式

## 📱 功能特色

### ✨ 核心功能

**滑鼠控制：**
- 觸控板移動（拖曳手勢）
- 左鍵、中鍵、右鍵
- 垂直/水平滾輪（獨立滾輪條）
- 雙擊
- 長按拖曳模式（按住左鍵）

**鍵盤控制：**
- 📝 **底部輸入面板**（整合設計）
  - 修飾鍵選擇器（Ctrl、Shift、Alt、Win）
  - 軟體鍵盤整合（即時文字輸入）
  - 組合鍵支援（Win+G、Ctrl+Shift+Esc 等）
  - 摺疊式虛擬按鍵：
    - 方向鍵（↑↓←→）
    - 功能鍵（Enter、Tab、Esc、Del、Space、Back）
    - F1-F12
    - 其他按鍵（Home、End、PgUp、PgDn、Insert）

- ⚡ **快捷鍵對話框**（常用快捷鍵快速入口）
  - 編輯：複製、貼上、剪下、復原、全選
  - 系統：儲存、尋找、關閉、Alt+Tab、Alt+F4

- 🎯 **BLE IME Direct 模式**（EmulStick 專用）
  - 支援中文輸入（CustomIn 報告）
  - UTF-8 安全切割（避免亂碼）
  - 即時發送模式

**除錯工具：**
- 即時連線日誌
- 統計資訊（總計/錯誤/警告/資訊）
- 日誌清除功能
- 日誌類型篩選（BLE 專用）

### 🎯 單指操作優化

- **右側按鈕配置**：常用功能放右側，方便右手操作
- **底部展開面板**：不遮擋觸控板，保持可用性
- **大按鈕設計**：滑鼠左右鍵 60dp 高度
- **清晰視覺**：滾輪條使用主題色，在黑色背景上清楚可見
- **摺疊式介面**：虛擬按鍵預設收合，減少滾動距離

### ⚡ Tailscale 優化（TCP 模式）

針對 overlay network 環境的連線參數優化：

| 參數 | 原版 | 優化版 | 改善 |
|------|------|--------|------|
| 心跳間隔 | 60秒 | **15秒** | 4倍 |
| 連線 Timeout | 5秒 | **3秒** | 1.67倍 |
| 重連延遲 | 1-5秒 | **0.5-2秒** | 2.5倍 |
| 緩衝區 | 4KB | **16KB** | 4倍 |
| 心跳超時偵測 | 無 | **5秒自動重連** | 新功能 |

### 🔧 BLE 功能優化（EmulStick 模式）

- ✅ **NumLock 自動啟用**（Alt 碼輸入支援）
- ✅ **LED 狀態追蹤**（NumLock/CapsLock/ScrollLock）
- ✅ **MouseV1/SingleKeyboard 支援**（Ver ≥1 裝置）
- ✅ **IME Direct 模式**（中英文混合輸入）
- ✅ **裝置歷史管理**（快速重連）
- ✅ **XInput 模式**（Xbox 360 控制器模擬）

### 🎮 XInput 遊戲手把模式（EmulStick 專用）

**功能特色：**
- **雙搖桿控制**：左搖桿/右搖桿（觸控拖曳，自動回中心）
- **按鈕面板**：A/B/X/Y（Xbox 配色）+ LB/RB 肩鈕
- **扳機控制**：LT/RT 垂直滑桿（0-100% 精確控制）
- **方向鍵**：D-Pad 八方向控制
- **系統按鈕**：Start/Back/L3/R3
- **橫向/直向**：自動偵測螢幕方向，動態調整佈局
- **單指優化**：大按鈕（48-70dp）、大搖桿（130-140dp）
- **一鍵切換**：返回按鈕快速切換回鍵盤/滑鼠模式

**適用場景：**
- Steam Big Picture 導覽與遊戲
- 原生 XInput 遊戲（大部分現代 PC 遊戲）
- 模擬器（PCSX2、Dolphin、RetroArch 等）
- Windows 遊戲列（Win+G）

## 🚀 快速開始

### 1. 準備環境

**開發環境：**
- Android Studio (最新版)
- JDK 11+
- Android SDK (最低 API 21 / Android 5.0)

**執行環境（TCP 模式）：**
- Android 裝置（平板或手機）
- Windows PC（安裝 Unified Remote Server）
- Tailscale（客戶端與伺服器端）

**執行環境（BLE 模式）：**
- Android 裝置（平板或手機）
- **EmulStick 藍牙接收器**（USB 插入 PC）
  - ⚠️ **需另行購買**：[https://www.emulstick.com/](https://www.emulstick.com/)
  - 本專案僅提供相容客戶端軟體，不提供硬體
- 無需安裝伺服器軟體

### 2. 設定 Tailscale（TCP 模式）

> **⚠️ 重要安全提醒**：
> - Unified Remote Server 存在 RCE 漏洞，**絕對不要在公開網路使用**
> - **必須使用 Tailscale/VPN 等私有加密網路**
> - 設定強密碼，定期更換

**伺服器端（Windows/Mac/Linux）：**
```bash
# 安裝並啟動 Tailscale
tailscale up

# 查看 Tailscale IP
tailscale ip
# 例如：100.101.102.103
```

**確保 Unified Remote Server 正在執行**
- 預設埠號：9512（TCP + UDP）
- 下載：https://www.unifiedremote.com/
- 🔐 **務必設定強密碼**

### 3. 編譯 APK

**Windows：**
```bash
./gradlew.bat assembleDebug
```

**Linux/Mac：**
```bash
./gradlew assembleDebug
```

**APK 輸出位置：**
```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. 安裝與使用

#### TCP 模式（Unified Remote）
1. 將 APK 傳輸到 Android 裝置
2. 安裝 APK（需允許安裝未知來源）
3. 開啟 App
4. 選擇「TCP 連線」
5. **伺服器設定：**
   - IP 位址：輸入伺服器 Tailscale IP（例如：100.101.102.103）
   - 埠號：9512（預設）
6. 點擊「連接」
7. 連線成功後自動切換到觸控板介面

#### BLE 模式（EmulStick）
1. 將 APK 傳輸到 Android 裝置
2. 安裝 APK（需允許安裝未知來源）
3. 將 EmulStick 接收器插入 PC USB 埠
4. 開啟 App
5. 選擇「BLE 連線」
6. 掃描並選擇 EmulStick 裝置
7. 等待身份驗證完成
8. 連線成功後自動切換到觸控板介面

## 🎮 使用說明

### 介面配置

**頂部功能列：**
- 📊 除錯日誌（左側）
- ❌ 斷線（左側）
- 🎮 遊戲手把（中間，僅 BLE 模式）- 切換 XInput 模式
- ⚡ 快捷鍵（右側）
- 📝 輸入面板（右側）
- ⚙️ 靈敏度設定（右上角，僅 BLE 模式）

**中央觸控板：**
- 全螢幕觸控區域
- 顯示「觸控板」或「拖曳中...」提示

**右側滾輪條：**
- 垂直滾動控制（淡藍色半透明）

**底部區域：**
- 水平滾輪條（左右滾動）
- 滑鼠按鍵：L（左鍵）、M（中鍵）、R（右鍵）

### 觸控板手勢

- **拖曳**：移動滑鼠游標
- **點擊**：左鍵點擊
- **雙擊**：雙擊左鍵
- **長按**：進入拖曳模式（按住左鍵）
  - 長按後會顯示「釋放拖曳」按鈕
  - 點擊按鈕釋放左鍵

### 📝 輸入面板（底部展開）

點擊頂部右側的 📝 按鈕打開輸入面板：

**1. 一般文字輸入：**
- 不選修飾鍵
- 直接使用軟體鍵盤輸入
- 文字即時發送到 PC
- **BLE 模式**：使用 IME Direct 模式（支援中文）

**2. 組合鍵輸入：**
- 選擇修飾鍵（Ctrl/Shift/Alt/Win，可多選）
- 使用軟體鍵盤輸入字元
- 點擊「發送: WIN + G」按鈕
- 支援複雜組合鍵（例如：Ctrl+Shift+Esc）

**3. 虛擬按鍵：**
- 展開「方向鍵」區塊 → 使用 ↑↓←→
- 展開「功能鍵」區塊 → Enter、Tab、Esc、Del、Space、Back
- 展開「F 鍵」區塊 → F1-F12
- 展開「其他按鍵」區塊 → Home、End、PgUp、PgDn、Insert

### ⚡ 快捷鍵對話框

點擊頂部右側的 ⚡ 按鈕打開快捷鍵對話框：

**編輯快捷鍵：**
- 複製（Ctrl+C）
- 剪下（Ctrl+X）
- 貼上（Ctrl+V）
- 復原（Ctrl+Z）
- 全選（Ctrl+A）

**系統快捷鍵：**
- 儲存（Ctrl+S）
- 尋找（Ctrl+F）
- 關閉（Ctrl+W）
- Alt+Tab（切換視窗）
- Alt+F4（關閉程式）

### 📊 除錯日誌

1. 點擊左上角「📊」圖示
2. 查看即時連線日誌
3. 統計資訊顯示於底部
4. 點擊「🗑️」清空日誌
5. **BLE 模式**：可篩選日誌類型（一般/HID/LED）

### 🎮 XInput 遊戲手把模式（BLE 專用）

#### 啟用方式
1. **連接到 EmulStick 接收器**（BLE 模式）
2. **點擊頂部「遊戲手把」開關**（Switch 切換為開啟）
3. **等待切換確認**（Toast 訊息：「已切換到 Xbox 360 控制器模式」）
4. **介面自動切換**為虛擬手把 UI

#### 虛擬手把操作

**搖桿控制：**
- **左搖桿**：拖曳控制移動方向與幅度
- **右搖桿**：拖曳控制視角方向與幅度
- **釋放自動回中心**：手指離開後搖桿回到中心位置

**按鈕操作：**
- **A 鈕**（綠色，下方）：確認/跳躍
- **B 鈕**（紅色，右方）：取消/返回
- **X 鈕**（藍色，左方）：重新載入/使用
- **Y 鈕**（黃色，上方）：切換武器/互動
- **LB/RB**（肩鈕）：上一個/下一個
- **Start/Back**：開始/選擇
- **L3/R3**：搖桿按下（衝刺/蹲下）

**扳機控制：**
- **LT/RT 滑桿**：垂直滑動（0-100%）
- 用於加速/煞車、瞄準/射擊

**方向鍵（D-Pad）：**
- **上下左右**：四方向控制
- 用於選單導覽、快速選擇

#### 切換回鍵盤/滑鼠模式
1. **點擊左上角「← 返回組合模式」按鈕**
2. **等待切換確認**（Toast 訊息：「已切換回鍵盤/滑鼠模式」）
3. **介面自動切換**回觸控板 UI

#### Windows 裝置確認
- 開啟「設定 → 裝置 → 遊戲控制器」
- 應該看到「Xbox 360 Controller for Windows」
- 點擊「內容」可測試按鈕/搖桿功能

## 📂 專案結構

```
unified-remote-evo/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/unifiedremote/evo/
│   │   │   ├── data/                # 資料結構
│   │   │   │   ├── Packet.kt        # 封包定義（TCP/藍牙）
│   │   │   │   ├── SavedDevice.kt   # 裝置記錄（三模式）
│   │   │   │   ├── DeviceHistoryManager.kt  # 歷史管理
│   │   │   │   ├── SensitivitySettings.kt   # 靈敏度設定
│   │   │   │   └── ThemeSettings.kt         # 主題設定
│   │   │   │
│   │   │   ├── network/             # 網路層
│   │   │   │   ├── tcp/             # TCP 模式
│   │   │   │   │   ├── ConnectionManager.kt  # TCP 連線管理
│   │   │   │   │   ├── BinaryReader.kt       # 二進制讀取
│   │   │   │   │   ├── BinaryWriter.kt       # 二進制寫入
│   │   │   │   │   └── PacketSerializer.kt   # 封包序列化
│   │   │   │   ├── bluetooth/       # 藍牙模式（RFCOMM）
│   │   │   │   │   └── BluetoothConnectionManager.kt  # 待測試
│   │   │   │   ├── ble/             # BLE 模式（EmulStick）
│   │   │   │   │   ├── BleManager.kt         # BLE 連線管理
│   │   │   │   │   ├── BleConnectionState.kt # 連線狀態
│   │   │   │   │   ├── HidReportBuilder.kt   # HID 報告建構器
│   │   │   │   │   ├── GattConstants.kt      # GATT UUID 定義
│   │   │   │   │   ├── AesCryptUtil.kt       # AES 加密工具
│   │   │   │   │   ├── KeyboardLedState.kt   # LED 狀態追蹤
│   │   │   │   │   ├── BleXInputController.kt # Xbox 360 控制器
│   │   │   │   │   └── ...                   # 其他 BLE 相關工具
│   │   │   │   ├── UnifiedConnectionManager.kt  # 統一介面
│   │   │   │   ├── RemoteController.kt       # 控制器介面
│   │   │   │   ├── ConnectionType.kt         # 連線類型
│   │   │   │   └── ConnectionLogger.kt       # 日誌系統
│   │   │   │
│   │   │   ├── controller/          # 控制層
│   │   │   │   ├── MouseController.kt        # Unified Remote 滑鼠
│   │   │   │   ├── KeyboardController.kt     # Unified Remote 鍵盤
│   │   │   │   ├── BleMouseController.kt     # EmulStick BLE 滑鼠
│   │   │   │   └── BleKeyboardController.kt  # EmulStick BLE 鍵盤
│   │   │   │
│   │   │   ├── ui/                  # UI 層
│   │   │   │   ├── theme/           # 主題
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── components/      # UI 元件
│   │   │   │   │   └── XInputControlPanel.kt  # Xbox 360 虛擬手把 UI
│   │   │   │   └── screens/         # 畫面
│   │   │   │       ├── MainScreen.kt          # 主畫面（三模式選擇）
│   │   │   │       ├── ServerConfigScreen.kt  # Unified Remote 設定
│   │   │   │       ├── BleDeviceListScreen.kt # EmulStick BLE 掃描
│   │   │   │       ├── TouchpadScreen.kt      # 觸控板（三模式共用）
│   │   │   │       ├── DebugScreen.kt         # 除錯日誌（三模式共用）
│   │   │   │       └── SensitivitySettingsScreen.kt # 靈敏度設定
│   │   │   │
│   │   │   ├── viewmodel/           # ViewModel
│   │   │   │   ├── BleViewModel.kt
│   │   │   │   └── BleUiState.kt
│   │   │   │
│   │   │   ├── agent/               # 背景任務
│   │   │   │   └── BleScanAgent.kt  # BLE 掃描代理
│   │   │   │
│   │   │   └── MainActivity.kt      # 主程式
│   │   │
│   │   ├── res/                     # 資源
│   │   └── AndroidManifest.xml
│   │
│   └── build.gradle.kts
│
├── apk_analysis/                    # 技術研究資料（協定分析）
│   ├── unified_decompiled/          # Unified Remote 協定研究
│   ├── emulstick_decompiled/        # EmulStick 協定研究
│   └── tools/
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── CLAUDE.md                        # 專案說明（開發指引）
└── README.md                        # 本檔案
```

## 🔧 技術細節

### Unified Remote 通訊協定（TCP/藍牙）

使用自訂二進制格式（非 JSON）：

**封包格式：**
```
[4 bytes: 資料長度（Big Endian）]
[1 byte:  加密旗標（0=未加密, 1=已加密）]
[N bytes: 二進制序列化資料]
```

**型別標記：**
```
1 = Root Dictionary
2 = Named Dictionary
3 = Integer (4 bytes)
4 = Boolean (1 byte)
5 = String (UTF-8 + null terminator)
6 = Array
7 = Binary (length + data)
8 = Byte (1 byte)
9 = Number (Double, 8 bytes)
0 = End marker
```

**Packet 結構：**
```kotlin
data class Packet(
    var Action: Byte? = null,
    var Request: Byte? = null,
    var Response: Byte? = null,
    var KeepAlive: Boolean? = null,
    var Run: Action? = null,
    var Session: String? = null,
    var Source: String? = null,
    var Destination: String? = null,
    var Version: Int? = null,
    var Password: String? = null
)
```

**Action 結構：**
```kotlin
data class Action(
    var Name: String? = null,       // 例如："move", "click", "keyboard"
    var Target: String? = null,     // 例如："text", "key"
    var Extras: Extras? = null      // 參數列表
)
```

### EmulStick BLE 協定

**GATT 服務架構：**
```
EmulStick Service (0xF800)
├── CH1 (0xF801) - 鍵盤 HID Report
├── CH2 (0xF802) - CustomIn Report（IME Direct）
├── CH3 (0xF803) - 滑鼠 HID Report (Ver ≥1)
├── CH4 (0xF804) - 觸控筆/多媒體 HID Report
└── COMMAND (0xF80F) - 控制指令（身份驗證）
```

**HID Report 格式（Ver ≥1）：**

MouseV1 (CH3):
```
[按鍵][X_low][X_high][Y_low][Y_high][滾輪]
6 bytes, 無 Report ID
```

SingleKeyboard (CH1):
```
[修飾鍵][保留][Key1][Key2][Key3][Key4][Key5][Key6]
8 bytes, 無 Report ID
```

### 控制指令範例

**滑鼠移動：**
```kotlin
mouseController.move(deltaX = 100, deltaY = 50)
// TCP: Action("move", null, Extras(["x": "100", "y": "50"]))
// BLE: MouseV1 HID Report
```

**滑鼠點擊：**
```kotlin
mouseController.click("left")   // 左鍵
mouseController.click("middle") // 中鍵
mouseController.click("right")  // 右鍵
```

**鍵盤輸入文字：**
```kotlin
keyboardController.type("Hello World")
// TCP: Action("keyboard", "text", Extras(["text": "Hello World"]))
// BLE: SingleKeyboard HID Report 或 CustomIn Report（中文）
```

**鍵盤組合鍵：**
```kotlin
keyboardController.press("g", listOf("win"))
// TCP: Action("keyboard", "key", Extras(["key": "g", "modifier": "win"]))
// BLE: SingleKeyboard HID Report with modifiers
```

**滾輪：**
```kotlin
mouseController.scroll(delta = 3)   // 向上滾動
mouseController.hscroll(delta = -2) // 向左滾動
```

### 技術棧

- **語言**：Kotlin 1.9.10
- **UI 框架**：Jetpack Compose (BOM 2023.10.01)
- **Material Design**：Material 3
- **非同步**：Kotlin Coroutines 1.7.3
- **導航**：Navigation Compose 2.7.5
- **資料儲存**：DataStore Preferences 1.0.0
- **序列化**：Gson 2.10.1
- **最低 API**：21 (Android 5.0)
- **目標 API**：34 (Android 14)

## 🐛 故障排除

### ⚠️ 安全檢查（TCP 模式使用前必讀）

**在使用 TCP 模式前，請確認：**

1. 🔴 **您是否在 Tailscale/VPN 等私有網路中？**
   - ✅ 是 → 可以繼續使用
   - ❌ 否 → **絕對不要使用 TCP 模式**，改用 BLE 模式

2. 🔐 **Unified Remote Server 是否設定強密碼？**
   - ✅ 是 → 可以繼續
   - ❌ 否 → **立即設定強密碼**

3. 🌐 **伺服器是否暴露在公開網路？**
   - ✅ 否，僅 Tailscale 可連 → 安全
   - ❌ 是，開放公開 IP/埠號 → **立即關閉！**

### TCP 模式連線失敗

**檢查清單：**

1. ✅ **Tailscale 是否正常運作**
   ```bash
   tailscale status
   ```

2. ✅ **伺服器 IP 是否正確**
   ```bash
   tailscale ip
   ```

3. ✅ **Unified Remote Server 是否執行中**
   - 檢查系統匣圖示
   - 確認 Server 版本（建議最新版）

4. ✅ **防火牆是否允許 9512 埠**
   ```powershell
   # Windows PowerShell (管理員)
   New-NetFirewallRule -DisplayName "Unified Remote" -Direction Inbound -Protocol TCP -LocalPort 9512 -Action Allow
   ```

   ⚠️ **安全提醒**：此規則應搭配 Tailscale 防火牆，不要直接開放給公開網路！

5. ✅ **查看除錯日誌**
   - 點擊 App 內的 📊 按鈕
   - 查看錯誤訊息

### BLE 模式連線失敗

**檢查清單：**

1. ✅ **EmulStick 接收器是否插入 PC**
   - 確認 USB 連接正常
   - Windows：裝置管理員應顯示藍牙裝置

2. ✅ **藍牙權限是否授予**
   - Android 12+：需要「附近裝置」權限
   - Android 11 以下：需要「位置」權限

3. ✅ **是否掃描到裝置**
   - 重新掃描
   - 確認 EmulStick 沒有連接到其他裝置

4. ✅ **身份驗證失敗**
   - 檢查除錯日誌
   - 確認 EmulStick 版本號

5. ✅ **查看除錯日誌**
   - 點擊 App 內的 📊 按鈕
   - 篩選「HID」類型查看 HID 報告
   - 篩選「LED」類型查看 LED 狀態

### 頻繁斷線

**TCP 模式可能原因：**
- Tailscale 使用 relay（延遲高）
- 網路不穩定
- 伺服器回應慢
- Android 系統省電模式

**BLE 模式可能原因：**
- 藍牙訊號干擾
- 距離太遠（建議 5 公尺內）
- Android 系統省電模式
- EmulStick 韌體問題

**解決方案：**

1. **檢查連線品質**
   ```bash
   # TCP 模式
   tailscale ping [IP]
   ```

2. **使用 WiFi 而非行動網路**（TCP 模式）

3. **關閉 Android 省電模式**
   - 設定 → 電池 → 將 App 加入白名單

4. **減少藍牙干擾**（BLE 模式）
   - 關閉不必要的藍牙裝置
   - 避免與 WiFi 路由器太近

### 滑鼠/鍵盤無回應

**檢查：**

1. **除錯日誌是否顯示「發送封包」**
   - 查看 📊 日誌
   - TCP: 確認有 "Sending packet" 訊息
   - BLE: 確認有 "Wrote to" 訊息

2. **是否收到心跳回應**（TCP 模式）
   - 查看 "Heartbeat response received"

3. **BLE LED 狀態是否正常**（BLE 模式）
   - 查看除錯日誌的 LED 狀態
   - 確認 NumLock 已啟用（Alt 碼輸入需要）

4. **重新連線**
   - 點擊 ❌ 斷線
   - 重新點擊「連接」

### 軟體鍵盤不顯示

**解決方案：**

1. **關閉並重新打開 📝 輸入面板**

2. **手動點擊文字輸入框**

3. **檢查 Android 輸入法設定**
   - 設定 → 系統 → 語言與輸入法
   - 確認預設輸入法已啟用

### BLE 中文輸入亂碼

**解決方案：**

1. **使用 IME Direct 模式**
   - 直接在 📝 輸入面板輸入
   - 不使用組合鍵

2. **檢查除錯日誌**
   - 查看 "Sent text via IME" 訊息
   - 確認 UTF-8 編碼正常

3. **確認 EmulStick 版本**
   - Ver ≥1 支援 CustomIn 報告

### XInput 模式問題

**模式切換失敗：**

1. **確認 BLE 連線正常**
   - 必須先連接到 EmulStick 接收器
   - 查看除錯日誌確認身份驗證成功

2. **檢查除錯日誌**
   - 查看切換指令是否發送成功
   - 確認沒有 GATT 錯誤

3. **重新連接**
   - 斷線後重新連接
   - 確保接收器沒有連接到其他裝置

**遊戲無法偵測到控制器：**

1. **確認 Windows 裝置管理員**
   - 設定 → 裝置 → 遊戲控制器
   - 應該顯示「Xbox 360 Controller for Windows」

2. **測試控制器功能**
   - 在遊戲控制器內容中測試按鈕/搖桿
   - 確認所有輸入都能正確回應

3. **重新啟動遊戲**
   - 部分遊戲需要在控制器連接後啟動
   - 或在遊戲設定中重新偵測控制器

**搖桿/按鈕無反應：**

1. **確認在 XInput 模式**
   - 介面應該顯示虛擬手把 UI
   - 頂部開關應該處於「開啟」狀態

2. **檢查觸控操作**
   - 搖桿：確實拖曳（不是點擊）
   - 按鈕：按住（不要立即放開）

3. **查看除錯日誌**
   - 篩選「HID」類型
   - 確認有發送 XInput 報告

## 🔍 開發指南

### 修改連線參數（TCP 模式）

編輯 `app/src/main/kotlin/com/unifiedremote/evo/network/ConnectionManager.kt`：

```kotlin
companion object {
    const val SERVER_PORT = 9512
    const val CONNECTION_TIMEOUT = 3000        // 連線 timeout (ms)
    const val SOCKET_TIMEOUT = 10000           // Socket 讀取 timeout (ms)
    const val BUFFER_SIZE = 16384              // 緩衝區大小 (bytes)

    const val HEARTBEAT_INTERVAL = 15000L      // 心跳間隔 (ms)
    const val HEARTBEAT_TIMEOUT = 5000L        // 心跳超時 (ms)

    val RECONNECT_DELAYS = longArrayOf(500, 1000, 2000)  // 重連延遲
    const val MAX_RECONNECT_ATTEMPTS = 10
}
```

### 修改 BLE 參數

編輯 `app/src/main/kotlin/com/unifiedremote/evo/network/ble/BleManager.kt`：

```kotlin
companion object {
    private const val SCAN_TIMEOUT = 4000L     // 掃描逾時 (ms)
    private const val CONNECTION_TIMEOUT = 5000L  // 連線逾時 (ms)
}
```

### 新增快捷鍵

編輯 `app/src/main/kotlin/com/unifiedremote/evo/ui/screens/TouchpadScreen.kt`：

在 `ShortcutsDialogContent` 函數中加入新按鈕：

```kotlin
Button(
    onClick = { keyboardController.press("n", listOf("ctrl")) },
    modifier = Modifier.weight(1f).height(50.dp)
) {
    Text("新增")  // Ctrl+N
}
```

### 自訂 UI 主題

編輯 `app/src/main/kotlin/com/unifiedremote/evo/ui/theme/Color.kt`：

```kotlin
val DarkBackground = Color(0xFF1C1B1F)      // 主背景
val TouchpadBackground = Color(0xFF383541)  // 觸控板背景
val Purple80 = Color(0xFFD0BCFF)            // 主題色
```

### 調整按鈕大小

編輯 `app/src/main/kotlin/com/unifiedremote/evo/ui/screens/TouchpadScreen.kt`：

```kotlin
// 滑鼠按鍵高度
.height(60.dp)  // 修改此值

// 滾輪條寬度
.width(50.dp)   // 修改此值
```

### 調整 BLE 靈敏度

在 App 內使用「⚙️ 靈敏度設定」功能：
- 滑鼠速度：0.5x - 5.0x（預設 1.0x）
- 滾輪速度：0.5x - 5.0x（預設 1.0x）
- 設定即時生效，自動儲存

## 📝 已知限制

### Unified Remote 模式
- 🔴 **存在 RCE 安全漏洞**：Unified Remote Server 有遠端程式碼執行漏洞
- 🔴 **官方已停止維護**：安全漏洞不會修補（最後更新 2022）
- ⚠️ **僅限私有網路使用**：必須在 Tailscale/VPN 等可信任網路中使用
- ⚠️ 不支援加密連線（原版有加密旗標但未實作）
- ⚠️ 不支援自動探索伺服器（需手動輸入 IP）
- ⚠️ 藍牙模式尚未實際測試

### BLE 模式
- ⚠️ 僅支援 EmulStick Ver ≥1（MouseV1/SingleKeyboard）
- ⚠️ CustomIn 模式無法使用組合鍵
- ⚠️ 大幅度滑鼠移動會自動分割（±2047 限制）
- ⚠️ NumLock 狀態可能與 PC 不同步（首次連線）
- ⚠️ XInput 模式與鍵盤/滑鼠模式互斥（需手動切換）

### 共通限制
- ⚠️ 不支援多指手勢
- ⚠️ 不支援 Gamepad/觸控筆等進階功能

## 📊 專案狀態

**目前狀態：** 🔄 **持續開發中**

- ✅ **BLE 模式**：已測試，穩定運作（**推薦使用，更安全**）
- ✅ TCP 模式：已測試，穩定運作（⚠️ **需在 Tailscale/VPN 中使用，存在 RCE 風險**）
- ⚠️ 藍牙模式：已實作，待測試
- ⚠️ BLE XInput 模式：已實作，待實機測試
- 🔄 核心功能持續優化（滑鼠、鍵盤、連線管理、遊戲手把）
- 🔄 根據實際使用回饋持續改進

**安全建議**：
- 🔐 優先使用 **BLE 模式**（EmulStick），無需網路，無 RCE 風險
- ⚠️ TCP 模式僅在必要時使用，且**必須在 Tailscale 等私有 VPN 中**

## 🎯 未來計畫

### 短期（觀察中）
- [ ] **根據實測回饋優化**：連線穩定性、UI/UX 調整
- [ ] **藍牙模式實際測試**：驗證 RFCOMM 連線穩定性
- [ ] **BLE 模式改進**：
  - [ ] 支援 EmulStick Ver 0 裝置（舊版格式）
  - [ ] NumLock 狀態同步優化
  - [ ] 自動重連機制

### 中長期
- [ ] 連線歷史記錄與快速切換（部分完成）
- [ ] 自動探索伺服器（Tailscale API）
- [ ] Widget 支援（快速連線）
- [ ] 自訂按鈕配置
- [ ] Dark/Light 主題切換

### 低優先級
- [ ] 自行開發伺服器端（C# / Python / Rust）
- [ ] 多指手勢支援
- [ ] 加密連線支援
- [ ] 多語言支援
- [ ] iOS/iPadOS 版本（技術可行性評估中）

## 🙏 致謝

**輔助技術與產品**：
- [Unified Remote](https://www.unifiedremote.com/) - 提供遠端遙控基礎協定
- [EmulStick](https://www.emulstick.com/) - 提供即插即用的 BLE HID 接收器
  - ⚠️ **使用 EmulStick 功能需購買硬體接收器**，請支持原廠

**無障礙設計參考**：
- [Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility) - Android 無障礙開發指引
- [Material Design Accessibility](https://m3.material.io/foundations/accessible-design) - 無障礙設計原則

**技術與工具**：
- [Tailscale](https://tailscale.com/) - 優秀的 VPN 服務
- [Android Jetpack Compose](https://developer.android.com/jetpack/compose) - 現代化 UI 框架
- [Material Design 3](https://m3.material.io/) - 設計系統

**社群與開源精神**：
- 感謝所有開源專案的貢獻者
- 感謝輔助技術社群的支持與回饋
- 本專案致力於改善身心障礙者的電腦使用體驗

## 📄 授權與免責聲明

### 使用授權

本專案採用 **GNU General Public License v3.0 (GPL-3.0)** 授權。

- ✅ 允許商業使用
- ✅ 允許修改與再散布
- ✅ 允許專利使用
- ✅ 允許私人使用
- ⚠️ **衍生作品必須以相同授權開源** (Copyleft)
- ⚠️ 必須包含授權聲明與版權資訊
- ⚠️ 必須說明對原始程式碼的修改
- ⚠️ 必須提供原始碼

完整授權條款請參閱 [LICENSE](LICENSE) 檔案。

### 免責聲明

**關於智慧財產權**：
- ✅ 本專案**不包含任何原版程式碼**或專有資產
- ✅ 完全從頭使用 Kotlin + Jetpack Compose 重新實作
- ✅ 透過公開可用的技術規格實作相容性協定
- ✅ 開發目的為**輔助技術**，改善身心障礙者使用體驗

**關於相容性**：
- 本專案實作與以下產品相容的協定：
  - Unified Remote Server（TCP/藍牙模式）
  - EmulStick 藍牙接收器（BLE HID 模式）
- **輔助技術相容性**：類似於螢幕閱讀器、語音輸入等輔助工具與標準軟體的相容

**關於硬體購買**：
- 本專案**僅提供軟體客戶端**，不提供任何硬體
- **EmulStick 接收器需另行購買**：[https://www.emulstick.com/](https://www.emulstick.com/)
- 本專案**不繞過任何購買驗證**或商業模式
- 如果您覺得這些產品有用，請支持原廠購買正版

**關於商標**：
- "Unified Remote" 與 "EmulStick" 為其各自所有者的商標
- 本專案使用這些名稱僅用於描述相容性
- 本專案與這些商標所有者無任何商業關係

**關於責任**：
- 本軟體「依現狀」提供，不提供任何形式的保證
- 使用者自行承擔使用本軟體的所有風險
- 開發者不對使用本軟體造成的任何損害負責

### 如果您是權利所有者

如果您是 Unified Remote 或 EmulStick 的權利所有者，並認為本專案侵犯了您的權利，請透過 GitHub Issues 聯繫，我們將立即配合處理。

### 輔助技術參考

本專案的開發理念類似於：
- **螢幕閱讀器**（如 NVDA、JAWS）：為視障者提供軟體存取能力
- **語音輸入系統**：為肢體障礙者提供免手操作
- **替代輸入裝置驅動**：為特殊需求使用者提供客製化控制方式
- **第三方相容客戶端**：實作標準協定以改善無障礙體驗

這些輔助技術專案均致力於改善身心障礙者的數位使用體驗，在各司法管轄區內受到支持與保護。

---

## 📌 專案資訊

**專案狀態：** 🔄 持續開發中（個人專案）

**套件名稱：** `com.unifiedremote.evo`

**開發背景：**
- **輔助技術需求**：為僅單指可操作的身心障礙使用者設計無障礙介面
- 現有遙控 APP 缺乏針對輔助功能的優化（按鈕太小、手勢複雜）
- 從頭開發相容客戶端，針對單指操作進行全面優化
- 整合多種連線模式（TCP/藍牙/BLE），適應不同輔助情境

**開發歷程：**
- 2025-10 月初啟動專案開發
- 2025-10-10 包名從 `lite` 更新為 `evo`
- 2025-10-11 新增 XInput 模式（Xbox 360 控制器模擬）
- 2025-10-13 優化心跳機制與連線穩定性
- 持續根據實際使用回饋改進功能

**授權資訊：**
- **授權**：GNU General Public License v3.0 (GPL-3.0)
- **版權**：2025 Unified Remote Evo Contributors
- **專案網址**：https://github.com/[your-username]/unified-remote-evo

**聯繫方式：**
- 如有任何問題或建議，歡迎提出 GitHub Issue
- 如有法律相關疑慮，請透過 Issue 聯繫，我們將立即處理

---

## 📜 版權聲明

```
Copyright (C) 2025 Unified Remote Evo Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

---

## 💡 進階說明

### BLE 身份驗證

EmulStick 接收器使用 AES 加密進行身份驗證：

1. **讀取裝置資訊**：System ID、Firmware Version、Hardware Revision、Software Version
2. **取得明文密碼**：根據 Software Version 選擇對應的密碼字串
3. **AES 加密**：使用 System ID 作為密鑰加密明文
4. **比對驗證**：將本地加密結果與接收器回傳的密文比對

### HID 報告格式（Ver ≥1）

**MouseV1（CH3, 6 bytes）**：
```
[按鍵][X_low][X_high][Y_low][Y_high][滾輪]
範圍：X/Y ±2047, 滾輪 ±15
```

**SingleKeyboard（CH1, 8 bytes）**：
```
[修飾鍵][保留][Key1][Key2][Key3][Key4][Key5][Key6]
支援最多 6 個按鍵同時按下
```

**CustomIn（CH2, 19 bytes）**：
```
[Report ID=40][資料類型=32][資料長度][UTF-8 資料（最多 16 bytes）]
用於中英文混合輸入（IME Direct 模式）
```

### Xbox 360 控制器格式（20 bytes）

```
[固定值][長度][D-Pad+按鈕1][按鈕2][LT][RT][左搖桿X][左搖桿Y][右搖桿X][右搖桿Y][保留×6]
```

**按鈕映射**：
- Byte 2: D-Pad (0-3位) + Start/Back/L3/R3 (4-7位)
- Byte 3: LB/RB/A/B/X/Y

### 兩段式掃描策略

為了提高 BLE 掃描成功率：

**第一階段（1.5秒）**：使用 Service UUID 過濾（`0xF800`）
**第二階段（2.5秒）**：移除過濾器，在回呼中用名稱/Service UUIDs 過濾

總掃描時間 4 秒，兼容廣播資料不完整的裝置。
