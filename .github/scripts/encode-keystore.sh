#!/bin/bash
# Bash 腳本：將 Keystore 轉換為 Base64
# 用法：./encode-keystore.sh path/to/your.keystore

set -e

# 檢查參數
if [ $# -eq 0 ]; then
    echo "錯誤：請提供 Keystore 檔案路徑"
    echo "用法：$0 <keystore-file>"
    exit 1
fi

KEYSTORE_PATH="$1"

# 檢查檔案是否存在
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "錯誤：找不到 Keystore 檔案 '$KEYSTORE_PATH'"
    exit 1
fi

echo "正在轉換 Keystore 為 Base64..."

# 轉換為 Base64
base64 "$KEYSTORE_PATH" > keystore.base64

# 顯示結果
echo ""
echo "✅ 轉換成功！"
echo ""
echo "輸出檔案：keystore.base64"
echo "檔案大小：$(wc -c < keystore.base64) 字元"
echo ""
echo "📋 下一步："
echo "1. 開啟 keystore.base64"
echo "2. 複製完整內容"
echo "3. 前往 GitHub Repository Settings"
echo "4. Secrets and variables → Actions → New repository secret"
echo "5. Name: KEYSTORE_BASE64"
echo "6. Value: 貼上複製的 Base64 內容"
echo ""
echo "⚠️  注意：請勿將 keystore.base64 上傳到 Git！"
echo ""
echo "完成！"
