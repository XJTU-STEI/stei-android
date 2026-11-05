<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="96" alt="电信科协 STEI 图标">
</p>

# STEI Android 客户端

电信科协（STEI）Android 客户端，通过 Android 系统 WebView 在线访问 [stei.top](https://stei.top/)。应用对应版本为 **1.1.1**（versionCode **3**）。

[获取 Android 安装包](https://stei.top/mobile/) · [公开源码仓库](https://github.com/XJTU-STEI/stei-android)

## 功能

- 在应用内浏览 stei.top，并支持系统返回键和下拉刷新。
- 通过网页文件选择器选择文件上传。
- 将站外链接交给系统中可处理该链接的应用打开。
- 页面加载失败时显示重试入口。
- 保存并恢复 WebView 页面状态，使用系统 WebView Cookie 保持登录会话。

应用需要联网；网页内容由 stei.top 提供。

## 运行要求

- Android 7.0 或更高版本（API 24+）。
- Android 系统 WebView。

## 构建

需要 JDK 17 或 21、Android SDK Platform 36 和 Android SDK Build-Tools 36.0.0。确保 Android SDK 已安装，并通过 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 或本机未提交的 `local.properties` 指向 SDK 目录。

Windows PowerShell：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Linux：

```sh
sh ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

调试版 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。调试版使用 `com.example.xjtu.preview` applicationId，可与正式版共存。

## Release 构建与签名

运行 `:app:assembleRelease` 可生成启用 R8 代码压缩和资源缩减的未签名 APK：

Windows：

```powershell
.\gradlew.bat :app:assembleRelease
```

Linux：

```sh
sh ./gradlew :app:assembleRelease
```

产物位于 `app/build/outputs/apk/release/app-release-unsigned.apk`。公开源码不包含签名配置或签名密钥；分发前需使用自己的密钥签名。自行签名的 APK 不能覆盖由官方签名安装的应用。请勿将签名密钥或本机签名配置提交到仓库。

## 维护与许可

维护者：Ancimen。

本仓库未附单独的源码许可证。

