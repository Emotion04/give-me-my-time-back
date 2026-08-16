package com.timec.app.data

import org.json.JSONObject

data class AppRule(
    // 0 = 正常模式（到点延长一分钟），1 = 时间银行（支持透支）
    val mode: Int = 1,
    val sessionLimitMinutes: Int = 15,
    val dailyLimitMinutes: Int = 120,
    val floorMinutes: Int = 5,
    val overdraftMultiplier: Float = 2f,
    val cooldownMinutes: Int = 30,
    val cooldownPenaltyMinutes: Int = 5,
    val extensionSeconds: Int = 60,
    // 加一分钟的次数上限（0 = 不允许延长）
    val maxExtensions: Int = 3,
    val warnPct1: Int = 80,
    val warnPct2: Int = 90,
    val frictionEnabled: Boolean = false,
    val frictionSeconds: Int = 4
)

fun AppRule.toJson(): String = JSONObject().apply {
    put("md", mode)
    put("s", sessionLimitMinutes)
    put("d", dailyLimitMinutes)
    put("f", floorMinutes)
    put("m", overdraftMultiplier.toDouble())
    put("c", cooldownMinutes)
    put("p", cooldownPenaltyMinutes)
    put("e", extensionSeconds)
    put("mx", maxExtensions)
    put("w1", warnPct1)
    put("w2", warnPct2)
    put("fr", frictionEnabled)
    put("fs", frictionSeconds)
}.toString()

fun appRuleFromJson(json: String): AppRule = try {
    val o = JSONObject(json)
    AppRule(
        mode = o.optInt("md", 1),
        sessionLimitMinutes = o.optInt("s", 15),
        dailyLimitMinutes = o.optInt("d", 120),
        floorMinutes = o.optInt("f", 5),
        overdraftMultiplier = o.optDouble("m", 2.0).toFloat(),
        cooldownMinutes = o.optInt("c", 30),
        cooldownPenaltyMinutes = o.optInt("p", 5),
        extensionSeconds = o.optInt("e", 60),
        maxExtensions = o.optInt("mx", 3),
        warnPct1 = o.optInt("w1", 80),
        warnPct2 = o.optInt("w2", 90),
        frictionEnabled = o.optBoolean("fr", false),
        frictionSeconds = o.optInt("fs", 4)
    )
} catch (e: Exception) {
    AppRule()
}

fun ruleMapToJson(map: Map<String, AppRule>): String = JSONObject().apply {
    map.forEach { (k, v) -> put(k, v.toJson()) }
}.toString()

fun ruleMapFromJson(json: String): Map<String, AppRule> = try {
    val o = JSONObject(json)
    val out = mutableMapOf<String, AppRule>()
    val it = o.keys()
    while (it.hasNext()) {
        val k = it.next()
        out[k] = appRuleFromJson(o.getString(k))
    }
    out
} catch (e: Exception) {
    emptyMap()
}

fun stringMapToJson(map: Map<String, String>): String = JSONObject().apply {
    map.forEach { (k, v) -> put(k, v) }
}.toString()

fun stringMapFromJson(json: String): Map<String, String> = try {
    val o = JSONObject(json)
    val out = mutableMapOf<String, String>()
    val it = o.keys()
    while (it.hasNext()) {
        val k = it.next()
        out[k] = o.getString(k)
    }
    out
} catch (e: Exception) {
    emptyMap()
}
