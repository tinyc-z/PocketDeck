package com.pocketdeck.launcher.data

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 统一的标题清洗、规范化、罗马数字等价与轻量级模糊匹配工具。
 * 供 RomScanner / CoverSource / MetadataRepository / ScraperClient 共用，消除重复与不一致实现。
 *
 * 修复的问题：
 * 1. Unicode NFD 去音标折叠（`Pokémon` → `pokemon`、`Ōkami` → `okami`），不再丢字母；
 * 2. 保留日文假名（平假名/片假名）与韩文音节，不再清空纯日韩 ROM 名；
 * 3. 修复 `, The` 误匹配 `"Theme"/"Theory"` 的 bug（严格要求词边界）；
 * 4. 剔除常见整合包前导编号（`001 - `、`[0123]`）与中文汉化/版本标签（`汉化版`/`简中`/`v1.2` 等）；
 * 5. 提供倒排词索引 + Token Jaccard + 编辑距离综合打分，替代死板的去空格精确相等。
 */
object TitleNormalizer {

    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val BRACKET_TAGS = Regex("""\s*(?:\([^)]*\)|\[[^\]]*\]|\{[^}]*\}|（[^）]*）|【[^】]*】)""")
    private val LEADING_INDEX = Regex("""^(?:No\.?\s*\d{1,5}\s*(?:[-._、)\]]\s*|\s+)|\d{1,5}\s*[-._、)\]]\s*)""", RegexOption.IGNORE_CASE)
    private val CJK_PACK_TAGS = Regex(
        """(?i)(?:\s*[-_·]?\s*(?:完全汉化版?|完美汉化版?|中文汉化版?|汉化修正版?|简体中文版?|繁体中文版?|官方中文版?|中文版|汉化版|简中版?|繁中版?|简体|繁体|汉化|中文|无敌版|修改版|改版|破解版|测试版|金手指版|修正版|修正|加强版|最终版|典藏版|整合版|chs|cht|hack|v\d+(?:\.\d+)+|rev\s*[a-z0-9]+))+$""",
    )
    private val ARTICLE_SUFFIX = Regex("""^(.+?),\s*(The|A|An)(?=$|\s|[:\-—])(.*)$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")
    private val TRAILING_EDITION_WORDS = Regex("""\s+(?:version|edition)$""")
    // 保留 ASCII 字母数字、CJK 统一汉字、日文平假名/片假名、韩文音节
    private val NON_WORD_SPACED = Regex("""[^a-z0-9\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af]+""")
    private val NON_WORD_COMPACT = Regex("""[^a-z0-9\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af]""")

    private val ROMAN_TO_ARABIC = listOf(
        Regex("""\bX\b""") to "10",
        Regex("""\bIX\b""") to "9",
        Regex("""\bVIII\b""") to "8",
        Regex("""\bVII\b""") to "7",
        Regex("""\bVI\b""") to "6",
        Regex("""\bIV\b""") to "4",
        Regex("""\bIII\b""") to "3",
        Regex("""\bII\b""") to "2",
        Regex("""\bV\b""") to "5",
    )

    private val ARABIC_TO_ROMAN = listOf(
        Regex("""\b10\b""") to "X",
        Regex("""\b9\b""") to "IX",
        Regex("""\b8\b""") to "VIII",
        Regex("""\b7\b""") to "VII",
        Regex("""\b6\b""") to "VI",
        Regex("""\b5\b""") to "V",
        Regex("""\b4\b""") to "IV",
        Regex("""\b3\b""") to "III",
        Regex("""\b2\b""") to "II",
    )

    /** 去掉拉丁音标符号：`Pokémon` → `Pokemon`，`Ōkami` → `Okami`，同时完整保留日文假名浊音与韩文音节。 */
    fun foldDiacritics(input: String): String {
        if (input.isEmpty()) return input
        var hasLatinExtended = false
        for (i in input.indices) {
            val c = input[i].code
            if ((c in 0x00C0..0x024F) || (c in 0x1E00..0x1EFF) || (c in 0x0300..0x036F)) {
                hasLatinExtended = true
                break
            }
        }
        if (!hasLatinExtended) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val c = ch.code
            if ((c in 0x00C0..0x024F) || (c in 0x1E00..0x1EFF)) {
                val nfd = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
                sb.append(DIACRITICS.replace(nfd, ""))
            } else if (c !in 0x0300..0x036F) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun hasBracketChars(s: String): Boolean {
        for (i in s.indices) {
            when (s[i]) {
                '(', '[', '{', '（', '【' -> return true
            }
        }
        return false
    }

    private fun mayHaveCjkOrPackSuffix(s: String): Boolean {
        for (i in s.indices) {
            if (s[i].code >= 0x80) return true
        }
        val last = s.lastOrNull()?.lowercaseChar() ?: return false
        return last.isDigit() ||
            s.endsWith("chs", ignoreCase = true) ||
            s.endsWith("cht", ignoreCase = true) ||
            s.endsWith("hack", ignoreCase = true) ||
            s.contains("rev", ignoreCase = true)
    }

    private fun isWordChar(c: Char): Boolean {
        val code = c.code
        return (code in 'a'.code..'z'.code) ||
            (code in '0'.code..'9'.code) ||
            (code in 0x3040..0x30FF) ||
            (code in 0x3400..0x4DBF) ||
            (code in 0x4E00..0x9FFF) ||
            (code in 0xAC00..0xD7AF)
    }

    /** 零正则单遍扫描：把非词字符折叠为单个空格并 trim，避免大批量建索引时产生海量 Regex/Matcher 垃圾对象。 */
    private fun collapseNonWordSpaced(lowered: String): String {
        val sb = StringBuilder(lowered.length)
        var prevSpace = true
        for (i in lowered.indices) {
            val ch = lowered[i]
            if (isWordChar(ch)) {
                sb.append(ch)
                prevSpace = false
            } else if (!prevSpace) {
                sb.append(' ')
                prevSpace = true
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
            sb.setLength(sb.length - 1)
        }
        return sb.toString()
    }

    /**
     * 剥离 ROM 括号标签、前导编号与常见汉化包后缀，并把 `, The` 冠词归位。
     * 用于展示名与 API 搜索词。
     */
    fun stripRomTags(raw: String): String {
        var s = raw.replace('_', ' ').trim()
        if (s.isEmpty()) return s
        val first = s[0]
        if (first.isDigit() || first == 'N' || first == 'n') {
            s = LEADING_INDEX.replace(s, "")
        }
        if (hasBracketChars(s)) {
            s = BRACKET_TAGS.replace(s, " ")
        }
        if (mayHaveCjkOrPackSuffix(s)) {
            s = CJK_PACK_TAGS.replace(s, "")
        }
        if (s.contains("  ")) {
            s = WHITESPACE.replace(s, " ")
        }
        s = s.trim()
        if (s.contains(", ")) {
            ARTICLE_SUFFIX.matchEntire(s)?.let { m ->
                s = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
                if (s.contains("  ")) s = WHITESPACE.replace(s, " ")
                s = s.trim()
            }
        }
        return s.ifEmpty { raw.replace('_', ' ').trim() }
    }

    /** 用于保留区域标签的精确匹配键（含括号内信息，仅统一空白与去音标）。 */
    fun exactKey(raw: String): String {
        val folded = foldDiacritics(raw).lowercase()
        return collapseNonWordSpaced(folded)
    }

    /** 用于标题级匹配的规范化空格分词键（已剥离括号/编号/冠词/音标，罗马数字统一转阿拉伯数字）。 */
    fun normalizedTitle(raw: String): String {
        var clean = stripRomTags(raw)
        clean = foldDiacritics(clean)
        clean = romanToArabic(clean)
        return collapseNonWordSpaced(clean.lowercase())
    }

    /** 从已计算好的 [normalizedTitle] 直接生成紧凑无空格键（零正则、避免重复计算）。 */
    fun compactFromNorm(norm: String): String {
        if (!norm.contains(' ')) return norm
        val sb = StringBuilder(norm.length)
        for (i in norm.indices) {
            val ch = norm[i]
            if (ch != ' ') sb.append(ch)
        }
        return sb.toString()
    }

    /** 紧凑无空格匹配键（同样已做去音标、冠词归位与罗马数字统一）。 */
    fun compactKey(raw: String): String = compactFromNorm(normalizedTitle(raw))

    fun romanToArabic(s: String): String {
        var hasRoman = false
        for (i in s.indices) {
            val c = s[i]
            if (c == 'I' || c == 'V' || c == 'X') {
                hasRoman = true
                break
            }
        }
        if (!hasRoman) return s
        var cur = s
        for ((re, num) in ROMAN_TO_ARABIC) {
            cur = re.replace(cur, num)
        }
        return cur
    }

    fun arabicToRoman(s: String): String {
        var cur = s
        for ((re, rom) in ARABIC_TO_ROMAN) {
            cur = re.replace(cur, rom)
        }
        return cur
    }

    /** 拆分检索词元（过滤单字符噪声但保留数字代号如 `2`/`3`）。 */
    fun tokenize(normalized: String): List<String> {
        if (normalized.isBlank()) return emptyList()
        return normalized.split(' ').filter { tok ->
            tok.length >= 2 || (tok.length == 1 && tok[0].isDigit())
        }
    }

    /** 生成一组搜索查询变体（原名、去标签名、罗马数字互换、中文译名扩展）。 */
    fun buildQueryVariants(
        baseName: String,
        title: String,
        officialName: String? = null,
    ): List<String> {
        val out = LinkedHashSet<String>(10)
        fun addWithVariants(src: String?) {
            if (src.isNullOrBlank()) return
            out += src
            val stripped = stripRomTags(src)
            if (stripped.isNotBlank()) {
                out += stripped
                val folded = foldDiacritics(stripped)
                if (folded != stripped) out += folded
                val r2a = romanToArabic(folded)
                if (r2a != folded) out += r2a
                val a2r = arabicToRoman(folded)
                if (a2r != folded) out += a2r
            }
            for (alias in CjkAliases.expandEnglishAliases(src)) {
                out += alias
            }
        }
        addWithVariants(officialName)
        addWithVariants(baseName)
        addWithVariants(title)
        return out.toList()
    }

    /** 返回最适合送给外部搜索 API（IGDB / TGDB / SteamGridDB / Wikidata）的干净英文/原名查询词。 */
    fun bestApiSearchQuery(baseName: String, title: String, officialName: String? = null): String {
        val primary = officialName?.takeIf { it.isNotBlank() }
            ?: title.takeIf { it.isNotBlank() }
            ?: baseName
        val cleaned = foldDiacritics(stripRomTags(primary))
        if (CjkAliases.containsCjk(cleaned)) {
            CjkAliases.expandEnglishAliases(cleaned).firstOrNull()?.let { return it }
        }
        return cleaned.ifBlank { primary }
    }

    /**
     * 计算两个标题之间的综合相似度（0.0 ~ 1.0）。
     * 结合精确一致、Token Jaccard 重合度、前缀包含以及归一化 Levenshtein 距离。
     */
    fun similarity(rawA: String, rawB: String): Double {
        val normA = normalizedTitle(rawA)
        val normB = normalizedTitle(rawB)
        if (normA.isEmpty() || normB.isEmpty()) return 0.0
        if (normA == normB) return 1.0

        val compA = NON_WORD_COMPACT.replace(normA, "")
        val compB = NON_WORD_COMPACT.replace(normB, "")
        if (compA == compB) return 0.98

        // 常见无歧义尾部版本词兼容（如 "Pokemon Gold" ↔ "Pokemon - Gold Version"）
        val baseA = TRAILING_EDITION_WORDS.replace(normA, "")
        val baseB = TRAILING_EDITION_WORDS.replace(normB, "")
        if (baseA.isNotEmpty() && baseA == baseB) return 0.96

        val tokA = tokenize(normA).toSet()
        val tokB = tokenize(normB).toSet()
        val jaccard = if (tokA.isEmpty() || tokB.isEmpty()) {
            0.0
        } else {
            val inter = tokA.count { it in tokB }
            val union = tokA.size + tokB.size - inter
            if (union == 0) 0.0 else inter.toDouble() / union.toDouble()
        }

        // 数字代号不匹配惩罚（防止 "Mega Man 2" 误匹配 "Mega Man 3"）
        val numsA = tokA.filter { it.all(Char::isDigit) }.toSet()
        val numsB = tokB.filter { it.all(Char::isDigit) }.toSet()
        val numPenalty = if (numsA.isNotEmpty() && numsB.isNotEmpty() && numsA != numsB) 0.28 else 0.0

        val maxLen = max(compA.length, compB.length)
        val lenDiff = abs(compA.length - compB.length)
        val editSim = if (maxLen <= 64 && lenDiff <= max(12, maxLen / 2)) {
            1.0 - (levenshtein(compA, compB).toDouble() / maxLen.toDouble())
        } else {
            0.0
        }

        val prefixBonus = if (compA.length >= 6 && compB.length >= 6 &&
            (compA.startsWith(compB) || compB.startsWith(compA))
        ) {
            0.06
        } else {
            0.0
        }

        return (max(jaccard * 0.65 + editSim * 0.35, max(jaccard * 0.92, editSim * 0.90)) + prefixBonus - numPenalty)
            .coerceIn(0.0, 1.0)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            val ca = a[i - 1]
            for (j in 1..n) {
                val cost = if (ca == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost,
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }
}
