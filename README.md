# 还我时间（TimeGuard）

一个 Android 屏幕时间统计 + 防沉迷应用。记录各 App 使用时长，并对指定 App 做连续使用管控。

## 功能
- **使用统计**：今日 / 近7天 / 近30天；饼图、条形图、排行榜、单 App 分时、屏幕总时长趋势
- **连续使用管控**：额度（默认 15 分钟）+ 阶梯翻倍消耗（100%/150% 倍率，支持小数）
- **恢复模式**：柔和·回充（离开按比例回充额度）/ 严格·冷却（到点须冷却再进入）
- **干预**：提前提醒（80%/90% 档可自定义）+ 全屏柔和落地页三选一（延长/跳过/确定）+ 可选硬拦截 + 打开摩擦页（One Sec 式）

## 技术栈
Kotlin · Jetpack Compose (Material 3) · DataStore · UsageStatsManager · specialUse 前台服务 · 自绘图表

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
- **方式 A（推荐）**：安装 [Obtainium](https://github.com/ImranR98/Obtainium)，添加本仓库，自动检测 Releases 里的新 APK 并更新。
- **方式 B**：在本仓库 Releases 页手动下载 APK 安装。

## 权限说明
- `PACKAGE_USAGE_STATS`：统计各 App 使用时长、识别前台应用
- `SYSTEM_ALERT_WINDOW`：正式限制/冷却/摩擦页的全屏落地
- `POST_NOTIFICATIONS`：前台服务通知与提醒
- 无障碍服务（可选）：硬拦截增强
