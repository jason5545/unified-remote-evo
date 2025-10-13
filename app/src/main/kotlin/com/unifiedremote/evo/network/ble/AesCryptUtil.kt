package com.unifiedremote.evo.network.ble

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES 加密工具類別
 *
 * 完全複製 EmulStick 原廠 APP 的加密邏輯
 * 參考：apk_analysis/emulstick_decompiled/.../utils/AesCryptUtil.java
 */
object AesCryptUtil {

    private const val AES_MODE = "AES/CBC/PKCS5Padding"  // Android 使用 PKCS5（等同 PKCS7）
    private const val HASH_ALGORITHM = "SHA-256"
    private const val CHARSET = "UTF-8"
    private val IV_BYTES = ByteArray(16) { 0 }  // 全零 IV

    /**
     * 生成 AES 密鑰
     *
     * @param password 密碼字串（例如：System ID 的 16 進位字串）
     * @return AES-256 密鑰
     */
    private fun generateKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        digest.update(passwordBytes)
        return SecretKeySpec(digest.digest(), "AES")
    }

    /**
     * AES 加密
     *
     * @param password 密碼字串（System ID 的 16 進位字串）
     * @param message 要加密的明文（plainMap 中的密碼）
     * @return Base64 編碼的加密結果
     */
    fun encrypt(password: String, message: String): String {
        // 1. 生成密鑰
        val key = generateKey(password)

        // 2. AES 加密
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(IV_BYTES))
        val encrypted = cipher.doFinal(message.toByteArray(Charsets.UTF_8))

        // 3. Base64 編碼
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * 將 ByteArray 轉換為 16 進位字串
     *
     * @param byteArray 位元組陣列（例如：System ID）
     * @return 16 進位字串（例如："F4B89831512C8AABB"）
     */
    fun byteArrayToHexString(byteArray: ByteArray): String {
        val hexChars = "0123456789ABCDEF".toCharArray()
        val result = CharArray(byteArray.size * 2)

        for (i in byteArray.indices) {
            val byte = byteArray[i].toInt()
            result[i * 2] = hexChars[(byte ushr 4) and 0x0F]       // 高 4 位
            result[i * 2 + 1] = hexChars[byte and 0x0F]            // 低 4 位
        }

        return String(result)
    }
}
