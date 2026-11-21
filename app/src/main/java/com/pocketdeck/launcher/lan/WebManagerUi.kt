package com.pocketdeck.launcher.lan

import android.content.Context

/**
 * PocketDeck Web Console 静态资源加载器：
 * HTML / CSS / JS 拆分存放在 `assets/web/`，按需在有 HTTP 请求时直接从 APK assets 流式读取，不常驻内存，并实时注入掌机主题变量。
 */
internal object WebManagerUi {

    private val STATIC_ASSET_MIMES = mapOf(
        "style.css" to "text/css; charset=utf-8",
        "app.js" to "application/javascript; charset=utf-8",
        "login.css" to "text/css; charset=utf-8",
        "login.js" to "application/javascript; charset=utf-8",
        "cjk_aliases.tsv" to "text/tab-separated-values; charset=utf-8",
    )

    private fun readAsset(context: Context, name: String): String {
        val assetPath = if (name == "cjk_aliases.tsv") name else "web/$name"
        return context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun staticAssetMime(name: String): String? = STATIC_ASSET_MIMES[name]

    fun loadStaticAsset(context: Context, name: String): Pair<String, String>? {
        val mime = STATIC_ASSET_MIMES[name] ?: return null
        val content = runCatching { readAsset(context, name) }.getOrNull() ?: return null
        return content to mime
    }

    private fun cssVars(theme: WebThemeConfig): String {
        val j = theme.toJson()
        val dark = j.getBoolean("isDark")
        val primary = j.getString("primary")
        val glow = j.getString("glow")
        val tertiary = j.getString("tertiary")
        val bg = j.getString("bg")
        val panel = j.getString("panel")
        val surf = j.getString("surf")
        val border = j.getString("border")
        val text = j.getString("text")
        val muted = j.getString("muted")
        val label = j.optString("label", muted)
        val bStart = j.optString("brandStart", glow)
        val bMid = j.optString("brandMid", primary)
        val bEnd = j.optString("brandEnd", tertiary)
        val surfAlt = j.optString("surfaceAlt", surf)
        val line = j.optString("line", border)
        val accSoft = j.optString("accentSoft", "rgba(232,62,108,0.12)")
        val flowA = j.optString("flowA", "rgba(255,196,138,0.55)")
        val flowB = j.optString("flowB", "rgba(255,157,181,0.50)")
        val flowC = j.optString("flowC", "rgba(201,178,255,0.42)")
        val sideBg = if (dark) "rgba(35,26,46,.68)" else "rgba(255,255,255,.62)"
        val shadow = if (dark) "0 8px 24px rgba(0,0,0,.45)" else "0 6px 18px rgba(120,40,70,.09),0 1px 3px rgba(120,40,70,.06)"
        val inputBg = if (dark) "#231A2E" else "#FFFFFF"
        return """
            --accent:$primary;
            --glow:$glow;
            --tertiary:$tertiary;
            --brand-start:$bStart;
            --brand-mid:$bMid;
            --brand-end:$bEnd;
            --brand:linear-gradient(120deg,$bStart 0%,$bMid 48%,$bEnd 100%);
            --bg:$bg;
            --panel:$panel;
            --surf:$surf;
            --surf-alt:$surfAlt;
            --border:$line;
            --line:$line;
            --text:$text;
            --muted:$muted;
            --label:$label;
            --accent-soft:$accSoft;
            --flow-a:$flowA;
            --flow-b:$flowB;
            --flow-c:$flowC;
            --side-bg:$sideBg;
            --shadow:$shadow;
            --input-bg:$inputBg;
            --danger:#e11d48;
            --ok:#1fa971;
            --warn:#f59e0b;
        """.trimIndent().replace("\n", "")
    }

    fun renderLoginHtml(context: Context, theme: WebThemeConfig): String =
        readAsset(context, "login.html")
            .replace("{{DARK_ATTR}}", if (theme.isDark) "1" else "0")
            .replace("{{THEME_VARS}}", cssVars(theme))

    fun renderAppHtml(context: Context, theme: WebThemeConfig): String =
        readAsset(context, "index.html")
            .replace("{{DARK_ATTR}}", if (theme.isDark) "1" else "0")
            .replace("{{THEME_VARS}}", cssVars(theme))
}
