package com.pocketdeck.launcher.data

import com.pocketdeck.launcher.data.model.GameMetadata
import java.io.BufferedInputStream
import java.io.File

internal data class LocalMediaEntry(
    val coverPath: String? = null,
    val metadata: GameMetadata? = null,
)

/**
 * 流式解析 Pegasus / 天马G `metadata.pegasus.txt` 与 `metadata.txt`（零 DOM、16KB 缓冲），
 * 返回标准化相对路径/文件名（小写）到封面与元数据的映射。
 */
internal object PegasusMetadataParser {

    private const val MAX_FILE_BYTES = 16L * 1024 * 1024

    private val BOXART_ASSET_KEYS = setOf(
        "assets.boxfront",
        "assets.box_front",
        "assets.box2dfront",
        "assets.boxart",
        "assets.cover",
    )

    private val FALLBACK_ASSET_KEYS = setOf(
        "assets.mix",
        "assets.miximage",
        "assets.screenshot",
        "assets.titlescreen",
        "assets.title",
        "assets.logo",
    )

    private val ISO_DATE_REGEX = Regex("""^(\d{4})(?:-(\d{1,2})(?:-(\d{1,2}))?)?$""")

    private fun parseReleaseDate(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        ISO_DATE_REGEX.matchEntire(trimmed)?.let { m ->
            val y = m.groupValues[1]
            val mo = m.groupValues[2]
            val d = m.groupValues[3]
            return when {
                mo.isNotEmpty() && d.isNotEmpty() -> "$y-${mo.padStart(2, '0')}-${d.padStart(2, '0')}"
                mo.isNotEmpty() -> "$y-${mo.padStart(2, '0')}"
                else -> y
            }
        }
        return RomScanner.formatGamelistDate(trimmed)
    }

    fun parse(baseDir: File, file: File): Map<String, LocalMediaEntry> {
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return emptyMap()
        val result = HashMap<String, LocalMediaEntry>()

        var inGame = false
        var activeKey: String? = null
        val files = ArrayList<String>(4)
        val developers = ArrayList<String>(2)
        val publishers = ArrayList<String>(2)
        val genres = ArrayList<String>(2)
        var releaseRaw: String? = null
        val descLines = ArrayList<String>()
        val summaryLines = ArrayList<String>()
        var boxartAsset: String? = null
        var fallbackAsset: String? = null

        fun resetBlock() {
            activeKey = null
            files.clear()
            developers.clear()
            publishers.clear()
            genres.clear()
            releaseRaw = null
            descLines.clear()
            summaryLines.clear()
            boxartAsset = null
            fallbackAsset = null
        }

        fun resolveAssetPath(rawAsset: String?): String? {
            val cleaned = rawAsset?.trim()?.removePrefix("./")?.replace('\\', '/')?.takeIf { it.isNotEmpty() }
                ?: return null
            val f = if (File(cleaned).isAbsolute) File(cleaned) else File(baseDir, cleaned)
            return f.takeIf { it.isFile }?.path
        }

        fun formatTextLines(lines: List<String>): String? {
            if (lines.isEmpty()) return null
            val joined = lines.joinToString("\n") { line -> if (line == ".") "" else line }.trim()
            return joined.takeIf { it.isNotEmpty() }
        }

        fun flushBlock() {
            if (!inGame || files.isEmpty()) {
                resetBlock()
                return
            }
            val resolvedCover = resolveAssetPath(boxartAsset) ?: resolveAssetPath(fallbackAsset)
            val description = formatTextLines(descLines) ?: formatTextLines(summaryLines)
            val meta = GameMetadata(
                releaseDate = parseReleaseDate(releaseRaw),
                genre = genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
                developer = developers.takeIf { it.isNotEmpty() }?.joinToString(", "),
                publisher = publishers.takeIf { it.isNotEmpty() }?.joinToString(", "),
                description = description,
            )
            if (resolvedCover == null && meta.isEmpty) {
                resetBlock()
                return
            }
            val entry = LocalMediaEntry(
                coverPath = resolvedCover,
                metadata = meta.takeIf { !it.isEmpty },
            )
            for (rawFile in files) {
                val normRel = rawFile.replace('\\', '/').removePrefix("./").trim()
                if (normRel.isEmpty()) continue
                val relLower = normRel.lowercase()
                result[relLower] = entry
                val relNoExt = normRel.substringBeforeLast('.', "").lowercase()
                if (relNoExt.isNotEmpty() && '.' in normRel.substringAfterLast('/')) {
                    result[relNoExt] = entry
                }
                val fileName = normRel.substringAfterLast('/')
                if (fileName.isNotEmpty()) {
                    result.putIfAbsent(fileName.lowercase(), entry)
                    val fileNoExt = fileName.substringBeforeLast('.', "").lowercase()
                    if (fileNoExt.isNotEmpty()) {
                        result.putIfAbsent(fileNoExt, entry)
                    }
                }
            }
            resetBlock()
        }

        fun handleFieldValue(key: String, rawValue: String, isContinuation: Boolean) {
            val trimmed = rawValue.trim()
            when (key) {
                "file" -> if (!isContinuation && trimmed.isNotEmpty()) files += trimmed
                "files" -> if (trimmed.isNotEmpty()) files += trimmed
                "developer", "developers" -> if (trimmed.isNotEmpty()) developers += trimmed
                "publisher", "publishers" -> if (trimmed.isNotEmpty()) publishers += trimmed
                "genre", "genres" -> if (trimmed.isNotEmpty()) genres += trimmed
                "release" -> if (!isContinuation && trimmed.isNotEmpty() && releaseRaw == null) releaseRaw = trimmed
                "description" -> if (trimmed.isNotEmpty() || isContinuation) descLines += trimmed
                "summary" -> if (trimmed.isNotEmpty() || isContinuation) summaryLines += trimmed
                else -> {
                    if (key in BOXART_ASSET_KEYS && boxartAsset == null && trimmed.isNotEmpty()) {
                        boxartAsset = trimmed
                    } else if (key in FALLBACK_ASSET_KEYS && fallbackAsset == null && trimmed.isNotEmpty()) {
                        fallbackAsset = trimmed
                    }
                }
            }
        }

        runCatching {
            BufferedInputStream(file.inputStream(), 16 * 1024).bufferedReader(Charsets.UTF_8).useLines { lines ->
                var firstLine = true
                lines.forEach { rawLine ->
                    val line = if (firstLine) {
                        firstLine = false
                        rawLine.removePrefix("\uFEFF")
                    } else {
                        rawLine
                    }
                    val trimmedStart = line.trimStart()
                    if (trimmedStart.startsWith("#")) return@forEach

                    val isIndented = line.isNotEmpty() && (line[0] == ' ' || line[0] == '\t')
                    if (isIndented) {
                        val key = activeKey
                        if (inGame && key != null) {
                            handleFieldValue(key, trimmedStart, isContinuation = true)
                        }
                        return@forEach
                    }

                    if (trimmedStart.isEmpty()) {
                        activeKey = null
                        return@forEach
                    }

                    val colonIdx = line.indexOf(':')
                    if (colonIdx <= 0) {
                        activeKey = null
                        return@forEach
                    }
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1)
                    if (key == "game") {
                        flushBlock()
                        inGame = true
                        activeKey = null
                    } else if (key == "collection" || key == "shortname" || key == "extensions" || key == "extension" || key == "launch") {
                        flushBlock()
                        inGame = false
                        activeKey = null
                    } else if (inGame) {
                        activeKey = key
                        handleFieldValue(key, value, isContinuation = false)
                    } else {
                        activeKey = null
                    }
                }
            }
            flushBlock()
        }
        return result
    }
}
