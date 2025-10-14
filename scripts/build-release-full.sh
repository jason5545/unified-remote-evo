#!/bin/bash
# Bash 腳本：完整模擬 GitHub Actions Release 流程
# 用法：./scripts/build-release-full.sh

set -e

echo "🚀 完整 Release 建置流程（模擬 GitHub Actions）"
echo "=================================================="
echo ""

# 步驟 1: 檢查環境
echo "📋 步驟 1: 檢查環境"
echo ""

# 檢查 Java/keytool
if [ -z "$JAVA_HOME" ]; then
    echo "❌ 找不到 JAVA_HOME，請設定 JAVA_HOME 環境變數"
    exit 1
fi

echo "✅ JAVA_HOME: $JAVA_HOME"

if ! command -v keytool &> /dev/null; then
    echo "❌ 找不到 keytool"
    exit 1
fi

echo "✅ keytool: $(which keytool)"
echo ""

# 步驟 2: 檢查並產生簽署金鑰
echo "📋 步驟 2: 檢查並產生簽署金鑰"
echo ""

if [ -f "app/release.keystore" ] && [ -f "keystore.properties" ]; then
    echo "✅ 找到現有的簽署金鑰"
    echo ""

    # 讀取現有設定
    source keystore.properties
    STORE_PASS=$storePassword
    KEY_PASS=$keyPassword
    ALIAS=$keyAlias

    echo "使用現有設定："
    echo "  KEY_ALIAS: $ALIAS"
    echo ""

else
    echo "⚠️  未找到簽署金鑰，自動產生新的..."
    echo ""

    # 產生隨機密碼（16 字元）
    STORE_PASS=$(openssl rand -base64 16 | tr -d "=+/" | cut -c1-16)
    KEY_PASS=$(openssl rand -base64 16 | tr -d "=+/" | cut -c1-16)
    ALIAS="unified-remote-evo"

    echo "產生的資訊（請妥善保管）："
    echo "=================================="
    echo "KEY_ALIAS=$ALIAS"
    echo "KEYSTORE_PASSWORD=$STORE_PASS"
    echo "KEY_PASSWORD=$KEY_PASS"
    echo "=================================="
    echo ""

    # 產生 Keystore
    echo "🔑 產生 Keystore..."

    keytool -genkey -v \
        -keystore app/release.keystore \
        -storetype JKS \
        -alias "$ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$STORE_PASS" \
        -keypass "$KEY_PASS" \
        -dname "CN=Unified Remote Evo, OU=Development, O=Personal, L=Taipei, ST=Taiwan, C=TW"

    echo "✅ Keystore 產生成功"
    echo ""

    # 轉換為 Base64（供參考）
    KEYSTORE_B64=$(base64 -w 0 app/release.keystore)

    echo "KEYSTORE_BASE64（供 GitHub Secrets 使用）："
    echo "=================================="
    echo "$KEYSTORE_B64"
    echo "=================================="
    echo ""
    echo "⚠️  請將以上 4 個值保存到安全的地方！"
    echo ""
fi

# 步驟 3: 驗證 Keystore 有效性
echo "📋 步驟 3: 驗證 Keystore 有效性"
echo ""

echo "🔍 驗證 Keystore..."

# 測試 KEYSTORE_PASSWORD
if ! keytool -list -keystore app/release.keystore \
     -storepass "$STORE_PASS" \
     -alias "$ALIAS" > /dev/null 2>&1; then
    echo "❌ Keystore 驗證失敗！(KEYSTORE_PASSWORD 或 KEY_ALIAS 錯誤)"
    exit 1
fi

echo "✅ Step 1: KEYSTORE_PASSWORD 驗證成功"

# 測試 KEY_PASSWORD
echo "🔍 驗證 KEY_PASSWORD..."

if ! keytool -list -keystore app/release.keystore \
     -storepass "$STORE_PASS" \
     -alias "$ALIAS" \
     -keypass "$KEY_PASS" > /dev/null 2>&1; then
    echo "❌ KEY_PASSWORD 驗證失敗！"
    exit 1
fi

echo "✅ Step 2: KEY_PASSWORD 驗證成功"
echo "✅ Keystore 完整驗證通過！別名: $ALIAS"
echo ""

# 顯示憑證資訊
keytool -list -keystore app/release.keystore \
        -storepass "$STORE_PASS" \
        -alias "$ALIAS" | grep -E "Creation date|建立日期" || true

echo ""

# 步驟 4: 建立 keystore.properties
echo "📋 步驟 4: 建立 keystore.properties"
echo ""

cat > keystore.properties <<EOF
storeFile=release.keystore
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS
EOF

echo "✅ 已建立 keystore.properties"
echo ""

# 步驟 5: 編譯 Release APK
echo "📋 步驟 5: 編譯 Release APK"
echo ""

echo "🔨 執行 ./gradlew assembleRelease（這可能需要幾分鐘）..."
echo ""

./gradlew assembleRelease

echo ""
echo "✅ 編譯成功"
echo ""

# 步驟 6: 取得版本號
echo "📋 步驟 6: 取得版本號"
echo ""

VERSION="dev-$(date +%Y%m%d-%H%M%S)"

echo "版本號: $VERSION"
echo ""

# 步驟 7: 重新命名 APK
echo "📋 步驟 7: 重新命名 APK"
echo ""

SOURCE_PATH="app/build/outputs/apk/release/app-release.apk"
DEST_PATH="unified-remote-evo-$VERSION.apk"

if [ -f "$SOURCE_PATH" ]; then
    cp "$SOURCE_PATH" "$DEST_PATH"
    echo "✅ APK 已複製到: $DEST_PATH"
else
    echo "⚠️  找不到原始 APK: $SOURCE_PATH"
fi

echo ""

# 步驟 8: 顯示 APK 資訊
echo "📋 步驟 8: APK 資訊"
echo ""

if [ -f "$DEST_PATH" ]; then
    APK_SIZE=$(stat -f%z "$DEST_PATH" 2>/dev/null || stat -c%s "$DEST_PATH" 2>/dev/null)
    APK_SIZE_MB=$(echo "scale=2; $APK_SIZE / 1024 / 1024" | bc)

    echo "APK 位置："
    echo "  $(pwd)/$DEST_PATH"
    echo ""
    echo "APK 大小："
    echo "  ${APK_SIZE_MB} MB"
else
    echo "⚠️  找不到 APK 檔案"
fi

echo ""

# 步驟 9: 清理（可選）
echo "📋 步驟 9: 清理"
echo ""

read -p "是否清理簽署金鑰？(y/n，建議輸入 n 保留以供下次使用) " cleanup

if [ "$cleanup" = "y" ] || [ "$cleanup" = "Y" ]; then
    rm -f app/release.keystore
    rm -f keystore.properties
    echo "✅ 已清理簽署金鑰"
else
    echo "✅ 保留簽署金鑰供下次使用"
fi

echo ""
echo "🎉 完整建置流程完成！"
echo ""
