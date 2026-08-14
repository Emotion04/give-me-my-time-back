package com.timec.app.data

// 恢复模式：0 = 柔和·回充（离开按比例回充额度），1 = 严格·冷却（到点后需冷却再进入）
object RecoverMode {
    const val RECHARGE = 0
    const val COOLDOWN = 1
}

data class AppSettings(
    val enabled: Boolean = true,
    val limitMinutes: Int = 15,
    // 阶梯翻倍消耗
    val tieredEnabled: Boolean = true,
    val rate100x: Float = 2f,
    val rate150x: Float = 3f,
    // 提前提醒档位（百分比，0 = 关闭该档）
    val warnPct1: Int = 80,
    val warnPct2: Int = 90,
    // 恢复模式
    val recoverMode: Int = RecoverMode.RECHARGE,
    // 柔和·回充参数
    val breakResetSeconds: Int = 30,
    val earnWorkSeconds: Int = 3,
    val earnRewardSeconds: Int = 1,
    // 严格·冷却参数
    val cooldownSeconds: Int = 60,
    // 正式限制
    val extensionSeconds: Int = 60,
    val maxExtensions: Int = 1,
    val hardBlock: Boolean = false,
    // 打开摩擦页（One Sec 式）
    val frictionEnabled: Boolean = false,
    val frictionSeconds: Int = 4,
    val selectedPackages: Set<String> = emptySet()
)
