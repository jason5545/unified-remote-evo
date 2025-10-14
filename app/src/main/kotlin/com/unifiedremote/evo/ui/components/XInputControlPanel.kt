package com.unifiedremote.evo.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.unifiedremote.evo.network.ble.BleXInputController
import com.unifiedremote.evo.network.ble.DPadDirection
import com.unifiedremote.evo.network.ble.XInputButton
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * XInput 控制面板（Xbox 360 虛擬手把）
 *
 * 提供完整的 Xbox 360 控制器 UI，包含：
 * - 雙搖桿（左/右）
 * - 按鈕面板（A/B/X/Y）
 * - 肩鈕與扳機（LB/RB/LT/RT）
 * - D-Pad（方向鍵）
 * - 系統按鈕（Start/Back/L3/R3）
 *
 * @param xInputController XInput 控制器實例
 * @param onBack 返回組合模式的回呼
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XInputControlPanel(
    xInputController: BleXInputController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            // 橫向佈局：左側雙搖桿，右側按鈕/扳機/D-Pad
            LandscapeLayout(xInputController = xInputController)
        } else {
            // 直向佈局：上方雙搖桿，下方按鈕/扳機/D-Pad
            PortraitLayout(xInputController = xInputController)
        }

        // 浮動選單按鈕（右下角）
        FloatingActionButton(
            onClick = { showMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "選單"
            )
        }

        // 選單 BottomSheet
        if (showMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMenu = false }
            ) {
                XInputMenuContent(
                    onBack = {
                        showMenu = false
                        onBack()
                    },
                    onDismiss = { showMenu = false }
                )
            }
        }
    }
}

/**
 * XInput 選單內容
 */
@Composable
private fun XInputMenuContent(
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
    ) {
        // 返回組合模式（主要功能）
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("← 返回組合模式", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 關閉選單按鈕
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("關閉", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ============ 橫向佈局 ============

@Composable
private fun LandscapeLayout(xInputController: BleXInputController) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左側：雙搖桿
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 左搖桿
            VirtualJoystick(
                label = "左搖桿",
                onMove = { x, y ->
                    kotlinx.coroutines.GlobalScope.launch {
                        xInputController.setLeftStick(x, y)
                    }
                },
                onRelease = {
                    kotlinx.coroutines.GlobalScope.launch {
                        xInputController.setLeftStick(0f, 0f)
                    }
                },
                modifier = Modifier.size(140.dp)
            )

            // 右搖桿
            VirtualJoystick(
                label = "右搖桿",
                onMove = { x, y ->
                    kotlinx.coroutines.GlobalScope.launch {
                        xInputController.setRightStick(x, y)
                    }
                },
                onRelease = {
                    kotlinx.coroutines.GlobalScope.launch {
                        xInputController.setRightStick(0f, 0f)
                    }
                },
                modifier = Modifier.size(140.dp)
            )
        }

        // 右側：按鈕、扳機、D-Pad
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 扳機滑桿
            TriggerControls(xInputController = xInputController)

            Spacer(modifier = Modifier.height(8.dp))

            // 按鈕面板與 D-Pad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // D-Pad（左側）
                DPadControl(xInputController = xInputController)

                // 按鈕面板（右側）
                ButtonPanel(xInputController = xInputController)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 系統按鈕（Start/Back）
            SystemButtons(xInputController = xInputController)
        }
    }
}

// ============ 直向佈局 ============

@Composable
private fun PortraitLayout(xInputController: BleXInputController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上方：雙搖桿
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 左搖桿
            VirtualJoystick(
                label = "左搖桿",
                onMove = { x, y ->
                    kotlinx.coroutines.GlobalScope.launch {
                        xInputController.setLeftStick(x, y)
                    }
                },
                onRelease = {
                    kotlinx.coroutines.GlobalScope.launch {
                        xInputController.setLeftStick(0f, 0f)
                    }
                },
                modifier = Modifier.size(130.dp)
            )

            // 右搖桿
            VirtualJoystick(
                label = "右搖桿",
                onMove = { x, y ->
                    kotlinx.coroutines.GlobalScope.launch {
                        xInputController.setRightStick(x, y)
                    }
                },
                onRelease = {
                    kotlinx.coroutines.GlobalScope.launch {
                        xInputController.setRightStick(0f, 0f)
                    }
                },
                modifier = Modifier.size(130.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 下方：扳機、按鈕、D-Pad
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 扳機滑桿
            TriggerControls(xInputController = xInputController)

            // 按鈕面板與 D-Pad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // D-Pad（左側）
                DPadControl(xInputController = xInputController)

                // 按鈕面板（右側）
                ButtonPanel(xInputController = xInputController)
            }

            // 系統按鈕
            SystemButtons(xInputController = xInputController)
        }
    }
}

// ============ 虛擬搖桿元件 ============

@Composable
private fun VirtualJoystick(
    label: String,
    onMove: (x: Float, y: Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            offset = Offset.Zero
                            onRelease()
                        },
                        onDragCancel = {
                            offset = Offset.Zero
                            onRelease()
                        }
                    ) { change, dragAmount ->
                        change.consume()

                        // 更新偏移量
                        val newOffset = offset + dragAmount

                        // 限制在圓形區域內（半徑為尺寸的 40%）
                        val maxRadius = size.width * 0.4f
                        val distance = sqrt(newOffset.x.pow(2) + newOffset.y.pow(2))

                        offset = if (distance > maxRadius) {
                            val scale = maxRadius / distance
                            Offset(newOffset.x * scale, newOffset.y * scale)
                        } else {
                            newOffset
                        }

                        // 轉換為 -1.0 ~ 1.0 的座標
                        val normalizedX = (offset.x / maxRadius).coerceIn(-1f, 1f)
                        val normalizedY = -(offset.y / maxRadius).coerceIn(-1f, 1f)  // Y 軸反向

                        onMove(normalizedX, normalizedY)
                    }
                }
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val baseRadius = size.width * 0.45f
            val stickRadius = size.width * 0.2f

            // 繪製底座（外圓）
            drawCircle(
                color = Color.Gray.copy(alpha = 0.3f),
                radius = baseRadius,
                center = Offset(centerX, centerY)
            )

            // 繪製搖桿（內圓，會移動）
            drawCircle(
                color = Color.Blue.copy(alpha = 0.7f),
                radius = stickRadius,
                center = Offset(centerX + offset.x, centerY + offset.y)
            )
        }

        // 標籤（底部）
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ============ 按鈕面板（A/B/X/Y + 肩鈕） ============

@Composable
private fun ButtonPanel(xInputController: BleXInputController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // LB/RB 肩鈕
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            XInputButton(
                label = "LB",
                color = Color.Gray,
                button = XInputButton.LB,
                controller = xInputController,
                modifier = Modifier.size(width = 60.dp, height = 40.dp)
            )
            XInputButton(
                label = "RB",
                color = Color.Gray,
                button = XInputButton.RB,
                controller = xInputController,
                modifier = Modifier.size(width = 60.dp, height = 40.dp)
            )
        }

        // Y 鈕（上）
        XInputButton(
            label = "Y",
            color = Color(0xFFFFD700),  // 黃色
            button = XInputButton.Y,
            controller = xInputController,
            modifier = Modifier.size(50.dp)
        )

        // X 和 B 鈕（左右）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XInputButton(
                label = "X",
                color = Color(0xFF0080FF),  // 藍色
                button = XInputButton.X,
                controller = xInputController,
                modifier = Modifier.size(50.dp)
            )
            XInputButton(
                label = "B",
                color = Color(0xFFFF4444),  // 紅色
                button = XInputButton.B,
                controller = xInputController,
                modifier = Modifier.size(50.dp)
            )
        }

        // A 鈕（下）
        XInputButton(
            label = "A",
            color = Color(0xFF44FF44),  // 綠色
            button = XInputButton.A,
            controller = xInputController,
            modifier = Modifier.size(50.dp)
        )
    }
}

// ============ 單個按鈕元件 ============

@Composable
private fun XInputButton(
    label: String,
    color: Color,
    button: XInputButton,
    controller: BleXInputController,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = { /* 按鍵由 pointerInput 處理 */ },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPressed) color.copy(alpha = 1f) else color.copy(alpha = 0.6f)
        ),
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    isPressed = true
                    coroutineScope.launch {
                        controller.pressButton(button)
                    }
                },
                onDragEnd = {
                    isPressed = false
                    coroutineScope.launch {
                        controller.releaseButton(button)
                    }
                },
                onDragCancel = {
                    isPressed = false
                    coroutineScope.launch {
                        controller.releaseButton(button)
                    }
                }
            ) { _, _ -> }
        }
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

// ============ D-Pad 控制（方向鍵） ============

@Composable
private fun DPadControl(xInputController: BleXInputController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 上
        DPadButton(
            label = "↑",
            direction = DPadDirection.UP,
            controller = xInputController,
            modifier = Modifier.size(50.dp)
        )

        // 左、中、右
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DPadButton(
                label = "←",
                direction = DPadDirection.LEFT,
                controller = xInputController,
                modifier = Modifier.size(50.dp)
            )
            DPadButton(
                label = "○",
                direction = DPadDirection.CENTER,
                controller = xInputController,
                modifier = Modifier.size(50.dp)
            )
            DPadButton(
                label = "→",
                direction = DPadDirection.RIGHT,
                controller = xInputController,
                modifier = Modifier.size(50.dp)
            )
        }

        // 下
        DPadButton(
            label = "↓",
            direction = DPadDirection.DOWN,
            controller = xInputController,
            modifier = Modifier.size(50.dp)
        )
    }
}

@Composable
private fun DPadButton(
    label: String,
    direction: DPadDirection,
    controller: BleXInputController,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = { /* 按鍵由 pointerInput 處理 */ },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        ),
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    isPressed = true
                    coroutineScope.launch {
                        controller.setDPad(direction)
                    }
                },
                onDragEnd = {
                    isPressed = false
                    coroutineScope.launch {
                        controller.setDPad(DPadDirection.CENTER)
                    }
                },
                onDragCancel = {
                    isPressed = false
                    coroutineScope.launch {
                        controller.setDPad(DPadDirection.CENTER)
                    }
                }
            ) { _, _ -> }
        }
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

// ============ 扳機控制（LT/RT 滑桿） ============

@Composable
private fun TriggerControls(xInputController: BleXInputController) {
    var leftTrigger by remember { mutableStateOf(0f) }
    var rightTrigger by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 左扳機（LT）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(80.dp)
        ) {
            Text("LT", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = leftTrigger,
                onValueChange = {
                    leftTrigger = it
                    coroutineScope.launch {
                        xInputController.setTriggers(leftTrigger, rightTrigger)
                    }
                },
                valueRange = 0f..1f,
                modifier = Modifier.height(120.dp)
            )
        }

        // 右扳機（RT）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(80.dp)
        ) {
            Text("RT", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = rightTrigger,
                onValueChange = {
                    rightTrigger = it
                    coroutineScope.launch {
                        xInputController.setTriggers(leftTrigger, rightTrigger)
                    }
                },
                valueRange = 0f..1f,
                modifier = Modifier.height(120.dp)
            )
        }
    }
}

// ============ 系統按鈕（Start/Back/L3/R3） ============

@Composable
private fun SystemButtons(xInputController: BleXInputController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        XInputButton(
            label = "Back",
            color = Color.DarkGray,
            button = XInputButton.BACK,
            controller = xInputController,
            modifier = Modifier.size(width = 70.dp, height = 45.dp)
        )
        XInputButton(
            label = "Start",
            color = Color.DarkGray,
            button = XInputButton.START,
            controller = xInputController,
            modifier = Modifier.size(width = 70.dp, height = 45.dp)
        )
        XInputButton(
            label = "L3",
            color = Color.Gray,
            button = XInputButton.L3,
            controller = xInputController,
            modifier = Modifier.size(width = 60.dp, height = 45.dp)
        )
        XInputButton(
            label = "R3",
            color = Color.Gray,
            button = XInputButton.R3,
            controller = xInputController,
            modifier = Modifier.size(width = 60.dp, height = 45.dp)
        )
    }
}
