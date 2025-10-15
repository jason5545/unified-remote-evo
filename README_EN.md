# Unified Remote Evo - Enhanced Remote Control App

**[English](README_EN.md) | 中文**

> **♿ Assistive Technology Project**
>
> An accessible remote control app designed specifically for **users with disabilities**.
> Optimized for **single-finger operation** with large buttons, simplified interface, and multi-mode connectivity support.

A **dual-system, three-mode remote control solution** designed for **accessibility needs** and **long-term stable connections**.

## 📢 Important Notice

### ⚠️ Security Warning

**Unified Remote Server has known RCE (Remote Code Execution) vulnerabilities!**

- 🔴 **DO NOT expose Unified Remote Server on public networks**
- 🔴 **DO NOT use default passwords or weak passwords**
- ✅ **Recommended for use only on trusted private networks** (such as Tailscale VPN)
- ✅ **Recommended to set strong passwords and change them regularly**
- ⚠️ **Official maintenance has stopped, security vulnerabilities will not be patched**

**Risk Explanation**:
- Attackers could potentially execute arbitrary code on your PC through these vulnerabilities
- It's recommended to prioritize **BLE mode** (EmulStick), which requires no network connection and is more secure
- If you must use TCP mode, **only use it within encrypted VPN environments like Tailscale**

### About This Project

**Project Positioning**:
- ✅ **Assistive Technology Tool**: An accessibility-focused remote client designed for users with disabilities
- ✅ **Compatibility Implementation**: Supports compatible protocols for Unified Remote Server and EmulStick receivers
- ✅ **Product Life Extension**: Original products are no longer updated; this project provides an accessibility-optimized modern solution
- ✅ **Complete Rewrite**: Built with Kotlin + Jetpack Compose, contains no original code

**Relationship with Official Products**:
- ❌ **Not Officially Authorized**: This project is not an official product of Unified Remote or EmulStick
- ❌ **Not a Replacement**: Does not claim to be "official" or a "replacement", only an accessibility-compatible implementation
- ✅ **Assistive Technology**: Developed to improve the remote control experience for users with disabilities
- ✅ **Personal Use**: Primarily for learning, research, and personal assistive use, not a commercial project

**Why Develop This Project?**
1. **Accessibility Needs**: Designed specifically for **users with disabilities** (operable with a single finger)
2. **Official Discontinued**: Unified Remote Server has not been updated since 2022, lacking accessibility optimizations
3. **Accessibility Design**: Large buttons, single-finger operation, simplified interface, long-term stable connections
4. **Multi-Mode Integration**: TCP, Bluetooth, and BLE three-in-one solution for different usage scenarios

## 📡 Three Connection Modes

### System 1: Unified Remote (Network Remote Control)
Requires Unified Remote Server installed on PC.

> **⚠️ Security Reminder**: Unified Remote Server has RCE vulnerabilities, **use only within trusted private VPNs like Tailscale**!

**Mode 1-A: TCP/IP Connection** ✅
- Connects to Unified Remote Server via **TCP/IP**
- ⚠️ **Security Requirement**: **Use only within encrypted private networks like Tailscale/VPN**
- 🔐 Set strong passwords, do not expose to public networks
- Implemented and tested

**Mode 1-B: Bluetooth Connection** ⚠️
- Connects to Unified Remote Server via **Classic Bluetooth (RFCOMM)**
- Suitable for **close-range, no-network environments**
- Uses the same packet protocol as TCP
- Implemented, pending testing

### System 2: EmulStick (Receiver Mode) ✅
No server software required, plug and play.

> **📦 Hardware Requirement: EmulStick Receiver Purchase Required**
>
> This project only provides the software client. **EmulStick receiver must be purchased separately**.
>
> Official website: [https://www.emulstick.com/](https://www.emulstick.com/)
>
> If you find EmulStick products useful, please support the original manufacturer by purchasing authentic hardware.

**Mode 2: BLE HID Connection**
- Connects to EmulStick hardware receiver via **Bluetooth BLE**
- Receiver emulates standard **HID devices** (keyboard/mouse)
- Uses HID over GATT protocol
- Implemented and tested

**🎮 XInput Mode** (Added 2025-10)
- Switch to **Xbox 360 controller** in BLE mode
- Complete virtual gamepad UI (dual joysticks, buttons, triggers, D-Pad)
- Supports gaming (Steam Big Picture, XInput games, emulators)
- One-click switch back to keyboard/mouse mode

## 📱 Features

### ✨ Core Features

**Mouse Control:**
- Touchpad movement (drag gestures)
- Left, middle, right click
- Vertical/horizontal scroll wheels (independent scroll bars)
- Double click
- Long press drag mode (hold left button)

**Keyboard Control:**
- 📝 **Bottom Input Panel** (integrated design)
  - Modifier key selector (Ctrl, Shift, Alt, Win)
  - Software keyboard integration (real-time text input)
  - Combination key support (Win+G, Ctrl+Shift+Esc, etc.)
  - Collapsible virtual keys:
    - Arrow keys (↑↓←→)
    - Function keys (Enter, Tab, Esc, Del, Space, Back)
    - F1-F12
    - Other keys (Home, End, PgUp, PgDn, Insert)

- ⚡ **Shortcut Dialog** (quick access to common shortcuts)
  - Editing: Copy, Paste, Cut, Undo, Select All
  - System: Save, Find, Close, Alt+Tab, Alt+F4

**Debug Tools:**
- Real-time connection logs
- Statistics (total/errors/warnings/info)
- Log clearing function
- Log type filtering (BLE specific)

### 🎯 Single-Finger Operation Optimization

- **Right-side button layout**: Common functions on the right for right-handed operation
- **Bottom expandable panel**: Doesn't obstruct touchpad, maintains usability
- **Large button design**: Mouse left/right buttons 60dp height
- **Clear visuals**: Scroll bars use theme colors, clearly visible on black background
- **Collapsible interface**: Virtual keys collapsed by default, reducing scrolling distance

### ⚡ Tailscale Optimization (TCP Mode)

Connection parameters optimized for overlay network environments:

| Parameter | Original | Optimized | Improvement |
|-----------|----------|-----------|-------------|
| Heartbeat Interval | 60s | **15s** | 4x |
| Connection Timeout | 5s | **3s** | 1.67x |
| Reconnect Delay | 1-5s | **0.5-2s** | 2.5x |
| Buffer Size | 4KB | **16KB** | 4x |
| Heartbeat Timeout Detection | None | **5s auto reconnect** | New feature |

### 🔧 BLE Feature Optimization (EmulStick Mode)

- ✅ **NumLock Auto-Enable** (Alt code input support)
- ✅ **LED State Tracking** (NumLock/CapsLock/ScrollLock)
- ✅ **MouseV1/SingleKeyboard Support** (Ver ≥1 devices)
- ✅ **Device History Management** (quick reconnection)
- ✅ **XInput Mode** (Xbox 360 controller simulation)

### 🎮 XInput Gamepad Mode (EmulStick Exclusive)

**Feature Highlights:**
- **Dual Joystick Control**: Left/Right joysticks (touch drag, auto-center)
- **Button Panel**: A/B/X/Y (Xbox colors) + LB/RB shoulder buttons
- **Trigger Control**: LT/RT vertical sliders (0-100% precise control)
- **Direction Keys**: D-Pad eight-direction control
- **System Buttons**: Start/Back/L3/R3
- **Landscape/Portrait**: Auto-detect screen orientation, dynamic layout adjustment
- **Single-Finger Optimization**: Large buttons (48-70dp), large joysticks (130-140dp)
- **One-Click Switch**: Return button for quick switch back to keyboard/mouse mode

**Applicable Scenarios:**
- Steam Big Picture navigation and gaming
- Native XInput games (most modern PC games)
- Emulators (PCSX2, Dolphin, RetroArch, etc.)
- Windows Game Bar (Win+G)

## 🚀 Quick Start

### 1. Environment Setup

**Development Environment:**
- Android Studio (latest version)
- JDK 11+
- Android SDK (minimum API 21 / Android 5.0)

**Runtime Environment (TCP Mode):**
- Android device (tablet or phone)
- Windows PC (with Unified Remote Server installed)
- Tailscale (client and server)

**Runtime Environment (BLE Mode):**
- Android device (tablet or phone)
- **EmulStick Bluetooth Receiver** (USB plugged into PC)
  - ⚠️ **Separate purchase required**: [https://www.emulstick.com/](https://www.emulstick.com/)
  - This project only provides compatible client software, not hardware
- No server software installation required

### 2. Setting Up Tailscale (TCP Mode)

> **⚠️ Important Security Reminder**:
> - Unified Remote Server has RCE vulnerabilities, **never use on public networks**
> - **Must use private encrypted networks like Tailscale/VPN**
> - Set strong passwords and change them regularly

**Server Side (Windows/Mac/Linux):**
```bash
# Install and start Tailscale
tailscale up

# Check Tailscale IP
tailscale ip
# For example: 100.101.102.103
```

**Ensure Unified Remote Server is running**
- Default port: 9512 (TCP + UDP)
- Download: https://www.unifiedremote.com/
- 🔐 **Be sure to set a strong password**

### 3. Building APK

**Windows:**
```bash
./gradlew.bat assembleDebug
```

**Linux/Mac:**
```bash
./gradlew assembleDebug
```

**APK Output Location:**
```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Installation and Usage

#### TCP Mode (Unified Remote)
1. Transfer APK to Android device
2. Install APK (allow installation from unknown sources)
3. Open the app
4. Select "TCP Connection"
5. **Server Configuration:**
   - IP Address: Enter server Tailscale IP (e.g., 100.101.102.103)
   - Port: 9512 (default)
6. Click "Connect"
7. Interface automatically switches to touchpad after successful connection

#### BLE Mode (EmulStick)
1. Transfer APK to Android device
2. Install APK (allow installation from unknown sources)
3. Plug EmulStick receiver into PC USB port
4. Open the app
5. Select "BLE Connection"
6. Scan and select EmulStick device
7. Wait for authentication to complete
8. Interface automatically switches to touchpad after successful connection

## 🎮 Usage Instructions

### Interface Layout

**Top Connection Status Indicator:**
- Automatically displays connection status (connecting, reconnecting, error, disconnected)
- Only shows continuously during error states, doesn't interfere with touchpad operation
- Supports three modes: TCP, Bluetooth, BLE

**Top Function Bar:**
- Left: ⚙️ Sensitivity Settings, 📊 Debug Logs, ❌ Disconnect
- Center (BLE mode only): 🎮 Gamepad Switch - toggle XInput mode
- Right: ⚡ Shortcuts, 📝 Input Panel

**Hardware Model Indicator (BLE mode only):**
- Automatically detects currently connected hardware model
- Automatically selects optimal input mode based on hardware

**Central Touchpad:**
- Full-screen touch area
- Shows "Touchpad" or "Dragging..." hints

**Right Vertical Scroll Bar:**
- Vertical scroll control (light blue semi-transparent)
- Drag operation, supports sensitivity adjustment

**Bottom Area:**
- Horizontal scroll bar (left/right scroll, TCP/Bluetooth mode)
  - Note: BLE mode doesn't support horizontal scroll (MouseV1 format limitation)
- Mouse buttons: L (left), M (middle), R (right)

### Touchpad Gestures

- **Drag**: Move mouse cursor
- **Tap**: Left click
- **Double Tap**: Double click left button
- **Long Press**: Enter drag mode (hold left button)
  - Shows "Release Drag" button after long press
  - Click button to release left button

### 📝 Input Panel (Bottom ModalBottomSheet)

Click the 📝 button in the top right to open the input panel:

**BLE Input Mode Switch (BLE mode and original hardware only):**
- **Big5 Alt Code Mode**: For Traditional Chinese Big5 input
- **Alt+X Unicode Mode**: For Unicode character input
- Click button to switch modes

**Modifier Key Selector:**
- Ctrl, Shift, Alt, Win (multi-select supported)
- Uses FilterChip components, click to select/deselect

**1. Regular Text Input (Real-time Send Mode):**
- No modifier keys selected
- Use software keyboard directly for input
- **Text sent to PC in real-time**
- Supports delete key (including empty input box deletion)
- **BLE Mode**: Automatically selects optimal input method based on hardware

**2. Combination Key Input:**
- Select modifier keys (Ctrl/Shift/Alt/Win, multi-select supported)
- Use software keyboard to enter characters
- Click "Send: WIN + G" button to send
- Supports complex combinations (e.g., Ctrl+Shift+Esc)

**3. Virtual Keys (Collapsible Sections):**
- Expand "Arrow Keys" section → ↑↓←→ direction control
- Expand "Function Keys" section → Enter, Tab, Esc, Del, Space, Back
- Expand "F Keys" section → F1-F12
- Expand "Other Keys" section → Home, End, PgUp, PgDn, Insert
- Collapsed by default, reducing scrolling distance

### ⚡ Shortcut Dialog (Dialog)

Click the ⚡ button in the top right to open the shortcut dialog:

**Editing Shortcuts:**
- Copy (Ctrl+C)
- Cut (Ctrl+X)
- Paste (Ctrl+V)
- Undo (Ctrl+Z)
- Select All (Ctrl+A)

**System Shortcuts:**
- Save (Ctrl+S)
- Find (Ctrl+F)
- Close (Ctrl+W)
- Alt+Tab (switch windows)
- Alt+F4 (close program)

**UI Features:**
- Uses Dialog component (doesn't interfere with touchpad)
- Scrollable content (adapts to different screen sizes)
- Buttons use Grid layout (easy for single-finger operation)

### 📊 Debug Logs

1. Click the "📊" icon in the top left
2. View real-time connection logs
3. Statistics displayed at bottom
4. Click "🗑️" to clear logs
5. **BLE Mode**: Filter log types (general/HID/LED)

### 🎮 XInput Gamepad Mode (BLE Exclusive)

#### How to Enable
1. **Connect to EmulStick receiver** (BLE mode)
2. **Click the "Gamepad" switch** at the top (Switch to on)
3. **Wait for switch confirmation** (Toast message: "Switched to Xbox 360 controller mode")
4. **Interface automatically switches** to virtual gamepad UI

#### Virtual Gamepad Operation

**Joystick Control:**
- **Left Joystick**: Drag to control movement direction and magnitude
- **Right Joystick**: Drag to control view direction and magnitude
- **Auto-center on release**: Joystick returns to center when finger leaves

**Button Operation:**
- **A Button** (green, bottom): Confirm/Jump
- **B Button** (red, right): Cancel/Back
- **X Button** (blue, left): Reload/Use
- **Y Button** (yellow, top): Switch weapon/Interact
- **LB/RB** (shoulder buttons): Previous/Next
- **Start/Back**: Start/Select
- **L3/R3**: Joystick press (Sprint/Crouch)

**Trigger Control:**
- **LT/RT Sliders**: Vertical slide (0-100%)
- Used for accelerate/brake, aim/shoot

**Direction Keys (D-Pad):**
- **Up/Down/Left/Right**: Four-direction control
- Used for menu navigation, quick selection

#### Switching Back to Keyboard/Mouse Mode
1. **Click the "← Return to Composite Mode" button** in the top left
2. **Wait for switch confirmation** (Toast message: "Switched back to keyboard/mouse mode")
3. **Interface automatically switches** back to touchpad UI

#### Windows Device Confirmation
- Open "Settings → Devices → Game controllers"
- Should see "Xbox 360 Controller for Windows"
- Click "Properties" to test button/joystick functions

## 📂 Project Structure

```
unified-remote-evo/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/unifiedremote/evo/
│   │   │   ├── data/                # Data structures
│   │   │   │   ├── Packet.kt        # Packet definitions (TCP/Bluetooth)
│   │   │   │   ├── SavedDevice.kt   # Device records (three modes)
│   │   │   │   ├── DeviceHistoryManager.kt  # History management
│   │   │   │   ├── SensitivitySettings.kt   # Sensitivity settings
│   │   │   │   └── ThemeSettings.kt         # Theme settings
│   │   │   │
│   │   │   ├── network/             # Network layer
│   │   │   │   ├── tcp/             # TCP mode
│   │   │   │   │   ├── ConnectionManager.kt  # TCP connection management
│   │   │   │   │   ├── BinaryReader.kt       # Binary reader
│   │   │   │   │   ├── BinaryWriter.kt       # Binary writer
│   │   │   │   │   └── PacketSerializer.kt   # Packet serialization
│   │   │   │   ├── bluetooth/       # Bluetooth mode (RFCOMM)
│   │   │   │   │   └── BluetoothConnectionManager.kt  # Pending testing
│   │   │   │   ├── ble/             # BLE mode (EmulStick)
│   │   │   │   │   ├── BleManager.kt         # BLE connection management
│   │   │   │   │   ├── BleConnectionState.kt # Connection state
│   │   │   │   │   ├── HidReportBuilder.kt   # HID report builder
│   │   │   │   │   ├── GattConstants.kt      # GATT UUID definitions
│   │   │   │   │   ├── AesCryptUtil.kt       # AES encryption utility
│   │   │   │   │   ├── KeyboardLedState.kt   # LED state tracking
│   │   │   │   │   ├── BleXInputController.kt # Xbox 360 controller
│   │   │   │   │   └── ...                   # Other BLE related utilities
│   │   │   │   ├── UnifiedConnectionManager.kt  # Unified interface
│   │   │   │   ├── RemoteController.kt       # Controller interface
│   │   │   │   ├── ConnectionType.kt         # Connection type
│   │   │   │   └── ConnectionLogger.kt       # Logging system
│   │   │   │
│   │   │   ├── controller/          # Control layer
│   │   │   │   ├── MouseController.kt        # Unified Remote mouse
│   │   │   │   ├── KeyboardController.kt     # Unified Remote keyboard
│   │   │   │   ├── BleMouseController.kt     # EmulStick BLE mouse
│   │   │   │   └── BleKeyboardController.kt  # EmulStick BLE keyboard
│   │   │   │
│   │   │   ├── ui/                  # UI layer
│   │   │   │   ├── theme/           # Theme
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── components/      # UI components
│   │   │   │   │   └── XInputControlPanel.kt  # Xbox 360 virtual gamepad UI
│   │   │   │   └── screens/         # Screens
│   │   │   │       ├── MainScreen.kt          # Main screen (three mode selection)
│   │   │   │       ├── ServerConfigScreen.kt  # Unified Remote settings
│   │   │   │       ├── BleDeviceListScreen.kt # EmulStick BLE scanning
│   │   │   │       ├── TouchpadScreen.kt      # Touchpad (shared by three modes)
│   │   │   │       ├── DebugScreen.kt         # Debug logs (shared by three modes)
│   │   │   │       └── SensitivitySettingsScreen.kt # Sensitivity settings
│   │   │   │
│   │   │   ├── viewmodel/           # ViewModel
│   │   │   │   ├── BleViewModel.kt
│   │   │   │   └── BleUiState.kt
│   │   │   │
│   │   │   ├── agent/               # Background tasks
│   │   │   │   └── BleScanAgent.kt  # BLE scanning agent
│   │   │   │
│   │   │   └── MainActivity.kt      # Main program
│   │   │
│   │   ├── res/                     # Resources
│   │   └── AndroidManifest.xml
│   │
│   └── build.gradle.kts
│
├── apk_analysis/                    # Technical research materials (protocol analysis)
│   ├── unified_decompiled/          # Unified Remote protocol research
│   ├── emulstick_decompiled/        # EmulStick protocol research
│   └── tools/
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── CLAUDE.md                        # Project documentation (development guide)
└── README.md                        # This file (Chinese version)
```

## 🔧 Technical Details

### Unified Remote Communication Protocol (TCP/Bluetooth)

Uses custom binary format (not JSON):

**Packet Format:**
```
[4 bytes: Data length (Big Endian)]
[1 byte: Encryption flag (0=unencrypted, 1=encrypted)]
[N bytes: Binary serialized data]
```

**Type Tags:**
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

**Packet Structure:**
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

**Action Structure:**
```kotlin
data class Action(
    var Name: String? = null,       // e.g.: "move", "click", "keyboard"
    var Target: String? = null,     // e.g.: "text", "key"
    var Extras: Extras? = null      // Parameter list
)
```

### EmulStick BLE Protocol

**GATT Service Architecture:**
```
EmulStick Service (0xF800)
├── CH1 (0xF801) - Keyboard HID Report
├── CH2 (0xF802) - Gamepad HID Report
├── CH3 (0xF803) - Mouse HID Report (Ver ≥1)
├── CH4 (0xF804) - Stylus/Multimedia HID Report
└── COMMAND (0xF80F) - Control commands (authentication)
```

**HID Report Format (Ver ≥1):**

MouseV1 (CH3):
```
[Buttons][X_low][X_high][Y_low][Y_high][Scroll]
6 bytes, no Report ID
```

SingleKeyboard (CH1):
```
[Modifiers][Reserved][Key1][Key2][Key3][Key4][Key5][Key6]
8 bytes, no Report ID
```

### Control Command Examples

**Mouse Movement:**
```kotlin
mouseController.move(deltaX = 100, deltaY = 50)
// TCP: Action("move", null, Extras(["x": "100", "y": "50"]))
// BLE: MouseV1 HID Report
```

**Mouse Click:**
```kotlin
mouseController.click("left")   // Left click
mouseController.click("middle") // Middle click
mouseController.click("right")  // Right click
```

**Keyboard Text Input:**
```kotlin
keyboardController.type("Hello World")
// TCP: Action("keyboard", "text", Extras(["text": "Hello World"]))
// BLE: SingleKeyboard HID Report or CustomIn Report (for Chinese)
```

**Keyboard Combination:**
```kotlin
keyboardController.press("g", listOf("win"))
// TCP: Action("keyboard", "key", Extras(["key": "g", "modifier": "win"]))
// BLE: SingleKeyboard HID Report with modifiers
```

**Scroll Wheel:**
```kotlin
mouseController.scroll(delta = 3)   // Scroll up
mouseController.hscroll(delta = -2) // Scroll left
```

### Tech Stack

- **Language**: Kotlin 1.9.10
- **UI Framework**: Jetpack Compose (BOM 2023.10.01)
- **Material Design**: Material 3
- **Async**: Kotlin Coroutines 1.7.3
- **Navigation**: Navigation Compose 2.7.5
- **Data Storage**: DataStore Preferences 1.0.0
- **Serialization**: Gson 2.10.1
- **Minimum API**: 21 (Android 5.0)
- **Target API**: 34 (Android 14)

## 🐛 Troubleshooting

### ⚠️ Security Check (Must read before using TCP mode)

**Before using TCP mode, please confirm:**

1. 🔴 **Are you on a Tailscale/VPN or other private network?**
   - ✅ Yes → Can continue using
   - ❌ No → **DO NOT use TCP mode**, switch to BLE mode

2. 🔐 **Is Unified Remote Server set with a strong password?**
   - ✅ Yes → Can continue
   - ❌ No → **Set a strong password immediately**

3. 🌐 **Is the server exposed to public networks?**
   - ✅ No, only Tailscale can connect → Secure
   - ❌ Yes, open public IP/port → **Close immediately!**

### TCP Mode Connection Failure

**Checklist:**

1. ✅ **Is Tailscale working properly**
   ```bash
   tailscale status
   ```

2. ✅ **Is server IP correct**
   ```bash
   tailscale ip
   ```

3. ✅ **Is Unified Remote Server running**
   - Check system tray icon
   - Confirm Server version (latest recommended)

4. ✅ **Does firewall allow port 9512**
   ```powershell
   # Windows PowerShell (Administrator)
   New-NetFirewallRule -DisplayName "Unified Remote" -Direction Inbound -Protocol TCP -LocalPort 9512 -Action Allow
   ```

   ⚠️ **Security Reminder**: This rule should be paired with Tailscale firewall, don't open directly to public networks!

5. ✅ **Check debug logs**
   - Click 📊 button in the app
   - View error messages

### BLE Mode Connection Failure

**Checklist:**

1. ✅ **Is EmulStick receiver plugged into PC**
   - Confirm USB connection is normal
   - Windows: Device Manager should show Bluetooth device

2. ✅ **Are Bluetooth permissions granted**
   - Android 12+: Need "Nearby devices" permission
   - Android 11 and below: Need "Location" permission

3. ✅ **Is device detected during scan**
   - Rescan
   - Confirm EmulStick is not connected to other devices

4. ✅ **Authentication failure**
   - Check debug logs
   - Confirm EmulStick version number

5. ✅ **Check debug logs**
   - Click 📊 button in the app
   - Filter "HID" type to view HID reports
   - Filter "LED" type to view LED status

### Frequent Disconnections

**Possible causes for TCP mode:**
- Tailscale using relay (high latency)
- Network instability
- Server response slow
- Android system power saving mode

**Possible causes for BLE mode:**
- Bluetooth signal interference
- Distance too far (recommended within 5 meters)
- Android system power saving mode
- EmulStick firmware issues

**Solutions:**

1. **Check connection quality**
   ```bash
   # TCP mode
   tailscale ping [IP]
   ```

2. **Use WiFi instead of mobile network** (TCP mode)

3. **Turn off Android power saving mode**
   - Settings → Battery → Add app to whitelist

4. **Reduce Bluetooth interference** (BLE mode)
   - Turn off unnecessary Bluetooth devices
   - Avoid being too close to WiFi routers

### Mouse/Keyboard Not Responding

**Check:**

1. **Does debug log show "Sending packet"**
   - View 📊 logs
   - TCP: Confirm "Sending packet" messages
   - BLE: Confirm "Wrote to" messages

2. **Is heartbeat response received** (TCP mode)
   - Check "Heartbeat response received"

3. **Is BLE LED status normal** (BLE mode)
   - Check debug logs for LED status
   - Confirm NumLock is enabled (Alt code input needs it)

4. **Reconnect**
   - Click ❌ Disconnect
   - Click "Connect" again

### Software Keyboard Not Showing

**Solutions:**

1. **Close and reopen 📝 input panel**

2. **Manually click text input box**

3. **Check Android input method settings**
   - Settings → System → Language & Input
   - Confirm default input method is enabled

### BLE Chinese Input Garbled

**Solutions:**

1. **Use Alt code mode**
   - Switch to Big5 Alt code mode in 📝 input panel
   - Confirm NumLock is enabled

2. **Check debug logs**
   - View LED status
   - Confirm NumLock shows as enabled

3. **Confirm EmulStick version**
   - Ver ≥1 supports Alt code input

### XInput Mode Issues

**Mode Switch Failure:**

1. **Confirm BLE connection is normal**
   - Must first connect to EmulStick receiver
   - Check debug logs to confirm successful authentication

2. **Check debug logs**
   - Check if switch command was sent successfully
   - Confirm no GATT errors

3. **Reconnect**
   - Disconnect and reconnect
   - Ensure receiver is not connected to other devices

**Game Cannot Detect Controller:**

1. **Confirm Windows Device Manager**
   - Settings → Devices → Game controllers
   - Should show "Xbox 360 Controller for Windows"

2. **Test controller functions**
   - Test buttons/joysticks in game controller properties
   - Confirm all inputs respond correctly

3. **Restart game**
   - Some games need to start after controller is connected
   - Or re-detect controller in game settings

**Joystick/Buttons Not Responding:**

1. **Confirm in XInput mode**
   - Interface should show virtual gamepad UI
   - Top switch should be in "on" state

2. **Check touch operations**
   - Joystick: Actually drag (not tap)
   - Buttons: Press and hold (don't release immediately)

3. **Check debug logs**
   - Filter "HID" type
   - Confirm XInput reports are being sent

## 🔍 Development Guide

### Modifying Connection Parameters (TCP Mode)

Edit `app/src/main/kotlin/com/unifiedremote/evo/network/ConnectionManager.kt`:

```kotlin
companion object {
    const val SERVER_PORT = 9512
    const val CONNECTION_TIMEOUT = 3000        // Connection timeout (ms)
    const val SOCKET_TIMEOUT = 10000           // Socket read timeout (ms)
    const val BUFFER_SIZE = 16384              // Buffer size (bytes)

    const val HEARTBEAT_INTERVAL = 15000L      // Heartbeat interval (ms)
    const val HEARTBEAT_TIMEOUT = 5000L        // Heartbeat timeout (ms)

    val RECONNECT_DELAYS = longArrayOf(500, 1000, 2000)  // Reconnect delays
    const val MAX_RECONNECT_ATTEMPTS = 10
}
```

### Modifying BLE Parameters

Edit `app/src/main/kotlin/com/unifiedremote/evo/network/ble/BleManager.kt`:

```kotlin
companion object {
    private const val SCAN_TIMEOUT = 4000L     // Scan timeout (ms)
    private const val CONNECTION_TIMEOUT = 5000L  // Connection timeout (ms)
}
```

### Adding Shortcuts

Edit `app/src/main/kotlin/com/unifiedremote/evo/ui/screens/TouchpadScreen.kt`:

Add new button in `ShortcutsDialogContent` function:

```kotlin
Button(
    onClick = { keyboardController.press("n", listOf("ctrl")) },
    modifier = Modifier.weight(1f).height(50.dp)
) {
    Text("New")  // Ctrl+N
}
```

### Custom UI Theme

Edit `app/src/main/kotlin/com/unifiedremote/evo/ui/theme/Color.kt`:

```kotlin
val DarkBackground = Color(0xFF1C1B1F)      // Main background
val TouchpadBackground = Color(0xFF383541)  // Touchpad background
val Purple80 = Color(0xFFD0BCFF)            // Theme color
```

### Adjust Button Sizes

Edit `app/src/main/kotlin/com/unifiedremote/evo/ui/screens/TouchpadScreen.kt`:

```kotlin
// Mouse button height
.height(60.dp)  // Modify this value

// Scroll bar width
.width(50.dp)   // Modify this value
```

### Adjust BLE Sensitivity

Use "⚙️ Sensitivity Settings" feature in the app:
- Mouse speed: 0.5x - 5.0x (default 1.0x)
- Scroll speed: 0.5x - 5.0x (default 1.0x)
- Settings take effect immediately, auto-save

## 📝 Known Limitations

### Unified Remote Mode
- 🔴 **RCE Security Vulnerability**: Unified Remote Server has remote code execution vulnerabilities
- 🔴 **Official Maintenance Stopped**: Security vulnerabilities will not be patched (last update 2022)
- ⚠️ **Limited to Private Networks**: Must use within trusted private networks like Tailscale/VPN
- ⚠️ No encrypted connection support (original version has encryption flag but not implemented)
- ⚠️ No automatic server discovery (manual IP input required)
- ⚠️ Bluetooth mode not yet actually tested

### BLE Mode
- ⚠️ Only supports EmulStick Ver ≥1 (MouseV1/SingleKeyboard)
- ⚠️ CustomIn mode cannot use combination keys
- ⚠️ Large mouse movements are automatically split (±2047 limit)
- ⚠️ NumLock state may not sync with PC (first connection)
- ⚠️ XInput mode and keyboard/mouse mode are mutually exclusive (manual switch required)

### Common Limitations
- ⚠️ No multi-touch gesture support
- ⚠️ No advanced features like Gamepad/Stylus

## 📊 Project Status

**Current Status:** 🔄 **Under Development**

- ✅ **BLE Mode**: Tested, stable operation (**Recommended use, more secure**)
- ✅ TCP Mode: Tested, stable operation (⚠️ **Must use within Tailscale/VPN, RCE risk exists**)
- ⚠️ Bluetooth Mode: Implemented, pending testing
- ⚠️ BLE XInput Mode: Implemented, pending real device testing
- 🔄 Core features continuously optimized (mouse, keyboard, connection management, gamepad)
- 🔄 Continuously improving based on actual usage feedback

**Security Recommendations:**
- 🔐 Prioritize **BLE mode** (EmulStick), no network required, no RCE risk
- ⚠️ Use TCP mode only when necessary, and **must be within private VPNs like Tailscale**

## 🎯 Future Plans

### Short Term (Under Observation)
- [ ] **Optimize based on real testing feedback**: Connection stability, UI/UX adjustments
- [ ] **Actual Bluetooth mode testing**: Verify RFCOMM connection stability
- [ ] **BLE mode improvements**:
  - [ ] Support EmulStick Ver 0 devices (old format)
  - [ ] NumLock state synchronization optimization
  - [ ] Auto-reconnect mechanism

### Medium to Long Term
- [ ] Connection history and quick switching (partially completed)
- [ ] Automatic server discovery (Tailscale API)
- [ ] Widget support (quick connection)
- [ ] Custom button configuration
- [ ] Dark/Light theme switching

### Low Priority
- [ ] Self-developed server-side (C# / Python / Rust)
- [ ] Multi-touch gesture support
- [ ] Encrypted connection support
- [ ] Multi-language support
- [ ] iOS/iPadOS version (technical feasibility under evaluation)

## 🙏 Acknowledgments

**Assistive Technology and Products**:
- [Unified Remote](https://www.unifiedremote.com/) - Provides basic remote control protocols
- [EmulStick](https://www.emulstick.com/) - Provides plug-and-play BLE HID receivers
  - ⚠️ **Using EmulStick features requires purchasing hardware receiver**, please support the original manufacturer

**Accessibility Design References**:
- [Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility) - Android accessibility development guidelines
- [Material Design Accessibility](https://m3.material.io/foundations/accessible-design) - Accessibility design principles

**Technology and Tools**:
- [Tailscale](https://tailscale.com/) - Excellent VPN service
- [Android Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI framework
- [Material Design 3](https://m3.material.io/) - Design system

**Community and Open Source Spirit**:
- Thanks to all contributors of open source projects
- Thanks to the assistive technology community for support and feedback
- This project is dedicated to improving the computer usage experience for people with disabilities

## 📄 License and Disclaimer

### Usage License

This project is licensed under **GNU General Public License v3.0 (GPL-3.0)**.

- ✅ Allows commercial use
- ✅ Allows modification and redistribution
- ✅ Allows patent use
- ✅ Allows private use
- ⚠️ **Derivative works must be open source under the same license** (Copyleft)
- ⚠️ Must include license statement and copyright information
- ⚠️ Must state changes to original code
- ⚠️ Must provide source code

For full license terms, please see the [LICENSE](LICENSE) file.

### Disclaimer

**Regarding Intellectual Property**:
- ✅ This project **contains no original code** or proprietary assets
- ✅ Completely reimplemented from scratch using Kotlin + Jetpack Compose
- ✅ Implements compatible protocols through publicly available technical specifications
- ✅ Development purpose is **assistive technology**, improving the experience for users with disabilities

**Regarding Compatibility**:
- This project implements protocols compatible with the following products:
  - Unified Remote Server (TCP/Bluetooth mode)
  - EmulStick Bluetooth receiver (BLE HID mode)
- **Assistive Technology Compatibility**: Similar to the compatibility between screen readers, voice input, and other assistive tools with standard software

**Regarding Hardware Purchase**:
- This project **only provides software client**, does not provide any hardware
- **EmulStick receiver must be purchased separately**: [https://www.emulstick.com/](https://www.emulstick.com/)
- This project **does not bypass any purchase verification** or business model
- If you find these products useful, please support the original manufacturer by purchasing authentic products

**Regarding Trademarks**:
- "Unified Remote" and "EmulStick" are trademarks of their respective owners
- This project uses these names only for describing compatibility
- This project has no commercial relationship with these trademark owners

**Regarding Liability**:
- This software is provided "as is" without any warranty
- Users assume all risks of using this software
- Developers are not responsible for any damages caused by using this software

### If You Are a Rights Holder

If you are a rights holder of Unified Remote or EmulStick and believe this project infringes on your rights, please contact us through GitHub Issues, and we will immediately cooperate to address the issue.

### Assistive Technology References

The development philosophy of this project is similar to:
- **Screen Readers** (like NVDA, JAWS): Provide software access for visually impaired users
- **Voice Input Systems**: Provide hands-free operation for users with physical disabilities
- **Alternative Input Device Drivers**: Provide customized control methods for special needs users
- **Third-party Compatible Clients**: Implement standard protocols to improve accessibility experience

These assistive technology projects are dedicated to improving the digital usage experience for people with disabilities and are supported and protected in various jurisdictions.

---

## 📌 Project Information

**Project Status:** 🔄 Under Development (personal project)

**Package Name:** `com.unifiedremote.evo`

**Development Background**:
- **Assistive Technology Needs**: Design accessible interfaces for users with disabilities who can operate with only a single finger
- Existing remote apps lack accessibility optimizations (buttons too small, gestures complex)
- Develop compatible client from scratch, fully optimized for single-finger operation
- Integrate multiple connection modes (TCP/Bluetooth/BLE) for different assistive scenarios

**Development History**:
- Project development started in early October 2025
- 2025-10-10 Package name updated from `lite` to `evo`
- 2025-10-11 Added XInput mode (Xbox 360 controller simulation)
- 2025-10-13 Optimized heartbeat mechanism and connection stability
- Continuously improving features based on actual usage feedback

**License Information**:
- **License**: GNU General Public License v3.0 (GPL-3.0)
- **Copyright**: 2025 Unified Remote Evo Contributors
- **Project URL**: https://github.com/[your-username]/unified-remote-evo

**Contact Information**:
- For any questions or suggestions, please submit a GitHub Issue
- For legal concerns, please contact through Issues, we will address immediately

---

## 📜 Copyright Notice

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

## 💡 Advanced Explanation

### BLE Authentication

EmulStick receiver uses AES encryption for authentication:

1. **Read device information**: System ID, Firmware Version, Hardware Revision, Software Version
2. **Get plaintext password**: Select corresponding password string based on Software Version
3. **AES encryption**: Use System ID as key to encrypt plaintext
4. **Comparison verification**: Compare local encrypted result with ciphertext returned by receiver

### HID Report Format (Ver ≥1)

**MouseV1 (CH3, 6 bytes)**:
```
[Buttons][X_low][X_high][Y_low][Y_high][Scroll]
Range: X/Y ±2047, Scroll ±15
```

**SingleKeyboard (CH1, 8 bytes)**:
```
[Modifiers][Reserved][Key1][Key2][Key3][Key4][Key5][Key6]
Supports up to 6 simultaneous key presses
```

**XInput (CH2, 20 bytes)**:
```
[Fixed][Length][D-Pad+Buttons1][Buttons2][LT][RT][LeftStickX][LeftStickY][RightStickX][RightStickY][Reserved×6]
Used for Xbox 360 controller simulation
```

### Xbox 360 Controller Format (20 bytes)

```
[Fixed][Length][D-Pad+Buttons1][Buttons2][LT][RT][LeftStickX][LeftStickY][RightStickX][RightStickY][Reserved×6]
```

**Button Mapping**:
- Byte 2: D-Pad (bits 0-3) + Start/Back/L3/R3 (bits 4-7)
- Byte 3: LB/RB/A/B/X/Y

### Two-Stage Scanning Strategy

To improve BLE scanning success rate:

**First Stage (1.5 seconds)**: Use Service UUID filter (`0xF800`)
**Second Stage (2.5 seconds)**: Remove filter, filter by name/Service UUIDs in callback

Total scan time 4 seconds, compatible with devices with incomplete broadcast data.