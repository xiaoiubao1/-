# 随心记正式签名配置

随心记 v1.2.0 开始，公开 APK 使用固定 Release 签名。私钥不会提交到公开仓库。

## GitHub Secret

仓库需要一个 Actions Secret：

```text
ANDROID_SIGNING_BUNDLE
```

它是一个 Base64 文本，内部包含：

- Release keystore 的 Base64 内容
- keystore 密码
- key alias
- key 密码

GitHub Actions 会在临时 Runner 中解码私钥，只在当前构建使用；Runner 结束后临时文件随之销毁。

## 配置位置

GitHub 仓库 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**：

- Name：`ANDROID_SIGNING_BUNDLE`
- Secret：粘贴签名包文本的完整内容

配置完成后，可以重新运行主分支工作流，或者让新的提交触发构建。

## 发布保护

`.github/workflows/android-build.yml` 对主分支有保护：

- PR 只构建 Debug APK，用于代码验证
- main 必须存在 `ANDROID_SIGNING_BUNDLE`
- main 使用 `assembleRelease`
- 发布前运行 `apksigner verify --verbose --print-certs`
- 缺少签名 Secret 时工作流会失败，并且不会创建新的 GitHub Release

这样可以避免 Debug / 未签名 APK 意外成为网站公开下载的最新版。

## 当前签名证书指纹

```text
SHA-1   81:2F:02:B8:91:7C:5C:5D:F7:4B:A4:BD:58:66:95:60:DC:44:4F:61
SHA-256 BD:53:B2:77:BE:3E:16:1B:96:58:EC:C3:FC:7F:24:39:09:E5:DA:CC:5E:DC:67:64:C6:BB:2C:82:43:EE:20:41
```

证书指纹可以公开，但 `.jks`、密码和 `ANDROID_SIGNING_BUNDLE` 必须保密。

## 重要备份

如果直接通过 APK 分发并自己管理签名密钥，以后的更新必须继续使用同一签名密钥。请把签名备份 ZIP 至少保存到两个安全位置，不要只保存在手机或某一台电脑上。
