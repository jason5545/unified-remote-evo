#!/bin/bash
# Bash 腳本：設定本機 keystore
# 用法：./scripts/setup-local-keystore.sh

set -e

echo "🔑 設定本機 Release 建置環境"
echo ""

# 檢查是否已有 keystore
if [ -f "app/release.keystore" ]; then
    echo "⚠️  發現現有的 keystore 檔案"
    read -p "是否覆蓋？(y/n) " overwrite
    if [ "$overwrite" != "y" ] && [ "$overwrite" != "Y" ]; then
        echo "取消設定"
        exit 0
    fi
fi

echo "請從 GitHub Actions 日誌或 Secrets 中取得以下資訊："
echo ""

# 輸入 Base64
echo "1. KEYSTORE_BASE64（Base64 字串，可能很長）："
read -p "請貼上: " base64

# 輸入密碼
echo ""
echo "2. KEYSTORE_PASSWORD："
read -p "請輸入: " storePass

echo ""
echo "3. KEY_ALIAS："
read -p "請輸入: " alias

echo ""
echo "4. KEY_PASSWORD："
read -p "請輸入: " keyPass

echo ""
echo "🔄 正在設定..."

# 解碼 Base64 並寫入 keystore 檔案
echo "$base64" | base64 -d > app/release.keystore

echo "✅ 已建立 app/release.keystore"

# 建立 keystore.properties
cat > keystore.properties <<EOF
storeFile=release.keystore
storePassword=$storePass
keyAlias=$alias
keyPassword=$keyPass
EOF

echo "✅ 已建立 keystore.properties"

# 驗證 keystore
echo ""
echo "🔍 驗證 keystore..."

if keytool -list -keystore app/release.keystore \
    -storepass "$storePass" \
    -alias "$alias" > /dev/null 2>&1; then
    echo ""
    echo "✅ Keystore 驗證成功！"
    keytool -list -keystore app/release.keystore \
        -storepass "$storePass" \
        -alias "$alias"
else
    echo ""
    echo "❌ Keystore 驗證失敗，請檢查輸入的資訊"
    exit 1
fi

echo ""
echo "🎉 設定完成！"
echo ""
echo "現在可以執行："
echo "  ./gradlew assembleRelease"
echo ""
echo "APK 位置："
echo "  app/build/outputs/apk/release/app-release.apk"
