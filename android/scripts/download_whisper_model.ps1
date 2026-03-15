# 下载 Whisper 模型到 app/src/main/assets，供语音转文字使用。
# 运行: 在项目根目录执行 .\scripts\download_whisper_model.ps1
# 或指定模型: .\scripts\download_whisper_model.ps1 -Model base

param(
    [ValidateSet("tiny", "base", "small")]
    [string]$Model = "tiny"
)

$baseUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
$fileName = "ggml-$Model.bin"
$assetsDir = Join-Path $PSScriptRoot "..\app\src\main\assets"
$outPath = Join-Path $assetsDir $fileName

if (-not (Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
}

if (Test-Path $outPath) {
    Write-Host "已存在: $outPath"
    exit 0
}

$url = "$baseUrl/$fileName"
Write-Host "下载中: $url -> $outPath"
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $outPath -UseBasicParsing
    Write-Host "完成. 请用 -PuseWhisper=true 重新编译 android 模块并运行输入法."
} catch {
    Write-Error "下载失败: $_"
    exit 1
}
