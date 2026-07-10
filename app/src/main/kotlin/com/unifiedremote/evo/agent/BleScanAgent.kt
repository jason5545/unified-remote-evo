@file:Suppress("MissingPermission")

package com.unifiedremote.evo.agent

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.ParcelUuid
import java.util.*
import android.util.SparseArray

/**
 * BLE 掃描診斷與穩定掃描代理
 *
 * 用途：
 * 1. 診斷掃描（startDiagnosticScan）：找出根因（權限、位置服務、節流、名稱來源等）
 * 2. 兩段式掃描（startTwoPhaseScan）：先 UUID 再無過濾，兼顧速度與命中率
 */
object BleScanAgent {
    private const val TAG = "BleScanAgent"

    // === 公用：權限/定位狀態 ===
    fun hasAllBlePerms(ctx: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isLocationEnabled(ctx: Context): Boolean = try {
        val lm = ctx.getSystemService(LocationManager::class.java)
        lm != null && LocationManagerCompat.isLocationEnabled(lm)
    } catch (_: Throwable) { false }

    // === 診斷掃描：無過濾器，強化日誌，長時間 ===
    // 用途：快速判斷 callback 是否有進來、名稱是否在 scanRecord、是否被節流/失敗、是否受位置服務限制
    fun startDiagnosticScan(
        context: Context,
        adapter: BluetoothAdapter,
        durationMs: Long = 8_000,
        nameKeyword: String = "emulstick",
        onHit: (ScanResult) -> Unit = {},
        onComplete: (List<ScanResult>) -> Unit = {}
    ) {
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "❌ scanner=null (adapterEnabled=${adapter.isEnabled})")
            onComplete(emptyList())
            return
        }

        Log.i(TAG, "🔍 startDiagnosticScan: btOn=${adapter.isEnabled}, perms=${hasAllBlePerms(context)}, locOn=${isLocationEnabled(context)}")

        val results = mutableListOf<ScanResult>()
        val stopAt = SystemClock.elapsedRealtime() + durationMs

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val advName = r.scanRecord?.deviceName
                val devName = r.device?.name
                val name = advName ?: devName
                if (results.none { it.device?.address == r.device?.address }) {
                    results += r
                }
                Log.d(
                    TAG,
                    "📡 hit name=${name ?: "(null)"} addr=${r.device?.address} rssi=${r.rssi} " +
                    "uuids=${r.scanRecord?.serviceUuids?.joinToString { it.uuid.toString() } ?: "[]"} " +
                    "mfg=${r.scanRecord?.manufacturerSpecificData?.toReadable() ?: "(none)"}"
                )

                if ((name ?: "").contains(nameKeyword, ignoreCase = true)) {
                    Log.i(TAG, "✅ 命中 $nameKeyword : $name @ ${r.device?.address}")
                    onHit(r)
                }

                if (SystemClock.elapsedRealtime() >= stopAt) {
                    Log.i(TAG, "⏱️ 停止診斷掃描（${durationMs}ms）")
                    try { scanner.stopScan(this) } catch (_: Throwable) {}
                    onComplete(results)
                }
            }

            override fun onBatchScanResults(batch: MutableList<ScanResult>) {
                Log.w(TAG, "📦 onBatchScanResults(${batch.size})")
                batch.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "❌ onScanFailed=${scanFailReason(errorCode)}")
                onComplete(results)
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // 重要：filters 用 null，而不是 emptyList()（部分 OEM 行為不同）
            scanner.startScan(null, settings, cb)
            Log.i(TAG, "✅ startScan() 提交成功（診斷：無過濾器, ${durationMs}ms）")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException: ${e.message}")
            onComplete(results)
        } catch (e: Throwable) {
            Log.e(TAG, "❌ startScan ex: ${e.message}")
            onComplete(results)
        }
    }

    // === 兩段式穩定掃描：先 UUID 再無過濾（總 4s） ===
    // 用途：日常正式使用，兼顧速度與命中率（HOGP 裝置不一定廣播 UUID）
    fun startTwoPhaseScan(
        context: Context,
        adapter: BluetoothAdapter,
        serviceUuid: UUID,
        totalMs: Long = 4_000,
        nameKeyword: String = "emulstick",
        onHit: (ScanResult) -> Unit,
        onComplete: (List<ScanResult>) -> Unit
    ) {
        val scanner = adapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "❌ scanner=null")
            onComplete(emptyList()); return
        }

        val phase1Ms = (totalMs * 0.375).toLong().coerceAtLeast(1000) // ~1.5s for 4s total
        val phase2Ms = totalMs - phase1Ms
        val results = mutableListOf<ScanResult>()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                if (results.none { it.device?.address == r.device?.address }) results += r
                val name = r.scanRecord?.deviceName ?: r.device?.name
                if ((name ?: "").contains(nameKeyword, ignoreCase = true)) onHit(r)
            }
            override fun onBatchScanResults(batch: MutableList<ScanResult>) {
                batch.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "❌ onScanFailed=${scanFailReason(errorCode)}")
                onComplete(results)
            }
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()

        try {
            // Phase #1: UUID 過濾（快且精準）
            Log.i(TAG, "🔄 phase#1 start (UUID=$serviceUuid, ${phase1Ms}ms)")
            scanner.startScan(listOf(filter), settings, cb)
            Thread.sleep(phase1Ms)
            scanner.stopScan(cb)

            // 若未命中，Phase #2：無過濾器（廣度，補救 HOGP 不塞 UUID 的情形）
            if (results.isEmpty()) {
                Log.i(TAG, "🔁 phase#1 no hit → phase#2 start (no filter, ${phase2Ms}ms)")
                scanner.startScan(null, settings, cb)
                Thread.sleep(phase2Ms)
                scanner.stopScan(cb)
            }

            Log.i(TAG, "✅ two-phase complete: found=${results.size}")
            onComplete(results)
        } catch (e: Throwable) {
            try { scanner.stopScan(cb) } catch (_: Throwable) {}
            Log.e(TAG, "❌ two-phase exception: ${e.message}")
            onComplete(results)
        }
    }

    private fun SparseArray<ByteArray>.toReadable(): String =
        (0 until size()).joinToString { i ->
            val k = keyAt(i)
            val v = valueAt(i).joinToString { b -> "%02X".format(b) }
            "0x${k.toString(16)}[$v]"
        }

    private fun scanFailReason(code: Int): String = when (code) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED(1)"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REG_FAILED(2)"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR(3)"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED(4)"
        5 -> "OUT_OF_HARDWARE_RESOURCES(5)"
        6 -> "SCANNING_TOO_FREQUENTLY(6)"
        else -> "UNKNOWN($code)"
    }
}
