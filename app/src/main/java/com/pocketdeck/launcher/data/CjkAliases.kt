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

    private class AliasEntry(
        val zh: String,
        val zhCompact: String,
        val en: String,
    )

    private class AliasTable(
        val exactGames: List<AliasEntry>,
        val seriesPrefixes: List<AliasEntry>,
    )

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var tableRef: SoftReference<AliasTable>? = null

    private val loadLock = Any()

    private const val MAX_EXPANDED_ALIASES = 4
    private val CLEAN_DELIM_REGEX = Regex("""[-:：·—_!！?？\s]+""")
    private val LEADING_SEQUEL_NUM = Regex("""^(\d{1,3}|[IVX]{1,4})(?=$|[^\dA-Za-z])""")
    private val TRAILING_SHORT_NUM = Regex("""(\d{1,3}|[IVX]{1,4})$""")
    private val ENDS_WITH_NUM = Regex("""(?:\b\d{1,4}|\b[IVX]{1,4})$""")

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
        val exact = ArrayList<AliasEntry>(640)
        val series = ArrayList<AliasEntry>(80)
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
                val entry = AliasEntry(zh, zh.replace(" ", ""), en)
                when (rawLine[0]) {
                    'E' -> exact.add(entry)
                    'S' -> series.add(entry)
                }
            }
        }
        return AliasTable(exact, series)
    }

    private fun isCjkChar(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF
    }

    /**
     * 校验词条在清洗后标题中的边界，返回命中结束下标（未命中返回 `-1`）：
     * - 短词（≤2 字，如 `战神`/`功夫`/`零`）与系列前缀禁止紧贴在其它汉字之后命中，防止「飞天战神→God of War」「傲视三国志→Romance of the Three Kingdoms」误配；
     * - 短精确词及单字前缀后若紧跟无分隔汉字同样拒绝（如「战神传说」「零式战机」），仅放行完整词、数字或带分隔副标题。
     */
    private fun findAliasMatchEnd(
        cleaned: String,
        compact: String,
        entry: AliasEntry,
        isSeries: Boolean,
    ): Int {
        val zhCompact = entry.zhCompact
        val idx = compact.indexOf(zhCompact, ignoreCase = true)
        if (idx < 0) return -1
        val endIdx = idx + zhCompact.length
        val shortKey = zhCompact.length <= 2
        if ((shortKey || isSeries) && idx > 0 && isCjkChar(compact[idx - 1])) {
            return -1
        }
        if (shortKey && (!isSeries || zhCompact.length == 1) && endIdx < compact.length) {
            val clIdx = cleaned.indexOf(entry.zh, ignoreCase = true)
            if (clIdx >= 0) {
                val clEnd = clIdx + entry.zh.length
                if (clEnd < cleaned.length && isCjkChar(cleaned[clEnd])) {
                    return -1
                }
            } else if (isCjkChar(compact[endIdx])) {
                return -1
            }
        }
        return endIdx
    }

    /** 若 [raw] 包含中文字符，返回 0..4 条英文候选标题（支持同中文多平台译名与副标题前的续作编号提取）。 */
    fun expandEnglishAliases(raw: String): List<String> {
        if (raw.isBlank() || !containsCjk(raw)) return emptyList()
        val cleaned = TitleNormalizer.stripRomTags(raw)
            .replace(CLEAN_DELIM_REGEX, " ")
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        val compact = cleaned.replace(" ", "")
        val tbl = table()

        val out = LinkedHashSet<String>(MAX_EXPANDED_ALIASES)

        fun addWithSequel(en: String, after: String, isSeries: Boolean) {
            val leading = LEADING_SEQUEL_NUM.find(after)?.groupValues?.getOrNull(1)
            val num = leading ?: if (isSeries && after.length <= 4) {
                TRAILING_SHORT_NUM.find(after)?.groupValues?.getOrNull(1)
            } else {
                null
            }
            if (!num.isNullOrBlank() && !ENDS_WITH_NUM.containsMatchIn(en)) {
                out += "$en $num"
            }
            if (out.size < MAX_EXPANDED_ALIASES) {
                out += en
            }
        }

        // 1) 精确/副标题词表（按字长降序且同词相邻分组）：锁定最长命中的中文键并收集其全部跨平台英文候选
        var matchedKey: String? = null
        for (entry in tbl.exactGames) {
            if (matchedKey != null) {
                if (!entry.zhCompact.equals(matchedKey, ignoreCase = true)) {
                    if (entry.zhCompact.length < matchedKey.length) break
                    continue
                }
            }
            val endIdx = findAliasMatchEnd(cleaned, compact, entry, isSeries = false)
            if (endIdx >= 0) {
                if (matchedKey == null) matchedKey = entry.zhCompact
                addWithSequel(entry.en, compact.substring(endIdx), isSeries = false)
                if (out.size >= MAX_EXPANDED_ALIASES) break
            }
        }

        // 2) 系列前缀兜底：提取紧跟在系列名后的续作编号（如「勇者斗恶龙3-罪恶渊源」→「Dragon Quest 3」）
        if (out.isEmpty()) {
            for (entry in tbl.seriesPrefixes) {
                val endIdx = findAliasMatchEnd(cleaned, compact, entry, isSeries = true)
                if (endIdx >= 0) {
                    addWithSequel(entry.en, compact.substring(endIdx), isSeries = true)
                    if (out.size >= MAX_EXPANDED_ALIASES) break
                }
            }
        }
        return out.toList()
    }

    fun containsCjk(s: String): Boolean {
        for (ch in s) {
            if (isCjkChar(ch)) return true
        }
        return false
    }

    /**
     * 当手动搜索未命中时，根据本地中英别名表推荐最接近的完整关键词（支持前缀包含与多字重合），
     * 例如输入「超级马里」→ 推荐「超级马里奥合集」「超级马里奥世界」「超级马里奥兄弟」等。
     */
    fun suggestKeywords(raw: String, limit: Int = 8): List<String> {
        val q = TitleNormalizer.stripRomTags(raw).replace(CLEAN_DELIM_REGEX, "").trim()
        if (q.length < 2) return emptyList()
        val tbl = table()

        val out = LinkedHashSet<String>(limit)
        val seenEn = HashSet<String>(limit * 2)
        if (containsCjk(q)) {
            val allPairs = ArrayList<AliasEntry>(tbl.exactGames.size + tbl.seriesPrefixes.size)
            allPairs.addAll(tbl.exactGames)
            allPairs.addAll(tbl.seriesPrefixes)
            // 短词/核心正统作优先，且按英文目标去重，避免同一游戏多个中文别名占满推荐位
            val byLen = allPairs.sortedBy { it.zh.length }

            fun tryAdd(zh: String, en: String): Boolean {
                if (seenEn.add(en.lowercase())) {
                    out += zh
                }
                return out.size >= limit
            }

            // 1) 优先：完整前缀匹配（如「超级马里」→「超级马里奥」「超级马里奥世界」「超级马里奥合集」…）
            for (entry in byLen) {
                val c = entry.zhCompact
                if (c != q && c.startsWith(q, ignoreCase = true) && tryAdd(entry.zh, entry.en)) {
                    return out.toList()
                }
            }
            // 2) 次优先：子串包含匹配（如「马里」→「超级马里奥…」）
            for (entry in byLen) {
                val c = entry.zhCompact
                if (c != q && c.contains(q, ignoreCase = true) && tryAdd(entry.zh, entry.en)) {
                    return out.toList()
                }
            }
            // 3) 兜底：至少 2 个汉字重合（如「马里合集」「宝可金」）
            val qChars = q.filter { it.code in 0x4E00..0x9FFF }.toSet()
            if (qChars.size >= 2) {
                val scored = ArrayList<Triple<String, String, Int>>()
                for (entry in byLen) {
                    if (entry.en.lowercase() in seenEn) continue
                    val overlap = entry.zh.count { it in qChars }
                    if (overlap >= 2) {
                        scored += Triple(entry.zh, entry.en, overlap)
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
            for (entry in tbl.exactGames) allEn += entry.en
            for (entry in tbl.seriesPrefixes) allEn += entry.en
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
