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
    val widgetFontSize: Int = 1,
    val screenOffResetThresholdSeconds: Int = 30,
    val widgetComparePeriod: Int = 0,
    val widgetPosX: Int = -1,
    val widgetPosY: Int = -1,
    val defaultRule: AppRule = AppRule(),
    val appRules: Map<String, AppRule> = emptyMap(),
    val templates: Map<String, AppRule> = emptyMap(),
    // 应用 -> 使用的模板名（用于分组展示）
    val appTemplateNames: Map<String, String> = emptyMap()
) {
    fun ruleFor(packageName: String): AppRule = appRules[packageName] ?: defaultRule
}
