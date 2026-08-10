# 随心记（Android）

一个以“快速记录 + 快速查找 + 到点提醒”为核心的原生 Android 小应用。

## 已实现

- 📝 **快速记录事件**：标题、详细内容、地点/位置、事件时间
- 🔎 **即时搜索**：同时搜索标题、内容和地点
- 🧭 **快速定位记录**：按“全部 / 待办 / 已完成”筛选，时间与地点直接显示在记录卡片上
- 🔔 **事件提醒**：可为记录开启“到点通知”，使用 Android WorkManager 持久化调度
- ✅ **完成状态**：一键标记完成或恢复待办；完成后自动取消未触发提醒
- 🗑️ **编辑与删除**：记录可随时修改，删除时同步取消提醒
- 🔒 **离线优先**：数据保存在手机本地 SQLite，不要求登录、不上传服务器
- 📱 **Android 13+ 通知权限**：只有真正使用提醒功能时才申请通知权限
- 🔗 **通知直达事件**：点击提醒通知后会直接打开对应记录
- 🛠️ **GitHub Actions 自动构建与发布**：推送到 `main` 后自动构建 APK，并创建新的 GitHub Release

## 技术栈

- Kotlin 2.3.21
- Jetpack Compose
- Compose BOM 2026.06.00
- Android Gradle Plugin 8.13.2
- compileSdk / targetSdk 36
- Gradle 8.13
- WorkManager 2.11.2
- SQLiteOpenHelper
- minSdk 26（Android 8.0+）

## 在 Android Studio 运行

1. 克隆本仓库。
2. 用 Android Studio 打开仓库根目录。
3. 等待 Gradle Sync 完成。
4. 如果 Android Studio 询问 Gradle 版本，请选择 / 配置 **Gradle 8.13**。
5. 连接安卓手机（开启 USB 调试）或启动模拟器。
6. 点击 **Run ▶**。

首次为某条记录打开“到点通知”时，Android 13 及以上系统会请求通知权限。

## 公开下载最新版 APK

每次有代码推送到 `main`，GitHub Actions 会自动：

1. 构建 Android Debug APK。
2. 将 APK 固定重命名为 `suixinji.apk`。
3. 生成 SHA-256 校验文件 `suixinji.apk.sha256`。
4. 创建一个新的 GitHub Release。
5. 把 APK 和校验文件上传到该 Release。

### 永久固定下载地址

网站、博客或下载页都可以一直使用下面这个地址，不需要随着版本更新修改：

```text
https://github.com/xiaoiubao1/-/releases/latest/download/suixinji.apk
```

GitHub 会自动把这个地址指向最新一次 Release 中的 `suixinji.apk`。

### 网站下载按钮示例

```html
<a href="https://github.com/xiaoiubao1/-/releases/latest/download/suixinji.apk"
   style="display:inline-block;padding:14px 24px;border-radius:12px;text-decoration:none;font-weight:700;">
  📱 下载随心记 Android 版
</a>
```

手机用户点击按钮后会直接下载 `suixinji.apk`。下载完成后点击 APK 安装；如果 Android 提示不允许安装未知来源应用，需要按系统提示允许当前浏览器或文件管理器安装应用。

> 当前公开下载的是 GitHub 自动签名的 Debug APK，适合测试、自用和公开体验。正式上架应用商店前建议增加独立 Release 签名、版本管理、隐私政策和正式发布流程。

## Actions Artifact

除了 Release，构建流程仍然会保留 `suixinji-debug-apk` Artifact，方便开发时检查构建产物。普通用户和公开网站建议直接使用上面的 Release 固定下载地址。

## 提醒机制说明

当前版本使用 WorkManager。它适合可靠、持久化的提醒任务，但 Android 为省电可能会让通知在设定时间附近触发，而不是保证到秒级精确。如果后续需要“闹钟级精确提醒”，可以升级为 AlarmManager + 精确闹钟权限方案。

## 数据与搜索

每条记录目前包含：

- 标题
- 详细内容
- 地点 / 位置
- 事件时间
- 是否开启提醒
- 是否完成
- 创建时间

搜索框会同时匹配标题、详细内容和地点，因此既可以按事情名称找，也可以按地点快速定位记录。

## 后续可扩展

- 标签 / 分类
- 置顶与收藏
- 图片附件
- 日历视图
- GPS 地点与地图
- 数据导出 / 导入
- WebDAV / 云同步
- 桌面小组件
- 指纹 / 密码锁

## 数据位置

事件数据存放在应用私有 SQLite 数据库 `suixinji.db` 中。卸载应用会清除本地数据，请在加入导出 / 备份功能前注意这一点。
