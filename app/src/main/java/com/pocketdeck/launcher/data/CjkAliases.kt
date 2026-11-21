package com.pocketdeck.launcher.data

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.SoftReference

/**
 * 精选经典主机/掌机/街机中文译名 → 英文原名映射表。
 *
 * 词表存放在 `assets/cjk_aliases.tsv`（约 400 条），通过 [SoftReference] 按需懒加载：
 * - 仅调用 [containsCjk] 或英文游戏库时完全不读文件、零内存占用；
 * - 实际遇到中文 ROM 名需要翻译/推荐时才从 assets 解析，内存紧张时可自动回收。
 */
object CjkAliases {

    private class AliasTable(
        val exactGames: List<Pair<String, String>>,
        val seriesPrefixes: List<Pair<String, String>>,
    )

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var tableRef: SoftReference<AliasTable>? = null

    private val loadLock = Any()

    private val TRAILING_NUM = Regex("""(\d{1,4}|[IVX]{1,4})$""")

    /** 仅保存 Application Context 引用，不触发任何磁盘读取。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun table(): AliasTable {
        tableRef?.get()?.let { return it }
        synchronized(loadLock) {
            tableRef?.get()?.let { return it }
            val loaded = loadTable()
            tableRef = SoftReference(loaded)
            return loaded
        }
    }

    private fun openReader(): BufferedReader? {
        appContext?.let { ctx ->
            return runCatching {
                BufferedReader(InputStreamReader(ctx.assets.open("cjk_aliases.tsv"), Charsets.UTF_8))
            }.getOrNull()
        }
        // 供纯 JVM 单元测试（无 Android Context）读取本地 assets 文件
        val candidates = listOf(
            File("app/src/main/assets/cjk_aliases.tsv"),
            File("src/main/assets/cjk_aliases.tsv"),
        )
        val file = candidates.firstOrNull { it.isFile } ?: return null
        return runCatching { file.bufferedReader(Charsets.UTF_8) }.getOrNull()
    }

    private fun loadTable(): AliasTable {
        val exact = ArrayList<Pair<String, String>>(350)
        val series = ArrayList<Pair<String, String>>(80)
        openReader()?.use { br ->
            br.forEachLine { rawLine ->
                if (rawLine.isEmpty() || rawLine[0] == '#') return@forEachLine
                val t1 = rawLine.indexOf('\t')
                if (t1 <= 0) return@forEachLine
                val t2 = rawLine.indexOf('\t', t1 + 1)
                if (t2 <= t1 + 1) return@forEachLine
                val zh = rawLine.substring(t1 + 1, t2)
                val en = rawLine.substring(t2 + 1)
                if (zh.isEmpty() || en.isEmpty()) return@forEachLine
                when (rawLine[0]) {
                    'E' -> exact.add(zh to en)
                    'S' -> series.add(zh to en)
                }
            }
        }
        return AliasTable(exact, series)
    }

    /** 若 [raw] 包含中文字符，返回 0..3 条英文候选标题。 */
    fun expandEnglishAliases(raw: String): List<String> {
        if (raw.isBlank() || !containsCjk(raw)) return emptyList()
        val cleaned = TitleNormalizer.stripRomTags(raw)
            .replace(Regex("""[-:：·—_\s]+"""), " ")
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        val compact = cleaned.replace(" ", "")
        val tbl = table()

        val out = LinkedHashSet<String>(3)
        for ((zh, en) in tbl.exactGames) {
            val zhCompact = zh.replace(" ", "")
            if (cleaned.contains(zh, ignoreCase = true) || compact.contains(zhCompact, ignoreCase = true)) {
                out += en
                if (out.size >= 2) break
            }
        }
        if (out.isEmpty()) {
            for ((prefix, enSeries) in tbl.seriesPrefixes) {
                val pCompact = prefix.replace(" ", "")
                val idx = compact.indexOf(pCompact, ignoreCase = true)
                if (idx >= 0) {
                    val tail = compact.substring(idx + pCompact.length).trim()
                    val num = TRAILING_NUM.find(tail)?.groupValues?.getOrNull(1)
                    if (!num.isNullOrBlank()) {
                        out += "$enSeries $num"
                    }
                    out += enSeries
                    break
                }
            }
        }
        return out.toList()
    }

    fun containsCjk(s: String): Boolean {
        for (ch in s) {
            val code = ch.code
            if (code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF) return true
        }
        return false
    }

    /**
     * 当手动搜索未命中时，根据本地中英别名表推荐最接近的完整关键词（支持前缀包含与多字重合），
     * 例如输入「超级马里」→ 推荐「超级马里奥合集」「超级马里奥世界」「超级马里奥兄弟」等。
     */
    fun suggestKeywords(raw: String, limit: Int = 8): List<String> {
        val q = TitleNormalizer.stripRomTags(raw).replace(Regex("""[-:：·—_\s]+"""), "").trim()
        if (q.length < 2) return emptyList()
        val tbl = table()

        val out = LinkedHashSet<String>(limit)
        val seenEn = HashSet<String>(limit * 2)
        if (containsCjk(q)) {
            val allPairs = ArrayList<Pair<String, String>>(tbl.exactGames.size + tbl.seriesPrefixes.size)
            allPairs.addAll(tbl.exactGames)
            allPairs.addAll(tbl.seriesPrefixes)
            // 短词/核心正统作优先，且按英文目标去重，避免同一游戏多个中文别名占满推荐位
            val byLen = allPairs.sortedBy { it.first.length }

            fun tryAdd(zh: String, en: String): Boolean {
                if (seenEn.add(en.lowercase())) {
                    out += zh
                }
                return out.size >= limit
            }

            // 1) 优先：完整前缀匹配（如「超级马里」→「超级马里奥」「超级马里奥世界」「超级马里奥合集」…）
            for ((zh, en) in byLen) {
                val c = zh.replace(" ", "")
                if (c != q && c.startsWith(q, ignoreCase = true) && tryAdd(zh, en)) {
                    return out.toList()
                }
            }
            // 2) 次优先：子串包含匹配（如「马里」→「超级马里奥…」）
            for ((zh, en) in byLen) {
                val c = zh.replace(" ", "")
                if (c != q && c.contains(q, ignoreCase = true) && tryAdd(zh, en)) {
                    return out.toList()
                }
            }
            // 3) 兜底：至少 2 个汉字重合（如「马里合集」「宝可金」）
            val qChars = q.filter { it.code in 0x4E00..0x9FFF }.toSet()
            if (qChars.size >= 2) {
                val scored = ArrayList<Triple<String, String, Int>>()
                for ((zh, en) in byLen) {
                    if (en.lowercase() in seenEn) continue
                    val overlap = zh.count { it in qChars }
                    if (overlap >= 2) {
                        scored += Triple(zh, en, overlap)
                    }
                }
                scored.sortWith(compareByDescending<Triple<String, String, Int>> { it.third }.thenBy { it.first.length })
                for ((zh, en, _) in scored) {
                    if (tryAdd(zh, en)) break
                }
            }
        } else {
            val qLower = q.lowercase()
            val allEn = LinkedHashSet<String>()
            for ((_, en) in tbl.exactGames) allEn += en
            for ((_, en) in tbl.seriesPrefixes) allEn += en
            for (en in allEn) {
                val c = en.lowercase().replace(Regex("""[^a-z0-9]"""), "")
                if (c != qLower && c.contains(qLower)) {
                    out += en
                    if (out.size >= limit) break
                }
            }
        }
        return out.toList()
    }
}
