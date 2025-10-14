# =============================================================================
# Unified Remote Evo - ProGuard 規則
# =============================================================================
# 用於保護必要的類別和反射功能，同時啟用最大化的程式碼縮減

# =============================================================================
# 1. 保護序列化相關的註解和反射功能
# =============================================================================

# 保留序列化註解本身
-keep @interface com.unifiedremote.evo.network.serialization.BinarySerializable
-keep @interface com.unifiedremote.evo.network.serialization.BinaryField
-keep @interface com.unifiedremote.evo.network.serialization.BinaryIgnore

# 保留所有標記為 @BinarySerializable 的類別及其成員
-keep @com.unifiedremote.evo.network.serialization.BinarySerializable class * {
    *;
}

# 保留所有標記為 @BinaryField 的屬性
-keepclassmembers class * {
    @com.unifiedremote.evo.network.serialization.BinaryField *;
}

# 保留 Kotlin 反射功能（序列化引擎需要）
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# 保留 Kotlin 標準函式庫的反射相關功能
-dontwarn kotlin.reflect.**
-keep class kotlin.reflect.jvm.internal.** { *; }

# =============================================================================
# 2. 保護資料類別（確保序列化/反序列化正常）
# =============================================================================

# 保留所有資料類別的結構
-keepclassmembers class com.unifiedremote.evo.data.** {
    <init>(...);
    <fields>;
}

# 保留 Packet 相關類別
-keep class com.unifiedremote.evo.data.Packet { *; }
-keep class com.unifiedremote.evo.data.Action { *; }
-keep class com.unifiedremote.evo.data.Extras { *; }
-keep class com.unifiedremote.evo.data.Extra { *; }
-keep class com.unifiedremote.evo.data.Capabilities { *; }
-keep class com.unifiedremote.evo.data.Layout { *; }
-keep class com.unifiedremote.evo.data.Control { *; }

# =============================================================================
# 3. 保護 Gson（用於設定儲存）
# =============================================================================

# Gson 特定規則
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# 保留 Gson 序列化的類別
-keep class com.unifiedremote.evo.data.SavedDevice { *; }
-keep class com.unifiedremote.evo.data.SensitivitySettings { *; }
-keep class com.unifiedremote.evo.data.ThemeMode { *; }

# =============================================================================
# 4. 保護 Jetpack Compose 和 Material 3
# =============================================================================

# Compose 特定規則
-keep class androidx.compose.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Material 3 元件
-keep class androidx.compose.material3.** { *; }

# =============================================================================
# 5. 保護藍牙和 BLE 相關類別
# =============================================================================

# 保留 BLE 控制器
-keep class com.unifiedremote.evo.controller.BleMouseController { *; }
-keep class com.unifiedremote.evo.controller.BleKeyboardController { *; }
-keep class com.unifiedremote.evo.controller.BleXInputController { *; }

# 保留 BLE 管理器
-keep class com.unifiedremote.evo.network.ble.BleManager { *; }
-keep class com.unifiedremote.evo.network.ble.BleConnectionState { *; }

# =============================================================================
# 6. 保護網路層和連線管理
# =============================================================================

# 保留連線管理器
-keep class com.unifiedremote.evo.network.tcp.ConnectionManager { *; }
-keep class com.unifiedremote.evo.network.bluetooth.BluetoothConnectionManager { *; }

# 保留序列化器
-keep class com.unifiedremote.evo.network.serialization.BinarySerializer { *; }

# =============================================================================
# 7. 一般性保護規則
# =============================================================================

# 保留所有原生方法（JNI）
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留自訂 View 的建構子
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留 Activity 的生命週期方法
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# 保留列舉類別
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable 實作
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# =============================================================================
# 8. 最佳化選項
# =============================================================================

# 啟用最佳化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# 保留行號資訊（方便除錯 crash）
-keepattributes SourceFile,LineNumberTable

# 保留泛型簽名
-keepattributes Signature

# 保留註解
-keepattributes *Annotation*

# 保留 InnerClasses 屬性
-keepattributes InnerClasses

# 保留加密相關屬性
-keepattributes EnclosingMethod

# =============================================================================
# 9. 移除日誌輸出（Release 版本）
# =============================================================================

# 移除 Log.v/Log.d/Log.i，保留 Log.w/Log.e
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# =============================================================================
# 10. 警告壓制
# =============================================================================

# 壓制反射相關警告
-dontwarn kotlin.reflect.**
-dontwarn java.lang.invoke.StringConcatFactory

# 壓制協程相關警告
-dontwarn kotlinx.coroutines.**
