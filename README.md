# 还我时间（TimeGuard）

抖音快手小红书，豆瓣微博贴吧信息流！再也忍受不了互联网巨头对我们时间的占据。一个 Android 屏幕时间统计 + 防沉迷应用。记录各 App 使用时长，但我要做的，是与市面上的时间管理APP不同的是，我觉得现存的时间管理APP都太过鸡肋。让人感觉作者似乎从未沉迷于手机，也没有对如何摆脱手机成瘾有过思考。这个app真正的特点是市面上还没有的特色功能，对指定 App 做连续使用管控等。于是我直接自己写一个给自己用。

## 功能

TODO

## 技术栈
Kotlin · Jetpack Compose (Material 3)

## 构建
需要 Android Studio（含 JDK 21）+ Android SDK（platform android-37）。

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 安装
1. 打开 App → 概览页点「去授权」→ 开启「使用情况访问」
2. 设置页授予：悬浮窗、忽略电池优化；（可选）硬拦截需无障碍服务
3. 「应用」页勾选要守护的 App

## 更新
- **方式 A**：安装 [Obtainium](https://github.com/ImranR98/Obtainium)，添加本仓库，自动检测 Releases 里的新 APK 并更新。
- **方式 B**：在本仓库 Releases 页手动下载 APK 安装。

## 权限说明
- `PACKAGE_USAGE_STATS`：统计各 App 使用时长、识别前台应用
- `SYSTEM_ALERT_WINDOW`：悬浮窗设置全屏落地
- `POST_NOTIFICATIONS`：前台服务通知与提醒
- 无障碍服务（可选）：硬拦截增强

***无任何敏感权限，不会收集用户的任何信息。***
