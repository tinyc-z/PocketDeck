package com.pocketdeck.launcher.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * 应用内语言。存在 SharedPreferences 里，因为 attachBaseContext 需要同步读取。
 * API 33 以下没有 LocaleManager，所以统一在 Activity.attachBaseContext 里套用 locale，切换后 recreate。
 */
object AppLocale {
    const val SYSTEM = "system"
    val CODES = listOf(SYSTEM, "en", "zh-CN", "zh-TW")

    private const val PREFS = "app_locale"
    private const val KEY = "lang"

    fun get(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
        return if (saved in CODES) saved else SYSTEM
    }

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).commit()
    }

    private fun localeOf(code: String): Locale? = when (code) {
        SYSTEM -> null
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        "zh-TW" -> Locale.TRADITIONAL_CHINESE
        else -> Locale.forLanguageTag(code)
    }

    /** 返回解析后的实际语言代码（`"zh-CN"` / `"zh-TW"` / `"en"`），`"system"` 会跟随系统语言。 */
    fun resolvedCode(context: Context): String {
        val code = get(context)
        if (code != SYSTEM) return code
        val loc = context.resources.configuration.locales[0] ?: Locale.getDefault()
        if (loc.language.equals("zh", ignoreCase = true)) {
            val isTrad = loc.script.equals("Hant", ignoreCase = true) ||
                loc.country.uppercase() in setOf("TW", "HK", "MO")
            return if (isTrad) "zh-TW" else "zh-CN"
        }
        return "en"
    }

    /** 返回套用了所选语言的 Context。 */
    fun wrap(base: Context): Context {
        val locales = localeOf(get(base))?.let { LocaleList(it) }
            ?: Resources.getSystem().configuration.locales
        Locale.setDefault(locales[0])
        val config = Configuration(base.resources.configuration)
        config.setLocales(locales)
        return base.createConfigurationContext(config)
    }
}
