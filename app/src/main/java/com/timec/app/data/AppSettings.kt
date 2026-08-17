package com.timec.app.data

data class AppSettings(
    val enabled: Boolean = true,
    val themeIndex: Int = 0,
    // 透支需等待多少秒后才能点击（0 = 立即）
    val overdraftDelaySeconds: Int = 0,
    // 正式限制时的提示语
    val finalMessage: String = "你不是在被惩罚，而是在拿回选择权。",
    // 悬浮窗背景样式索引
    val overlayBackground: Int = 0,
    // ---- 悬浮计时窗 ----
    val widgetEnabled: Boolean = false,
    val widgetAllApps: Boolean = false,
    val widgetMetrics: Set<String> = setOf("app_session"),
    val widgetOpacity: Int = 80,
    // 字号（sp，无极调节）
    val widgetFontSize: Int = 14,
    // 背景：0 黑底 1 白底 2 全透明
    val widgetBackground: Int = 0,
    // 文字颜色：0 白 1 黑（透明背景 / 自动无授权时生效）
    val widgetTextColor: Int = 0,
    // 文字到背景边沿的边距（dp，四边等宽）
    val widgetMargin: Int = 10,
    // 刷新间隔（秒，默认 1）
    val widgetRefreshSeconds: Int = 1,
    // 拖动惯性 0..10（越大越飘、松手滑得更远）
    val widgetInertia: Int = 5,
    // 各指标前缀（可自定义/留空/emoji）
    val widgetMetricPrefixes: Map<String, String> = emptyMap(),
    val screenOffResetThresholdSeconds: Int = 30,
    val widgetComparePeriod: Int = 0,
    // 显示方式：0 = 循环切换，1 = 固定单指标
    val widgetMode: Int = 0,
    val widgetSingleMetric: String = "app_session",
    val widgetPosX: Int = -1,
    val widgetPosY: Int = -1,
    // 手动运行守护服务（守护清单为空时，用于悬浮计时窗）
    val serviceManual: Boolean = false,
    val defaultRule: AppRule = AppRule(),
    val appRules: Map<String, AppRule> = emptyMap(),
    val templates: Map<String, AppRule> = emptyMap(),
    // 应用 -> 使用的模板名（用于分组展示）
    val appTemplateNames: Map<String, String> = emptyMap()
) {
    fun ruleFor(packageName: String): AppRule = appRules[packageName] ?: defaultRule
}
