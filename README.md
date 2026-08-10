# 随心记（Android）

一个以“快速记录 + 快速查找 + 到点提醒”为核心的原生 Android 小应用。当前版本 **v1.1.0** 在原有记录能力上加入了个性化、图片、导入、桌面小组件和课程表。

## v1.1.0 新增

- 🎨 **自定义主题**：奶油橙、樱花粉、天空蓝、薄荷绿、深色 5 套主题
- 🖼️ **自定义壁纸**：可关闭壁纸、使用内置 Q 版壁纸，或从手机选择自己的图片
- 📷 **记录图片附件**：每条记录可以添加 / 更换 / 移除一张图片，并在列表卡片直接预览
- 🔔 **通知测试**：设置页可立即发送测试通知，确认通知权限和系统通知渠道是否正常
- 📥 **文件导入**：支持 CSV、JSON、TXT
  - CSV 支持中英文表头，如 `title/标题`、`details/内容`、`location/地点`、`eventTime/时间`、`reminder/提醒`、`completed/已完成`
  - JSON 支持事件数组或带 `events` 数组的对象
  - TXT 每一行作为一条新记录
- 🧩 **安卓桌面小组件**：“随心记概览”会显示下一条待办和今天下一节课程
- 🏫 **课程表**：支持周一到周日，课程名称、老师、教室、开始/结束时间和备注
- 🌸 **Q 版视觉**：新增 Q 版应用图标与内置主题壁纸

## 原有功能

- 📝 快速记录事件：标题、详细内容、地点 / 位置、事件时间
- 🔎 即时搜索：同时搜索标题、内容和地点
- 🧭 全部 / 待办 / 已完成筛选
- 🔔 WorkManager 到点通知
- ✅ 完成状态，完成后取消未触发提醒
- 🗑️ 编辑与删除
- 🔒 本地 SQLite，离线优先
- 📱 Android 13+ 按需申请通知权限
- 🔗 点击提醒通知直达对应事件

## 文件导入示例

CSV：

```csv
title,details,location,eventTime,reminder,completed
交作业,数学作业,教学楼,2026-08-11 09:00,true,false
买牛奶,顺路购买,便利店,,false,false
```

JSON：

```json
[
  {
    "title": "社团活动",
    "details": "带上相机",
    "location": "活动室",
    "eventTime": "2026-08-12 18:30",
    "reminder": true
  }
]
```

## 桌面小组件

安装并打开一次随心记后，在安卓桌面长按空白区域，进入 **小组件**，找到 **随心记 → 随心记概览**，拖到桌面即可。小组件会在记录、课程或导入数据发生变化时主动刷新，同时系统也会周期刷新。

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

## 直接下载最新版 APK

仓库使用 GitHub Actions 自动构建并发布 Release。网站可以长期使用下面这个固定下载地址：

```text
https://github.com/xiaoiubao1/-/releases/latest/download/suixinji.apk
```

每次 `main` 分支更新后，GitHub Actions 会重新构建 APK、生成 SHA-256 校验文件，并创建新的 GitHub Release。

> 当前自动发布的是 Debug APK，适合个人安装和测试。后续准备长期分发或上架应用商店时，建议升级为正式 Release 签名。

## 提醒机制说明

当前事件提醒使用 WorkManager。它适合可靠、持久化的提醒任务，但 Android 的省电策略可能让通知在设定时间附近触发，而不是保证到秒级精确。若以后需要“闹钟级精确提醒”，可以继续升级为 AlarmManager + 精确闹钟权限。

## 数据说明

- 事件、课程保存在应用私有 SQLite 数据库 `suixinji.db`
- 主题与壁纸偏好保存在应用私有 SharedPreferences
- 自定义壁纸和记录图片通过 Android 文档选择器保存持久 URI 访问权限，不需要申请整盘存储权限
- 卸载应用会清除本地数据库和偏好设置
