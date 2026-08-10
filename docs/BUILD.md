# 构建说明

本项目使用 Android Gradle Plugin 8.13.2、Gradle 8.13、JDK 17 和 Android SDK 36。

## GitHub Actions

仓库中的 `.github/workflows/android-build.yml` 会在 push、pull request 或手动触发时执行 Debug APK 构建，并上传 `suixinji-debug-apk` Artifact。

## 本地构建

使用 Android Studio 打开仓库根目录；如果 IDE 要求选择 Gradle 版本，请使用 Gradle 8.13，并确保已安装 Android SDK 36。
