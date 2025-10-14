# PowerShell 腳本：將 Keystore 轉換為 Base64
# 用法：.\encode-keystore.ps1 path\to\your.keystore

param(
    [Parameter(Mandatory=$true)]
    [string]$KeystorePath
)

# 檢查檔案是否存在
if (-not (Test-Path $KeystorePath)) {
    Write-Error "錯誤：找不到 Keystore 檔案 '$KeystorePath'"
    exit 1
}

Write-Host "正在轉換 Keystore 為 Base64..." -ForegroundColor Green

try {
    # 讀取檔案並轉換為 Base64
    $bytes = [System.IO.File]::ReadAllBytes($KeystorePath)
    $base64 = [System.Convert]::ToBase64String($bytes)

    # 輸出到檔案
    $outputFile = "keystore.base64"
    $base64 | Out-File -Encoding ASCII -FilePath $outputFile

    Write-Host "`n✅ 轉換成功！" -ForegroundColor Green
    Write-Host "`n輸出檔案：$outputFile" -ForegroundColor Cyan
    Write-Host "檔案大小：$($base64.Length) 字元" -ForegroundColor Cyan

    Write-Host "`n📋 下一步：" -ForegroundColor Yellow
    Write-Host "1. 開啟 $outputFile" -ForegroundColor White
    Write-Host "2. 複製完整內容" -ForegroundColor White
    Write-Host "3. 前往 GitHub Repository Settings" -ForegroundColor White
    Write-Host "4. Secrets and variables → Actions → New repository secret" -ForegroundColor White
    Write-Host "5. Name: KEYSTORE_BASE64" -ForegroundColor White
    Write-Host "6. Value: 貼上複製的 Base64 內容" -ForegroundColor White

    Write-Host "`n⚠️  注意：請勿將 keystore.base64 上傳到 Git！" -ForegroundColor Red

    # 詢問是否要複製到剪貼簿（Windows 10+）
    $copy = Read-Host "`n是否要將內容複製到剪貼簿？(y/n)"
    if ($copy -eq 'y' -or $copy -eq 'Y') {
        $base64 | Set-Clipboard
        Write-Host "✅ 已複製到剪貼簿！" -ForegroundColor Green
    }

} catch {
    Write-Error "轉換失敗：$($_.Exception.Message)"
    exit 1
}

Write-Host "`n完成！" -ForegroundColor Green
