package com.pocketdeck.launcher.lan

import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.data.SystemTabConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Web 管理端标签分类配置严格校验器：
 * 确保任何来自浏览器的 JSON 数据在写入掌机 UserStore 前经过类型、长度、字符集、枚举与引用完整性校验，
 * 防止畸形或越界配置破坏系统状态。
 */
object CategoryValidator {
    private const val MAX_CATEGORIES = 100
    private const val MAX_NAME_LEN = 40
    private const val MAX_SYSTEM_NAME_LEN = 30
    private const val MAX_SOURCES_PER_CAT = 200
    private const val MAX_GAMES_PER_CAT = 5000
    private val SYSTEM_IDS = setOf("now", "favs", "other")
    private val SAFE_ID_REGEX = Regex("""^[a-zA-Z0-9:_-]{1,80}$""")
    private val SAFE_PLATFORM_REGEX = Regex("""^[a-zA-Z0-9_-]{1,40}$""")
    private val CONTROL_CHARS = Regex("""[\u0000-\u001F\u007F]""")

    data class ValidatedConfig(
        val categories: List<Category>,
        val tabOrder: List<String>,
        val hiddenTabs: Set<String>,
        val nowTab: Boolean,
        val favsTab: Boolean,
        val systemTabs: Map<String, SystemTabConfig>,
    )

    sealed interface Result {
        data class Ok(val config: ValidatedConfig) : Result
        data class Error(val code: String, val message: String) : Result
    }

    fun validate(rawJson: String, existingSystemTabs: Map<String, SystemTabConfig>): Result {
        val root = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return Result.Error("invalid_json", "Payload must be a valid JSON object")

        val catsArr = root.optJSONArray("categories")
            ?: return Result.Error("missing_categories", "Field 'categories' must be a JSON array")
        if (catsArr.length() > MAX_CATEGORIES) {
            return Result.Error("too_many_categories", "Maximum $MAX_CATEGORIES categories allowed")
        }

        val seenCatIds = HashSet<String>()
        val validatedCats = ArrayList<Category>(catsArr.length())
        for (i in 0 until catsArr.length()) {
            val item = catsArr.optJSONObject(i)
                ?: return Result.Error("invalid_category_item", "Category at index $i must be an object")

            val rawId = item.optString("id", "").trim()
            val catId = if (rawId.isEmpty()) {
                "cat:web_${UUID.randomUUID().toString().take(8)}"
            } else {
                if (rawId in SYSTEM_IDS) {
                    return Result.Error("reserved_category_id", "Category id '$rawId' is reserved")
                }
                if (!SAFE_ID_REGEX.matches(rawId)) {
                    return Result.Error("invalid_category_id", "Category id '$rawId' contains illegal characters")
                }
                rawId
            }
            if (!seenCatIds.add(catId)) {
                return Result.Error("duplicate_category_id", "Duplicate category id '$catId'")
            }

            val cleanName = sanitizeDisplayName(item.optString("name", ""), MAX_NAME_LEN)
            if (cleanName.isEmpty()) {
                return Result.Error("empty_category_name", "Category name cannot be empty")
            }

            val sortStr = item.optString("sortOrder", CategorySortOrder.TitleAsc.name).trim()
            val sortOrder = CategorySortOrder.entries.firstOrNull { it.name == sortStr }
                ?: return Result.Error("invalid_sort_order", "Unknown sortOrder '$sortStr'")

            val sourcesArr = item.optJSONArray("sources") ?: JSONArray()
            if (sourcesArr.length() > MAX_SOURCES_PER_CAT) {
                return Result.Error("too_many_sources", "Category '$cleanName' exceeds max platform count")
            }
            val sources = LinkedHashSet<String>()
            for (j in 0 until sourcesArr.length()) {
                val rawSrc = sourcesArr.opt(j) as? String
                    ?: return Result.Error("invalid_source_type", "Platform source must be a string")
                val src = rawSrc.trim().lowercase()
                if (!SAFE_PLATFORM_REGEX.matches(src)) {
                    return Result.Error("invalid_source_id", "Invalid platform id '$rawSrc'")
                }
                sources += src
            }

            val gamesArr = item.optJSONArray("games") ?: JSONArray()
            if (gamesArr.length() > MAX_GAMES_PER_CAT) {
                return Result.Error("too_many_games", "Category '$cleanName' exceeds max manual games count")
            }
            val games = LinkedHashSet<String>()
            for (j in 0 until gamesArr.length()) {
                val rawGid = gamesArr.opt(j) as? String
                    ?: return Result.Error("invalid_game_type", "Game id must be a string")
                val gid = rawGid.trim()
                if (gid.isEmpty() || gid.length > 1024 || CONTROL_CHARS.containsMatchIn(gid) || "/../" in gid) {
                    return Result.Error("invalid_game_id", "Invalid game id at index $j")
                }
                games += gid
            }

            validatedCats += Category(
                id = catId,
                name = cleanName,
                sources = sources.toList(),
                games = games,
                sortOrder = sortOrder,
            )
        }

        val validTabIds = SYSTEM_IDS + seenCatIds

        val sysObj = root.optJSONObject("systemTabs")
        val nextSystemTabs = existingSystemTabs.toMutableMap()
        if (sysObj != null) {
            for (sysId in SYSTEM_IDS) {
                val cfgObj = sysObj.optJSONObject(sysId) ?: continue
                val customName = sanitizeDisplayName(cfgObj.optString("customName", ""), MAX_SYSTEM_NAME_LEN)
                val followLang = if (customName.isEmpty()) true else cfgObj.optBoolean("followSystemLanguage", true)
                val sortStr = cfgObj.optString("sortOrder", "").trim()
                val sortOrder = when {
                    sortStr.isEmpty() || (sysId == "now" && sortStr == "Recent") -> null
                    else -> CategorySortOrder.entries.firstOrNull { it.name == sortStr }
                        ?: return Result.Error("invalid_sort_order", "Unknown sortOrder '$sortStr'")
                }
                nextSystemTabs[sysId] = SystemTabConfig(
                    customName = customName,
                    followSystemLanguage = followLang,
                    sortOrder = sortOrder,
                )
            }
        }

        val hiddenArr = root.optJSONArray("hiddenTabs")
        val hiddenTabs = LinkedHashSet<String>()
        if (hiddenArr != null) {
            for (i in 0 until hiddenArr.length()) {
                val id = (hiddenArr.opt(i) as? String)?.trim().orEmpty()
                if (id in validTabIds && id !in setOf("now", "favs")) {
                    hiddenTabs += id
                }
            }
        }
        val nowTab = if (root.has("nowTab")) root.optBoolean("nowTab", true) else true
        val favsTab = if (root.has("favsTab")) root.optBoolean("favsTab", true) else true

        val orderArr = root.optJSONArray("tabOrder")
        val tabOrder = LinkedHashSet<String>()
        if (orderArr != null) {
            for (i in 0 until orderArr.length()) {
                val id = (orderArr.opt(i) as? String)?.trim().orEmpty()
                if (id in validTabIds) {
                    tabOrder += id
                }
            }
        }
        // 补齐任何未列出的有效标签，保证顺序表完整
        listOf("now", "favs").forEach { tabOrder += it }
        seenCatIds.forEach { tabOrder += it }
        tabOrder += "other"

        return Result.Ok(
            ValidatedConfig(
                categories = validatedCats,
                tabOrder = tabOrder.toList(),
                hiddenTabs = hiddenTabs,
                nowTab = nowTab,
                favsTab = favsTab,
                systemTabs = nextSystemTabs,
            ),
        )
    }

    private fun sanitizeDisplayName(raw: String, maxLen: Int): String =
        raw.replace(CONTROL_CHARS, "").trim().take(maxLen)
}
