package com.timec.app.data

data class AppSettings(
    val enabled: Boolean = true,
    val themeIndex: Int = 0,
    // 0 = 正常模式（到点延长一分钟），1 = 时间银行（支持透支）
    val mode: Int = 1,
    // 透支需等待多少秒后才能点击（0 = 立即）
    val overdraftDelaySeconds: Int = 0,
    val defaultRule: AppRule = AppRule(),
    val appRules: Map<String, AppRule> = emptyMap(),
    val templates: Map<String, AppRule> = emptyMap()
) {
    fun ruleFor(packageName: String): AppRule = appRules[packageName] ?: defaultRule
}
