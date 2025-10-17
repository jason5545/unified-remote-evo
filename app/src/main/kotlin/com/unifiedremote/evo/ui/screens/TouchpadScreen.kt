package com.unifiedremote.evo.ui.screens

import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import com.unifiedremote.evo.ui.util.NullCharFilterOffsetMapping
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unifiedremote.evo.controller.KeyboardController
import com.unifiedremote.evo.controller.MouseController
import com.unifiedremote.evo.data.SavedDevice
import com.unifiedremote.evo.network.ConnectionLogger
import com.unifiedremote.evo.network.UnifiedConnectionState
import com.unifiedremote.evo.network.ble.BleKeyboardControllerAdapter
import com.unifiedremote.evo.ui.components.ConnectionStatusIndicator
import com.unifiedremote.evo.ui.components.XInputControlPanel
import com.unifiedremote.evo.ui.theme.TouchpadBackground
import com.unifiedremote.evo.viewmodel.BleViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TouchpadScreen(
    mouseController: MouseController,
    keyboardController: KeyboardController,
    mouseSensitivity: Float = 1.0f,
    verticalScrollSensitivity: Float = 3.0f,
    horizontalScrollSensitivity: Float = 3.0f,
    onShowDebug: () -> Unit,
    onShowSettings: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    // 連線狀態（三模式）
    tcpConnectionState: StateFlow<UnifiedConnectionState>? = null,
    bluetoothConnectionState: StateFlow<UnifiedConnectionState>? = null,
    // BLE XInput 相關參數（改用 ViewModel）
    bleViewModel: BleViewModel? = null,
    onXInputModeChange: ((Boolean) -> Unit)? = null,
    // 裝置切換相關參數
    savedDevices: List<SavedDevice> = emptyList(),
    currentDeviceId: String? = null,
    onSwitchDevice: ((SavedDevice) -> Unit)? = null
) {
    var isDragging by remember { mutableStateOf(false) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    var showInputPanel by remember { mutableStateOf(false) }
    var showDeviceSwitcher by remember { mutableStateOf(false) }

    // ✅ 從 ViewModel 取得 XInput 狀態與控制器
    val isXInputMode by bleViewModel?.isXInputMode?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val xInputController = bleViewModel?.getXInputController()
    val bleManager = bleViewModel?.bleManager

    // 收集連線狀態（用於狀態指示器）
    val tcpState = tcpConnectionState?.collectAsStateWithLifecycle()?.value
    val bluetoothState = bluetoothConnectionState?.collectAsStateWithLifecycle()?.value
    val bleState = bleViewModel?.bleManager?.connectionState?.collectAsStateWithLifecycle()?.value

    Box(modifier = modifier.fillMaxSize()) {
        // 根據模式顯示不同 UI
        if (isXInputMode && xInputController != null) {
            // XInput 模式：只顯示虛擬手把 UI
            XInputControlPanel(
                xInputController = xInputController,
                onBack = {
                    // ✅ 返回組合模式（狀態由 ViewModel Flow 自動更新）
                    onXInputModeChange?.invoke(false)
                }
            )
        } else {
            // 組合模式：顯示觸控板/滑鼠/鍵盤 UI
            TouchpadModeUI(
                mouseController = mouseController,
                keyboardController = keyboardController,
                isDragging = isDragging,
                mouseSensitivity = mouseSensitivity,
                verticalScrollSensitivity = verticalScrollSensitivity,
                horizontalScrollSensitivity = horizontalScrollSensitivity,
                onDragStart = {
                    isDragging = true
                    mouseController.down("left")
                },
                onDragRelease = {
                    isDragging = false
                    mouseController.up("left")
                },
                onShowDebug = onShowDebug,
                onShowSettings = onShowSettings,
                onDisconnect = onDisconnect,
                bleManager = bleManager,
                isXInputMode = isXInputMode,
                onXInputToggle = if (bleViewModel != null) {
                    // ✅ 只觸發切換，狀態由 ViewModel Flow 自動更新
                    { enabled -> onXInputModeChange?.invoke(enabled) }
                } else null,
                showShortcutsDialog = showShortcutsDialog,
                onShowShortcutsDialog = { showShortcutsDialog = it },
                showInputPanel = showInputPanel,
                onShowInputPanel = { showInputPanel = it },
                onShowDeviceSwitcher = { showDeviceSwitcher = it }
            )
        }

        // 連線狀態指示器（最上層，不干擾觸控）
        ConnectionStatusIndicator(
            tcpState = tcpState,
            bluetoothState = bluetoothState,
            bleState = bleState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)  // 直接在頂部（不需要避開功能列）
        )
    }

    // 彈出式視窗（僅組合模式）
    if (!isXInputMode) {
        if (showShortcutsDialog) {
            Dialog(onDismissRequest = { showShortcutsDialog = false }) {
                ShortcutsDialogContent(
                    keyboardController = keyboardController,
                    onDismiss = { showShortcutsDialog = false }
                )
            }
        }

        if (showInputPanel) {
            FloatingInputPanel(
                keyboardController = keyboardController,
                onDismiss = { showInputPanel = false }
            )
        }

        if (showDeviceSwitcher) {
            Dialog(onDismissRequest = { showDeviceSwitcher = false }) {
                DeviceSwitcherDialog(
                    savedDevices = savedDevices,
                    currentDeviceId = currentDeviceId,
                    onSelectDevice = { device ->
                        showDeviceSwitcher = false
                        onSwitchDevice?.invoke(device)
                    },
                    onDismiss = { showDeviceSwitcher = false }
                )
            }
        }
    }
}

// ============ 組合模式 UI ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TouchpadModeUI(
    mouseController: MouseController,
    keyboardController: KeyboardController,
    isDragging: Boolean,
    mouseSensitivity: Float,
    verticalScrollSensitivity: Float,
    horizontalScrollSensitivity: Float,
    onDragStart: () -> Unit,
    onDragRelease: () -> Unit,
    onShowDebug: () -> Unit,
    onShowSettings: () -> Unit,
    onDisconnect: () -> Unit,
    bleManager: com.unifiedremote.evo.network.ble.BleManager?,
    isXInputMode: Boolean,
    onXInputToggle: ((Boolean) -> Unit)?,
    showShortcutsDialog: Boolean,
    onShowShortcutsDialog: (Boolean) -> Unit,
    showInputPanel: Boolean,
    onShowInputPanel: (Boolean) -> Unit,
    onShowDeviceSwitcher: (Boolean) -> Unit = {}
) {
    var showMenuBottomSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 全螢幕觸控板
        TouchpadArea(
            mouseController = mouseController,
            isDragging = isDragging,
            mouseSensitivity = mouseSensitivity,
            onDragStart = onDragStart,
            onDragRelease = onDragRelease,
            modifier = Modifier.fillMaxSize()
        )

        // 右側垂直滾輪條
        VerticalScrollBar(
            mouseController = mouseController,
            sensitivity = verticalScrollSensitivity,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 60.dp, bottom = 120.dp)
                .width(50.dp)
        )

        // 底部水平滾輪條（在左右鍵上方）
        // 注意：BLE 模式不支援水平滾輪（MouseV1 格式限制），故不顯示
        if (bleManager == null) {
            HorizontalScrollBar(
                mouseController = mouseController,
                sensitivity = horizontalScrollSensitivity,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 60.dp, bottom = 66.dp)
                    .height(40.dp)
            )
        }

        // 底部左右鍵
        MouseButtons(
            mouseController = mouseController,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp)
                .padding(6.dp)
        )

        // 硬體指示器（僅 BLE 模式顯示）
        if (bleManager != null) {
            val hardwareType = bleManager.getHardwareType()
            HardwareIndicator(
                hardwareType = hardwareType,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 8.dp)  // 直接在頂部，不需要避開功能列
            )
        }

        // 右下角浮動按鈕選單
        FloatingActionButton(
            onClick = { showMenuBottomSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 76.dp)  // 避開底部滑鼠按鈕
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "選單"
            )
        }
    }

    // 功能選單 BottomSheet
    if (showMenuBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuBottomSheet = false }
        ) {
            MenuBottomSheetContent(
                onShortcuts = {
                    showMenuBottomSheet = false
                    onShowShortcutsDialog(true)
                },
                onText = {
                    showMenuBottomSheet = false
                    onShowInputPanel(true)
                },
                onSettings = {
                    showMenuBottomSheet = false
                    onShowSettings()
                },
                onDebug = {
                    showMenuBottomSheet = false
                    onShowDebug()
                },
                onDisconnect = {
                    showMenuBottomSheet = false
                    onDisconnect()
                },
                onSwitchDevice = {
                    showMenuBottomSheet = false
                    onShowDeviceSwitcher(true)
                },
                isBleMode = bleManager != null,
                isXInputMode = isXInputMode,
                onXInputToggle = onXInputToggle
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchpadArea(
    mouseController: MouseController,
    isDragging: Boolean,
    mouseSensitivity: Float,
    onDragStart: () -> Unit,
    onDragRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 追蹤上次觸控位置（用於計算滑鼠移動）
    var lastX by remember { mutableStateOf(0f) }
    var lastY by remember { mutableStateOf(0f) }
    var accumulatedX by remember { mutableStateOf(0f) }
    var accumulatedY by remember { mutableStateOf(0f) }

    // 建立 GestureDetector（完全模仿原版）
    val gestureDetector = remember(isDragging) {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // 如果正在拖曳，點一下就釋放拖曳
                if (isDragging) {
                    onDragRelease()
                } else {
                    mouseController.click("left")
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                mouseController.doubleClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                onDragStart()
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        })
    }

    Box(
        modifier = modifier
            .background(TouchpadBackground)
            .pointerInteropFilter { event ->
                // 先讓 GestureDetector 處理（點擊、雙擊、長按）
                gestureDetector.onTouchEvent(event)

                // 同時處理滑鼠移動（模仿原版的 onTouchEvent）
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x
                        lastY = event.y
                        accumulatedX = 0f
                        accumulatedY = 0f
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.x - lastX
                        val deltaY = event.y - lastY

                        accumulatedX += deltaX * mouseSensitivity
                        accumulatedY += deltaY * mouseSensitivity

                        val moveX = accumulatedX.toInt()
                        val moveY = accumulatedY.toInt()

                        if (moveX != 0 || moveY != 0) {
                            mouseController.move(moveX, moveY)
                            accumulatedX -= moveX
                            accumulatedY -= moveY
                        }

                        lastX = event.x
                        lastY = event.y
                    }
                }

                true
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isDragging) "拖曳中..." else "觸控板",
            style = MaterialTheme.typography.titleLarge,
            color = if (isDragging)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }
}

// 垂直滾輪條（右側）
@Composable
fun VerticalScrollBar(
    mouseController: MouseController,
    sensitivity: Float = 3.0f,
    modifier: Modifier = Modifier
) {
    var accumulatedScroll by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            .pointerInput(sensitivity) {
                detectDragGestures(
                    onDragEnd = {
                        // 重置累積值
                        accumulatedScroll = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()

                    // 累積滾動量（套用靈敏度）
                    accumulatedScroll -= dragAmount.y / sensitivity

                    // 當累積量超過 1 時傳送
                    val delta = accumulatedScroll.toInt()
                    if (delta != 0) {
                        mouseController.scroll(delta)
                        accumulatedScroll -= delta
                    }
                }
            }
    )
}

// 水平滾輪條（底部）
@Composable
fun HorizontalScrollBar(
    mouseController: MouseController,
    sensitivity: Float = 3.0f,
    modifier: Modifier = Modifier
) {
    var accumulatedScroll by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            .pointerInput(sensitivity) {
                detectDragGestures(
                    onDragEnd = {
                        // 重置累積值
                        accumulatedScroll = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()

                    // 累積滾動量（套用靈敏度）
                    accumulatedScroll += dragAmount.x / sensitivity

                    // 當累積量超過 1 時傳送
                    val delta = accumulatedScroll.toInt()
                    if (delta != 0) {
                        mouseController.hscroll(delta)
                        accumulatedScroll -= delta
                    }
                }
            }
    )
}

// 底部滑鼠左右鍵
@Composable
fun MouseButtons(
    mouseController: MouseController,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Button(
            onClick = { mouseController.click("left") },
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
        ) {
            Text("L", style = MaterialTheme.typography.titleLarge)
        }
        Button(
            onClick = { mouseController.click("middle") },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text("M", style = MaterialTheme.typography.titleMedium)
        }
        Button(
            onClick = { mouseController.click("right") },
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
        ) {
            Text("R", style = MaterialTheme.typography.titleLarge)
        }
    }
}

// 功能選單底部面板內容
@Composable
fun MenuBottomSheetContent(
    onShortcuts: () -> Unit,
    onText: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
    onDisconnect: () -> Unit,
    onSwitchDevice: () -> Unit,
    isBleMode: Boolean = false,
    isXInputMode: Boolean = false,
    onXInputToggle: ((Boolean) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "功能選單",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 切換裝置
        Button(
            onClick = onSwitchDevice,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = "切換裝置"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("切換裝置", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 設定
        Button(
            onClick = onSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "設定"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("設定", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 除錯
        Button(
            onClick = onDebug,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = "除錯"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("除錯", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 斷線
        Button(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerOff,
                    contentDescription = "斷線"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("斷線", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Divider()

        Spacer(modifier = Modifier.height(16.dp))

        // 快捷鍵
        Button(
            onClick = onShortcuts,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.FlashOn,
                    contentDescription = "快捷鍵"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("快捷鍵", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 文字輸入
        Button(
            onClick = onText,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = "文字輸入"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("文字輸入", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // 遊戲手把切換（僅 BLE 模式）
        if (isBleMode && onXInputToggle != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Divider()

            Spacer(modifier = Modifier.height(16.dp))

            // 遊戲手把模式切換（使用 Card 包裹，更明顯）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = "遊戲手把"
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "遊戲手把模式",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "切換至虛擬手把控制",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isXInputMode,
                        onCheckedChange = onXInputToggle
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// 頂部功能列（已不使用，保留供參考）
@Composable
fun TopControlBar(
    onShortcuts: () -> Unit,
    onText: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
    onDisconnect: () -> Unit,
    onSwitchDevice: () -> Unit = {},
    modifier: Modifier = Modifier,
    isBleMode: Boolean = false,
    isXInputMode: Boolean = false,
    onXInputToggle: ((Boolean) -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSwitchDevice) {
                    Icon(Icons.Filled.SwapHoriz, "切換裝置")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, "設定")
                }
                IconButton(onClick = onDebug) {
                    Icon(Icons.Filled.BugReport, "除錯")
                }
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Filled.PowerOff, "斷線")
                }
            }

            // 中間：XInput 切換（僅 BLE 模式）
            if (isBleMode && onXInputToggle != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = "遊戲手把",
                        modifier = Modifier.size(16.dp)
                    )
                    Switch(
                        checked = isXInputMode,
                        onCheckedChange = onXInputToggle,
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onShortcuts) {
                    Icon(Icons.Filled.FlashOn, "快捷鍵")
                }
                IconButton(onClick = onText) {
                    Icon(Icons.Filled.Keyboard, "文字輸入")
                }
            }
        }
    }
}

// ============ 浮動可拖曳輸入面板 ============

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun FloatingInputPanel(
    keyboardController: KeyboardController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // 視窗大小（固定）
    val panelWidth = 360.dp
    val panelHeight = 500.dp

    // 取得螢幕大小
    val screenWidthPx = with(density) {
        context.resources.displayMetrics.widthPixels.toFloat()
    }
    val screenHeightPx = with(density) {
        context.resources.displayMetrics.heightPixels.toFloat()
    }
    val panelWidthPx = with(density) { panelWidth.toPx() }
    val panelHeightPx = with(density) { panelHeight.toPx() }

    // 預設位置：右下角（避開 FAB，留 100dp 邊距）
    val defaultOffsetX = screenWidthPx - panelWidthPx - with(density) { 16.dp.toPx() }
    val defaultOffsetY = screenHeightPx - panelHeightPx - with(density) { 160.dp.toPx() }

    // 拖曳位置狀態
    var offsetX by remember { mutableStateOf(defaultOffsetX) }
    var offsetY by remember { mutableStateOf(defaultOffsetY) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 浮動視窗
        Surface(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .width(panelWidth)
                .height(panelHeight),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 拖曳把手（標題列）
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()

                                // 更新位置
                                offsetX = (offsetX + dragAmount.x).coerceIn(
                                    0f,
                                    screenWidthPx - panelWidthPx
                                )
                                offsetY = (offsetY + dragAmount.y).coerceIn(
                                    0f,
                                    screenHeightPx - panelHeightPx
                                )
                            }
                        },
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "拖曳",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "輸入面板（可拖曳）",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "關閉",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // 面板內容（重用原有的 InputPanelContent 的內容）
                InputPanelContentBody(
                    keyboardController = keyboardController,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// 輸入面板內容主體（不含標題列，供浮動面板與底部面板共用）
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun InputPanelContentBody(
    keyboardController: KeyboardController,
    modifier: Modifier = Modifier
) {
    var modifierKeys by remember { mutableStateOf(setOf<String>()) }

    // ✅ 原廠技巧：預填充 1000 個空字元（使刪除鍵總是有效）
    val placeholderText = remember { "\u0000".repeat(1000) }
    var textInput by remember {
        mutableStateOf(TextFieldValue(
            text = placeholderText,
            selection = TextRange(placeholderText.length)
        ))
    }
    var previousText by remember { mutableStateOf(placeholderText) }
    var isResetting by remember { mutableStateOf(false) }

    // 摺疊狀態
    var showDirectionKeys by remember { mutableStateOf(false) }
    var showFunctionKeys by remember { mutableStateOf(false) }
    var showFKeys by remember { mutableStateOf(false) }
    var showOtherKeys by remember { mutableStateOf(false) }

    // 傳送組合鍵
    fun sendCombination() {
        val text = textInput.text.replace("\u0000", "")  // 移除空字元

        if (text.isNotEmpty()) {
            // 傳送每個字元作為組合鍵
            for (char in text) {
                if (char == '\n') {
                    if (modifierKeys.isEmpty()) {
                        keyboardController.enter()
                    } else {
                        keyboardController.press("enter", modifierKeys.toList())
                    }
                } else {
                    keyboardController.press(char.toString(), modifierKeys.toList())
                }
            }
            // 清除
            isResetting = true
            textInput = TextFieldValue(
                text = placeholderText,
                selection = TextRange(placeholderText.length)
            )
            previousText = placeholderText
            modifierKeys = setOf()
            isResetting = false
        }
    }

    // ✅ 監聽文字變化並即時傳送（模仿原廠 TextWatcher.onTextChanged）
    LaunchedEffect(textInput.text) {
        if (isResetting) return@LaunchedEffect  // 重置時不處理

        val currentText = textInput.text

        // ✅ 原廠邏輯：文字長度為 0 時視為按刪除鍵
        if (currentText.isEmpty()) {
            ConnectionLogger.log("空輸入框刪除", ConnectionLogger.LogLevel.DEBUG)
            keyboardController.backspace()

            // ✅ 重置為預填充狀態（模仿原廠 removeTextChangedListener + setText）
            isResetting = true
            textInput = TextFieldValue(
                text = placeholderText,
                selection = TextRange(placeholderText.length)
            )
            previousText = placeholderText
            isResetting = false
            return@LaunchedEffect
        }

        if (modifierKeys.isNotEmpty()) {
            previousText = currentText
            return@LaunchedEffect
        }

        // 計算差異
        val minLen = minOf(currentText.length, previousText.length)
        var commonPrefix = 0
        for (i in 0 until minLen) {
            if (currentText[i] == previousText[i]) {
                commonPrefix++
            } else {
                break
            }
        }

        val deletedCount = previousText.length - commonPrefix
        val addedText = currentText.substring(commonPrefix)

        // 處理刪除
        if (deletedCount > 0) {
            ConnectionLogger.log("刪除 $deletedCount 個字元", ConnectionLogger.LogLevel.DEBUG)
            repeat(deletedCount) {
                keyboardController.backspace()
            }
        }

        // 處理新增的文字
        for (char in addedText) {
            if (char == '\n') {
                keyboardController.enter()
            } else if (char != '\u0000') {
                keyboardController.type(char.toString())
            }
        }

        previousText = currentText
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // BLE 文字輸入模式切換（僅 BLE 模式且為原廠硬體時顯示）
        if (keyboardController is BleKeyboardControllerAdapter) {
            val bleKeyboardController = keyboardController.bleKeyboardController

            // 使用 remember + mutableStateOf 追蹤模式變化，確保 UI 更新
            var currentMode by remember { mutableStateOf(bleKeyboardController.getTextInputMode()) }

            val modeText = when (currentMode) {
                com.unifiedremote.evo.network.ble.BleTextInputMode.BIG5_ALT_CODE -> "Big5 Alt 碼"
                com.unifiedremote.evo.network.ble.BleTextInputMode.ALT_CODE -> "Alt+X Unicode"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("輸入模式：", style = MaterialTheme.typography.labelMedium)
                Button(
                    onClick = {
                        // 切換模式
                        val newMode = when (currentMode) {
                            com.unifiedremote.evo.network.ble.BleTextInputMode.BIG5_ALT_CODE ->
                                com.unifiedremote.evo.network.ble.BleTextInputMode.ALT_CODE
                            com.unifiedremote.evo.network.ble.BleTextInputMode.ALT_CODE ->
                                com.unifiedremote.evo.network.ble.BleTextInputMode.BIG5_ALT_CODE
                        }
                        // 更新控制器狀態
                        bleKeyboardController.setTextInputMode(newMode)
                        // 更新 UI 狀態（觸發 recompose）
                        currentMode = newMode
                    },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(modeText, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 修飾鍵選擇器
        Text("修飾鍵", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ctrl", "shift", "alt", "win").forEach { mod ->
                FilterChip(
                    selected = modifierKeys.contains(mod),
                    onClick = {
                        modifierKeys = if (modifierKeys.contains(mod)) {
                            modifierKeys - mod
                        } else {
                            modifierKeys + mod
                        }
                    },
                    label = { Text(mod.uppercase()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ✅ 文字輸入框（使用 Compose OutlinedTextField + visualTransformation 隱藏空字元）
        OutlinedTextField(
            value = textInput,
            onValueChange = { newValue ->
                if (!isResetting) {
                    textInput = newValue
                }
            },
            label = { Text(if (modifierKeys.isEmpty()) "文字輸入（即時傳送）" else "文字輸入") },
            maxLines = 3,
            visualTransformation = { text ->
                // 過濾掉空字元，讓使用者看到的是乾淨的輸入框
                val filtered = text.text.replace("\u0000", "")
                TransformedText(
                    AnnotatedString(filtered),
                    // ✅ 使用自訂 OffsetMapping 處理空字元過濾（避免 IndexOutOfBoundsException）
                    NullCharFilterOffsetMapping(text.text)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        // 傳送按鈕（當有修飾鍵時顯示）
        if (modifierKeys.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { sendCombination() },
                modifier = Modifier.fillMaxWidth()
            ) {
                val text = textInput.text.replace("\u0000", "")
                val preview = buildString {
                    modifierKeys.forEach { append(it.uppercase()).append(" + ") }
                    append(if (text.isEmpty()) "..." else text)
                }
                Text("傳送: $preview")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 可摺疊區塊：方向鍵
        ExpandableSection(
            title = "方向鍵",
            expanded = showDirectionKeys,
            onToggle = { showDirectionKeys = !showDirectionKeys }
        ) {
            DirectionKeysContent(keyboardController)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 可摺疊區塊：功能鍵
        ExpandableSection(
            title = "功能鍵",
            expanded = showFunctionKeys,
            onToggle = { showFunctionKeys = !showFunctionKeys }
        ) {
            FunctionKeysContent(keyboardController)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 可摺疊區塊：F1-F12
        ExpandableSection(
            title = "F 鍵",
            expanded = showFKeys,
            onToggle = { showFKeys = !showFKeys }
        ) {
            FKeysContent(keyboardController)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 可摺疊區塊：其他按鍵
        ExpandableSection(
            title = "其他按鍵",
            expanded = showOtherKeys,
            onToggle = { showOtherKeys = !showOtherKeys }
        ) {
            OtherKeysContent(keyboardController)
        }

        Spacer(modifier = Modifier.height(80.dp)) // 為軟體鍵盤留空間
    }
}

// 底部輸入面板（整合修飾鍵 + 文字輸入 + 虛擬鍵盤）
// 註：此 Composable 保留以備未來需要 BottomSheet 版本
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun InputPanelContent(
    keyboardController: KeyboardController,
    onDismiss: () -> Unit
) {
    var modifierKeys by remember { mutableStateOf(setOf<String>()) }

    // ✅ 原廠技巧：預填充 1000 個空字元（使刪除鍵總是有效）
    val placeholderText = remember { "\u0000".repeat(1000) }
    var textInput by remember {
        mutableStateOf(TextFieldValue(
            text = placeholderText,
            selection = TextRange(placeholderText.length)
        ))
    }
    var previousText by remember { mutableStateOf(placeholderText) }
    var isResetting by remember { mutableStateOf(false) }

    // 摺疊狀態
    var showDirectionKeys by remember { mutableStateOf(false) }
    var showFunctionKeys by remember { mutableStateOf(false) }
    var showFKeys by remember { mutableStateOf(false) }
    var showOtherKeys by remember { mutableStateOf(false) }

    // 傳送組合鍵
    fun sendCombination() {
        val text = textInput.text.replace("\u0000", "")  // 移除空字元

        if (text.isNotEmpty()) {
            // 傳送每個字元作為組合鍵
            for (char in text) {
                if (char == '\n') {
                    if (modifierKeys.isEmpty()) {
                        keyboardController.enter()
                    } else {
                        keyboardController.press("enter", modifierKeys.toList())
                    }
                } else {
                    keyboardController.press(char.toString(), modifierKeys.toList())
                }
            }
            // 清除
            isResetting = true
            textInput = TextFieldValue(
                text = placeholderText,
                selection = TextRange(placeholderText.length)
            )
            previousText = placeholderText
            modifierKeys = setOf()
            isResetting = false
        }
    }

    // ✅ 監聽文字變化並即時傳送（模仿原廠 TextWatcher.onTextChanged）
    LaunchedEffect(textInput.text) {
        if (isResetting) return@LaunchedEffect  // 重置時不處理

        val currentText = textInput.text

        // ✅ 原廠邏輯：文字長度為 0 時視為按刪除鍵
        if (currentText.isEmpty()) {
            ConnectionLogger.log("空輸入框刪除", ConnectionLogger.LogLevel.DEBUG)
            keyboardController.backspace()

            // ✅ 重置為預填充狀態（模仿原廠 removeTextChangedListener + setText）
            isResetting = true
            textInput = TextFieldValue(
                text = placeholderText,
                selection = TextRange(placeholderText.length)
            )
            previousText = placeholderText
            isResetting = false
            return@LaunchedEffect
        }

        if (modifierKeys.isNotEmpty()) {
            previousText = currentText
            return@LaunchedEffect
        }

        // 計算差異
        val minLen = minOf(currentText.length, previousText.length)
        var commonPrefix = 0
        for (i in 0 until minLen) {
            if (currentText[i] == previousText[i]) {
                commonPrefix++
            } else {
                break
            }
        }

        val deletedCount = previousText.length - commonPrefix
        val addedText = currentText.substring(commonPrefix)

        // 處理刪除
        if (deletedCount > 0) {
            ConnectionLogger.log("刪除 $deletedCount 個字元", ConnectionLogger.LogLevel.DEBUG)
            repeat(deletedCount) {
                keyboardController.backspace()
            }
        }

        // 處理新增的文字
        for (char in addedText) {
            if (char == '\n') {
                keyboardController.enter()
            } else if (char != '\u0000') {
                keyboardController.type(char.toString())
            }
        }

        previousText = currentText
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 標題
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("輸入面板", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // BLE 文字輸入模式切換（僅 BLE 模式且為原廠硬體時顯示）
        if (keyboardController is BleKeyboardControllerAdapter) {
            val bleKeyboardController = keyboardController.bleKeyboardController

            // 使用 remember + mutableStateOf 追蹤模式變化，確保 UI 更新
            var currentMode by remember { mutableStateOf(bleKeyboardController.getTextInputMode()) }

            val modeText = when (currentMode) {
                com.unifiedremote.evo.network.ble.BleTextInputMode.BIG5_ALT_CODE -> "Big5 Alt 碼"
                com.unifiedremote.evo.network.ble.BleTextInputMode.ALT_CODE -> "Alt+X Unicode"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("輸入模式：", style = MaterialTheme.typography.labelMedium)
                Button(
                    onClick = {
                        // 切換模式
                        val newMode = when (currentMode) {
                            com.unifiedremote.evo.network.ble.BleTextInputMode.BIG5_ALT_CODE ->
                                com.unifiedremote.evo.network.ble.BleTextInputMode.ALT_CODE
                            com.unifiedremote.evo.network.ble.BleTextInputMode.ALT_CODE ->
                                com.unifiedremote.evo.network.ble.BleTextInputMode.BIG5_ALT_CODE
                        }
                        // 更新控制器狀態
                        bleKeyboardController.setTextInputMode(newMode)
                        // 更新 UI 狀態（觸發 recompose）
                        currentMode = newMode
                    },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(modeText, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 修飾鍵選擇器
        Text("修飾鍵", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ctrl", "shift", "alt", "win").forEach { mod ->
                FilterChip(
                    selected = modifierKeys.contains(mod),
                    onClick = {
                        modifierKeys = if (modifierKeys.contains(mod)) {
                            modifierKeys - mod
                        } else {
                            modifierKeys + mod
                        }
                    },
                    label = { Text(mod.uppercase()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ✅ 文字輸入框（使用 Compose OutlinedTextField + visualTransformation 隱藏空字元）
        OutlinedTextField(
            value = textInput,
            onValueChange = { newValue ->
                if (!isResetting) {
                    textInput = newValue
                }
            },
            label = { Text(if (modifierKeys.isEmpty()) "文字輸入（即時傳送）" else "文字輸入") },
            maxLines = 3,
            visualTransformation = { text ->
                // 過濾掉空字元，讓使用者看到的是乾淨的輸入框
                val filtered = text.text.replace("\u0000", "")
                TransformedText(
                    AnnotatedString(filtered),
                    // ✅ 使用自訂 OffsetMapping 處理空字元過濾（避免 IndexOutOfBoundsException）
                    NullCharFilterOffsetMapping(text.text)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        // 傳送按鈕（當有修飾鍵時顯示）
        if (modifierKeys.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { sendCombination() },
                modifier = Modifier.fillMaxWidth()
            ) {
                val text = textInput.text.replace("\u0000", "")
                val preview = buildString {
                    modifierKeys.forEach { append(it.uppercase()).append(" + ") }
                    append(if (text.isEmpty()) "..." else text)
                }
                Text("傳送: $preview")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 可摺疊區塊：方向鍵
        ExpandableSection(
            title = "方向鍵",
            expanded = showDirectionKeys,
            onToggle = { showDirectionKeys = !showDirectionKeys }
        ) {
            DirectionKeysContent(keyboardController)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 可摺疊區塊：功能鍵
        ExpandableSection(
            title = "功能鍵",
            expanded = showFunctionKeys,
            onToggle = { showFunctionKeys = !showFunctionKeys }
        ) {
            FunctionKeysContent(keyboardController)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 可摺疊區塊：F1-F12
        ExpandableSection(
            title = "F 鍵",
            expanded = showFKeys,
            onToggle = { showFKeys = !showFKeys }
        ) {
            FKeysContent(keyboardController)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 可摺疊區塊：其他按鍵
        ExpandableSection(
            title = "其他按鍵",
            expanded = showOtherKeys,
            onToggle = { showOtherKeys = !showOtherKeys }
        ) {
            OtherKeysContent(keyboardController)
        }

        Spacer(modifier = Modifier.height(80.dp)) // 為軟體鍵盤留空間
    }
}

// 可摺疊區塊組件
@Composable
fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Surface(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(if (expanded) "▲" else "▼")
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

// 方向鍵內容
@Composable
fun DirectionKeysContent(keyboardController: KeyboardController) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 上鍵
        Button(
            onClick = { keyboardController.press("up", emptyList()) },
            modifier = Modifier.size(60.dp)
        ) {
            Text("↑", style = MaterialTheme.typography.titleLarge)
        }
        
        // 中間一排：左、空白、右
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { keyboardController.press("left", emptyList()) },
                modifier = Modifier.size(60.dp)
            ) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
            
            // 中間空白區域，保持十字型布局
            Spacer(modifier = Modifier.size(60.dp))
            
            Button(
                onClick = { keyboardController.press("right", emptyList()) },
                modifier = Modifier.size(60.dp)
            ) {
                Text("→", style = MaterialTheme.typography.titleLarge)
            }
        }
        
        // 下鍵
        Button(
            onClick = { keyboardController.press("down", emptyList()) },
            modifier = Modifier.size(60.dp)
        ) {
            Text("↓", style = MaterialTheme.typography.titleLarge)
        }
    }
}

// 功能鍵內容
@Composable
fun FunctionKeysContent(keyboardController: KeyboardController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { keyboardController.enter() },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Enter")
            }
            Button(
                onClick = { keyboardController.backspace() },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Back")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { keyboardController.press("tab", emptyList()) },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Tab")
            }
            Button(
                onClick = { keyboardController.press("escape", emptyList()) },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Esc")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { keyboardController.press("delete", emptyList()) },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Del")
            }
            Button(
                onClick = { keyboardController.press("space", emptyList()) },
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Space")
            }
        }
    }
}

// F 鍵內容
@Composable
fun FKeysContent(keyboardController: KeyboardController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 1..4) {
                Button(
                    onClick = { keyboardController.press("f$i", emptyList()) },
                    modifier = Modifier.weight(1f).height(45.dp)
                ) {
                    Text("F$i")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 5..8) {
                Button(
                    onClick = { keyboardController.press("f$i", emptyList()) },
                    modifier = Modifier.weight(1f).height(45.dp)
                ) {
                    Text("F$i")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 9..12) {
                Button(
                    onClick = { keyboardController.press("f$i", emptyList()) },
                    modifier = Modifier.weight(1f).height(45.dp)
                ) {
                    Text("F$i")
                }
            }
        }
    }
}

// 其他按鍵內容
@Composable
fun OtherKeysContent(keyboardController: KeyboardController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { keyboardController.press("home", emptyList()) },
                modifier = Modifier.weight(1f).height(45.dp)
            ) {
                Text("Home")
            }
            Button(
                onClick = { keyboardController.press("end", emptyList()) },
                modifier = Modifier.weight(1f).height(45.dp)
            ) {
                Text("End")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { keyboardController.press("prior", emptyList()) },
                modifier = Modifier.weight(1f).height(45.dp)
            ) {
                Text("PgUp")
            }
            Button(
                onClick = { keyboardController.press("next", emptyList()) },
                modifier = Modifier.weight(1f).height(45.dp)
            ) {
                Text("PgDn")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { keyboardController.press("insert", emptyList()) },
                modifier = Modifier.weight(1f).height(45.dp)
            ) {
                Text("Insert")
            }
        }
    }
}

// 快捷鍵彈出式視窗
@Composable
fun ShortcutsDialogContent(
    keyboardController: KeyboardController,
    onDismiss: () -> Unit
) {
    // 添加日誌以調試UI佈局問題
    ConnectionLogger.log("快捷鍵對話框初始化", ConnectionLogger.LogLevel.DEBUG)
    
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp) // 增加水平外邊距
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp) // 增加內邊距
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("快捷鍵", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) {
                    Text("關閉")
                }
            }

            Spacer(modifier = Modifier.height(20.dp)) // 增加標題下方間距

            // 編輯快捷鍵
            Text("編輯", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(12.dp)) // 增加標籤下方間距
            
            // 第一行按鈕 - 複製、剪下、貼上
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp) // 增加按鈕間距
            ) {
                Button(
                    onClick = { keyboardController.ctrlC() },
                    modifier = Modifier.weight(1f).height(56.dp) // 增加按鈕高度
                ) {
                    Text("複製", style = MaterialTheme.typography.bodyMedium) // 調整文字大小
                }
                Button(
                    onClick = { keyboardController.ctrlX() },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("剪下", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { keyboardController.ctrlV() },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("貼上", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp)) // 增加行間距
            
            // 第二行按鈕 - 復原、全選
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { keyboardController.ctrlZ() },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("復原", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { keyboardController.ctrlA() },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("全選", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp)) // 增加區塊間距

            // 系統快捷鍵
            Text("系統", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(12.dp))
            
            // 第一行系統按鈕 - 儲存、尋找
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { keyboardController.press("s", listOf("ctrl")) },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("儲存", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { keyboardController.press("f", listOf("ctrl")) },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("尋找", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 第二行系統按鈕 - 關閉、Alt+Tab
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { keyboardController.press("w", listOf("ctrl")) },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("關閉", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { keyboardController.press("tab", listOf("alt")) },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("Alt+Tab", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 第三行系統按鈕 - Alt+F4 (居中)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center // 居中單個按鈕
            ) {
                Button(
                    onClick = { keyboardController.press("f4", listOf("alt")) },
                    modifier = Modifier
                        .width(200.dp) // 固定寬度而不是weight
                        .height(56.dp)
                ) {
                    Text("Alt+F4", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp)) // 底部額外間距
        }
    }
}

// 硬體型號指示器（顯示當前硬體與輸入模式）
@Composable
fun HardwareIndicator(
    hardwareType: com.unifiedremote.evo.network.ble.EmulStickHardware,
    modifier: Modifier = Modifier
) {
    val (icon, text, color) = when (hardwareType) {
        com.unifiedremote.evo.network.ble.EmulStickHardware.ESP32S3_EVO ->
            Triple(Icons.Filled.Rocket, "Evo 高速模式", androidx.compose.ui.graphics.Color.Green)
        com.unifiedremote.evo.network.ble.EmulStickHardware.ORIGINAL_TI ->
            Triple(Icons.Filled.Router, "原廠 TI", androidx.compose.ui.graphics.Color.Blue)
        com.unifiedremote.evo.network.ble.EmulStickHardware.ORIGINAL_WCH ->
            Triple(Icons.Filled.Router, "原廠 WCH", androidx.compose.ui.graphics.Color.Blue)
        com.unifiedremote.evo.network.ble.EmulStickHardware.UNKNOWN ->
            Triple(Icons.Filled.Warning, "相容模式", androidx.compose.ui.graphics.Color.Gray)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}

// 裝置切換彈出式視窗
@Composable
fun DeviceSwitcherDialog(
    savedDevices: List<SavedDevice>,
    currentDeviceId: String?,
    onSelectDevice: (SavedDevice) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .widthIn(max = 400.dp)
            .heightIn(max = 600.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 標題列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "切換裝置",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onDismiss) {
                    Text("關閉")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 裝置列表
            if (savedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "尚無儲存的裝置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    savedDevices.forEach { device ->
                        val isCurrentDevice = device.id == currentDeviceId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = if (isCurrentDevice) {
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else {
                                CardDefaults.cardColors()
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isCurrentDevice) {
                                        onSelectDevice(device)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 裝置類型圖示
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(end = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (device.type) {
                                        com.unifiedremote.evo.network.ConnectionType.TCP -> {
                                            Icon(
                                                imageVector = Icons.Filled.Wifi,
                                                contentDescription = "WiFi/TCP",
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        com.unifiedremote.evo.network.ConnectionType.BLUETOOTH -> {
                                            Icon(
                                                imageVector = Icons.Filled.Bluetooth,
                                                contentDescription = "Bluetooth",
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        com.unifiedremote.evo.network.ConnectionType.BLE_EMULSTICK -> {
                                            Icon(
                                                imageVector = Icons.Filled.Usb,
                                                contentDescription = "USB Dongle",
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }

                                // 裝置資訊
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isCurrentDevice) {
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                            } else {
                                                androidx.compose.ui.text.font.FontWeight.Normal
                                            }
                                        )
                                        if (isCurrentDevice) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "當前裝置",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = device.getSubtitle(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when (device.type) {
                                            com.unifiedremote.evo.network.ConnectionType.TCP -> "WiFi/Tailscale"
                                            com.unifiedremote.evo.network.ConnectionType.BLUETOOTH -> "傳統藍牙"
                                            com.unifiedremote.evo.network.ConnectionType.BLE_EMULSTICK -> "BLE EmulStick"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
