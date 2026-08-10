# 随心记（Android）

一个以“快速记录 + 快速查找 + 到点提醒”为核心的原生 Android 小应用。当前开发版本 **v1.2.0** 加入了正式 Release 签名、完整备份恢复、CSV 导出和课程开始提醒。

## v1.2.0 新增

- 🔐 **固定正式签名**：主分支 Release APK 使用同一套私有签名密钥，便于后续覆盖升级
- 🛡️ **签名保护**：签名材料只从 GitHub Actions Secret `ANDROID_SIGNING_BUNDLE` 读取，公开仓库不保存私钥
- 📤 **CSV 导出**：设置页可将全部记录导出为 CSV
- 💾 **完整备份**：生成 `.suixinji` 备份文件，包含记录、课程、主题、壁纸及能够读取到的图片附件
- ♻️ **备份恢复**：恢复前二次确认，恢复后自动重建事件提醒和课程提醒
- 🔔 **课程提醒**：每门课程可独立开启提醒，可选“上课时”或提前 5 / 10 / 15 / 30 / 60 分钟
- 🔗 **课程通知直达**：点击课程通知会直接打开对应课程
- 🗃️ **数据库 v3**：在保留旧数据的基础上增加课程提醒字段

## 已有功能

- 📝 标题、详细内容、地点、事件时间
- 🔎 搜索标题 / 内容 / 地点
- ✅ 全部 / 待办 / 已完成筛选
- 📷 每条记录可附加一张图片
- 🎨 奶油橙、樱花粉、天空蓝、薄荷绿、深色 5 套主题
- 🖼️ 无壁纸 / Q 版内置壁纸 / 自定义手机图片壁纸
- 🔔 事件通知 + 通知测试
- 📥 CSV / JSON / TXT 导入
- 🧩 安卓桌面小组件
- 🏫 周一到周日课程表
- 🔒 SQLite 离线存储

## 数据导出与备份

设置 → **导出与完整备份**：

- **导出 CSV**：方便在 Excel、WPS 等表格软件查看记录
- **创建完整备份**：生成 `.suixinji` 文件
- **恢复备份**：会覆盖当前记录和课程，因此恢复前建议先创建一份最新备份

完整备份会尝试把自定义壁纸和记录图片一起放进备份包。若某个外部图片 URI 已失效，该图片可能无法写入备份，但其他数据仍可正常保存。

## 课程提醒

编辑课程时打开 **课程开始提醒**，可选择：

- 上课时
- 提前 5 分钟
- 提前 10 分钟
- 提前 15 分钟
- 提前 30 分钟
- 提前 60 分钟

课程提醒使用 WorkManager 安排下一次课程通知，通知触发后会自动安排下一周。Android 的省电策略可能让通知出现少量延迟，因此它属于可靠的“附近时间提醒”，不是秒级闹钟。

## 正式签名

正式签名配置见 [`docs/SIGNING.md`](docs/SIGNING.md)。仓库本身不会保存 `.jks` 私钥。

主分支发布流程只有在仓库已配置 `ANDROID_SIGNING_BUNDLE` Secret 时才会构建和发布正式 APK；缺少 Secret 会主动失败，避免 Debug APK 或未签名 APK 覆盖公开最新版。

## 直接下载最新版 APK

网站可以长期使用固定地址：

```text
https://github.com/xiaoiubao1/-/releases/latest/download/suixinji.apk
```

正式签名 Secret 配置完成并合并 v1.2.0 后，后续 `main` 更新会自动构建签名 Release APK、执行 `apksigner verify`，然后创建新的 GitHub Release。

## 技术栈

- Kotlin 2.3.21
- Jetpack Compose
- Compose BOM 2026.06.00
- Android Gradle Plugin 8.13.2
- compileSdk / targetSdk 36
- Gradle 8.13
- WorkManager 2.11.2
- SQLiteOpenHelper
- RemoteViews + AppWidgetProvider
- minSdk 26（Android 8.0+）

## 数据位置

- 事件、课程：应用私有 SQLite 数据库 `suixinji.db`
- 主题与壁纸偏好：应用私有 SharedPreferences
- 从备份恢复的图片：应用私有 `filesDir/restored_media`
- 卸载应用会清除本地数据，因此建议定期创建 `.suixinji` 备份并保存到其他位置
