package com.unifiedremote.evo.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * 連線日誌系統（Reactive 版本）
 */
object ConnectionLogger {

    private val logs = mutableListOf<LogEntry>()
    private const val MAX_LOGS = 500
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // SharedPreferences
    private var prefs: SharedPreferences? = null
    private const val PREF_NAME = "connection_logger"
    private const val KEY_MIN_LOG_LEVEL = "min_log_level"

    // Reactive State Flow - UI 可以訂閱並自動更新
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    // 目前的日誌等級過濾器
    private val _minLogLevel = MutableStateFlow(LogLevel.DEBUG)
    val minLogLevel: StateFlow<LogLevel> = _minLogLevel.asStateFlow()

    /**
     * 初始化 Logger（必須在 Application 或 Activity 中呼叫）
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // 載入儲存的日誌等級
        val savedLevel = prefs?.getInt(KEY_MIN_LOG_LEVEL, LogLevel.DEBUG.priority) ?: LogLevel.DEBUG.priority
        _minLogLevel.value = LogLevel.fromPriority(savedLevel)
    }

    data class LogEntry(
        val timestamp: Long,
        val message: String,
        val level: LogLevel
    )

    enum class LogLevel(val priority: Int, val displayName: String) {
        DEBUG(0, "偵錯"),
        INFO(1, "資訊"),
        WARNING(2, "警告"),
        ERROR(3, "錯誤");

        companion object {
            fun fromPriority(priority: Int): LogLevel =
                values().find { it.priority == priority } ?: DEBUG
        }
    }

    @Synchronized
    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        // 檢查是否應該記錄此日誌（根據最小等級過濾）
        if (level.priority < _minLogLevel.value.priority) {
            return
        }

        val entry = LogEntry(System.currentTimeMillis(), message, level)
        logs.add(entry)

        if (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }

        // 更新 StateFlow（觸發 UI 重組）
        _logsFlow.value = getFilteredLogs()

        val prefix = when (level) {
            LogLevel.DEBUG -> "[DEBUG]"
            LogLevel.INFO -> "[INFO]"
            LogLevel.WARNING -> "[WARN]"
            LogLevel.ERROR -> "[ERROR]"
        }
        println("$prefix ${formatTime(entry.timestamp)} $message")
    }

    /**
     * 設定最小日誌等級（低於此等級的日誌將不會顯示）
     */
    @Synchronized
    fun setMinLogLevel(level: LogLevel) {
        _minLogLevel.value = level
        // 儲存到 SharedPreferences
        prefs?.edit()?.putInt(KEY_MIN_LOG_LEVEL, level.priority)?.apply()
        // 重新過濾日誌
        _logsFlow.value = getFilteredLogs()
    }

    /**
     * 取得過濾後的日誌
     */
    @Synchronized
    private fun getFilteredLogs(): List<LogEntry> {
        return logs.filter { it.level.priority >= _minLogLevel.value.priority }
    }

    @Synchronized
    fun getFormattedLogs(): String {
        return logs.joinToString("\n") { entry ->
            val level = when (entry.level) {
                LogLevel.DEBUG -> "DEBUG"
                LogLevel.INFO -> "INFO "
                LogLevel.WARNING -> "WARN "
                LogLevel.ERROR -> "ERROR"
            }
            "[${formatTime(entry.timestamp)}] [$level] ${entry.message}"
        }
    }

    @Synchronized
    fun getLogs(): List<LogEntry> = logs.toList()

    @Synchronized
    fun clear() {
        logs.clear()
        _logsFlow.value = emptyList()
    }

    @Synchronized
    fun getStats(): Stats {
        val filteredLogs = getFilteredLogs()
        val errorCount = filteredLogs.count { it.level == LogLevel.ERROR }
        val warningCount = filteredLogs.count { it.level == LogLevel.WARNING }
        val infoCount = filteredLogs.count { it.level == LogLevel.INFO }
        val debugCount = filteredLogs.count { it.level == LogLevel.DEBUG }
        return Stats(filteredLogs.size, errorCount, warningCount, infoCount, debugCount)
    }

    private fun formatTime(timestamp: Long): String =
        dateFormat.format(Date(timestamp))

    data class Stats(
        val totalLogs: Int,
        val errorCount: Int,
        val warningCount: Int,
        val infoCount: Int,
        val debugCount: Int
    )
}
