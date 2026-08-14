package com.osfans.trime.langou.ai

import com.osfans.trime.BuildConfig
import java.util.Locale

object ChatApplicationMapper {
    private val applications =
        listOf(
            "com.tencent.mm" to "wechat",
            "com.tencent.mobileqq" to "qq",
            "com.tencent.wework" to "wecom",
            "com.alibaba.android.rimet" to "dingtalk",
            "com.ss.android.lark" to "feishu",
            "com.whatsapp" to "whatsapp",
            "org.telegram.messenger" to "telegram",
            "com.discord" to "discord",
        )

    fun map(packageName: String): String? {
        val normalized = packageName.lowercase(Locale.ROOT)
        if (BuildConfig.DEBUG && normalized == BuildConfig.APPLICATION_ID.lowercase(Locale.ROOT)) {
            return "wechat"
        }
        return applications.firstOrNull { (prefix, _) ->
            normalized == prefix || normalized.startsWith("$prefix.")
        }?.second
    }
}
