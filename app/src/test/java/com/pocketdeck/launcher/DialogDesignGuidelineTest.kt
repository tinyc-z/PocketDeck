package com.pocketdeck.launcher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.ui.components.DeckDialogSize
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 弹窗尺寸与圆角设计规范守卫测试（对应 docs/DESIGN_GUIDELINES.md 第 2 节）。
 *
 * 防止后续新增或修改弹窗时再次引入：
 * 1. 零散魔法宽度数字（如 400.dp / 420.dp / 460.dp / 480.dp / 560.dp / 748.dp）；
 * 2. 写死固定宽度 Modifier.width(NNN.dp)（窄屏会溢出）；
 * 3. 超过全 App 绝对上限 640.dp 的弹窗；
 * 4. 与 DeckBrand.RadiusXL (22.dp) 不一致的弹窗圆角。
 */
class DialogDesignGuidelineTest {

    private fun uiSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/pocketdeck/launcher/ui"),
            File("app/src/main/java/com/pocketdeck/launcher/ui"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate ui source directory from ${File(".").absolutePath}")
    }

    @Test
    fun dialogSizeTokensMatchDesignGuideline() {
        assertEquals(340.dp, DeckDialogSize.WidthS)
        assertEquals(440.dp, DeckDialogSize.WidthM)
        assertEquals(520.dp, DeckDialogSize.WidthL)
        assertEquals(640.dp, DeckDialogSize.WidthXL)
        assertEquals(RoundedCornerShape(DeckBrand.RadiusXL), DeckShapes.menuPanel)
    }

    @Test
    fun allDialogPanelCardsUseDeckDialogSizeAndResponsiveWidth() {
        val uiRoot = uiSourceRoot()
        val fixedWidthRegex = Regex("""\.width\(\s*\d+(?:\.\d+)?\.dp\s*\)""")
        val magicMaxWidthRegex = Regex("""\.widthIn\([^)]*max\s*=\s*\d+(?:\.\d+)?\.dp""")
        val tokenRegex = Regex("""DeckDialogSize\.Width(S|M|L|XL)\b""")
        val fillMaxWidthRegex = Regex("""\.fillMaxWidth\(""")

        val violations = mutableListOf<String>()

        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "DialogCloseButton.kt" }
            .forEach { file ->
                val relPath = file.relativeTo(uiRoot).path
                val lines = file.readLines()
                lines.forEachIndexed { idx, line ->
                    if (line.contains("DialogPanelCard(") && !line.trimStart().startsWith("fun ")) {
                        val window = lines.subList(idx, (idx + 14).coerceAtMost(lines.size)).joinToString("\n")
                        if (!tokenRegex.containsMatchIn(window)) {
                            violations += "$relPath:${idx + 1}: DialogPanelCard 未使用 DeckDialogSize.Width(S|M|L|XL)"
                        }
                        if (!fillMaxWidthRegex.containsMatchIn(window)) {
                            violations += "$relPath:${idx + 1}: DialogPanelCard 缺少 .fillMaxWidth()，窄屏或短内容时可能收缩变形"
                        }
                        if (fixedWidthRegex.containsMatchIn(window)) {
                            violations += "$relPath:${idx + 1}: DialogPanelCard 禁止写死固定 .width(N.dp)，请改用 .widthIn(max = DeckDialogSize.WidthX).fillMaxWidth()"
                        }
                        if (magicMaxWidthRegex.containsMatchIn(window)) {
                            violations += "$relPath:${idx + 1}: DialogPanelCard 禁止在 widthIn(max = ...) 中使用魔法数字，请改用 DeckDialogSize 常量"
                        }
                    }
                }
            }

        assertTrue(
            "发现违反弹窗尺寸规范的 DialogPanelCard：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun allCustomDialogContainersAvoidHardcodedWidthsAndExceedingMax() {
        val uiRoot = uiSourceRoot()
        val fixedDialogWidthRegex = Regex("""\.width\(\s*([3-9]\d{2})(?:\.\d+)?\.dp\s*\)""")
        val magicMaxWidthRegex = Regex("""\.widthIn\([^)]*max\s*=\s*(\d+)(?:\.\d+)?\.dp""")
        val coerceWidthRegex = Regex("""\.coerceAtMost\(\s*(\d+)(?:\.\d+)?\.dp\s*\)""")
        val nonStandardDialogRadiusRegex = Regex("""RoundedCornerShape\(\s*(16|18|20|24)(?:\.\d+)?\.dp\s*\)""")
        val scrimClickableRegex = Regex("""\.clickable\((?!enabled\s*=\s*(?:true|false),\s*onClick\s*=\s*\{\})""")

        val violations = mutableListOf<String>()

        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "DialogCloseButton.kt" }
            .forEach { file ->
                val relPath = file.relativeTo(uiRoot).path
                val lines = file.readLines()
                lines.forEachIndexed { idx, line ->
                    val isDialogSurface = line.contains(".clip(DeckShapes.menuPanel)") ||
                        (line.contains(".background(DeckColors.DialogBg)") && !line.contains("trackColor"))
                    if (isDialogSurface) {
                        val start = (idx - 10).coerceAtLeast(0)
                        val end = (idx + 3).coerceAtMost(lines.size)
                        val modifierWindow = lines.subList(start, end)
                        modifierWindow.forEachIndexed { offset, mLine ->
                            val lineNo = start + offset + 1
                            fixedDialogWidthRegex.find(mLine)?.let { m ->
                                violations += "$relPath:$lineNo: 弹窗容器禁止写死固定宽度 ${m.value}，请改用 .widthIn(max = DeckDialogSize.WidthX).fillMaxWidth()"
                            }
                            magicMaxWidthRegex.find(mLine)?.let { m ->
                                val dpVal = m.groupValues[1].toInt()
                                if (dpVal >= 300) {
                                    violations += "$relPath:$lineNo: 弹窗容器禁止使用魔法宽度 ${m.value}，请改用 DeckDialogSize.Width(S|M|L|XL)"
                                }
                            }
                            nonStandardDialogRadiusRegex.find(mLine)?.let { m ->
                                violations += "$relPath:$lineNo: 弹窗外轮廓禁止手写非规范圆角 ${m.value}，请统一使用 DeckShapes.menuPanel 或 RoundedCornerShape(DeckBrand.RadiusXL) (22.dp)"
                            }
                        }
                    }

                    // 防止任何 UI 文件通过 coerceAtMost(> 640.dp) 绕过 WidthXL 上限
                    coerceWidthRegex.find(line)?.let { m ->
                        val dpVal = m.groupValues[1].toInt()
                        if (dpVal > 640) {
                            violations += "$relPath:${idx + 1}: 弹窗宽度上限 ${dpVal}.dp 超过全 App 绝对上限 DeckDialogSize.WidthXL (640.dp)"
                        }
                    }

                    // 防止弹窗半透明遮罩层漏用 scrimTapDismiss 导致点击水波纹
                    if (line.contains(".background(DeckColors.CardShadow)")) {
                        val scrimEnd = (idx + 3).coerceAtMost(lines.size)
                        for (sIdx in (idx + 1) until scrimEnd) {
                            if (scrimClickableRegex.containsMatchIn(lines[sIdx])) {
                                violations += "$relPath:${sIdx + 1}: 弹窗遮罩请统一使用 .scrimTapDismiss(...) 替代 .clickable(...)，避免点击遮罩出现水波纹"
                            }
                        }
                    }
                }
            }

        assertTrue(
            "发现违反弹窗尺寸规范的自定义弹窗容器：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun uiCodeConformsToDesignGuidelineBaseline() {
        val uiRoot = uiSourceRoot()
        val bareTextStyleRegex = Regex("""\bTextStyle\(""")
        val directShadowRegex = Regex("""\.shadow\(""")
        val unsafeRequestFocusRegex = Regex("""\.requestFocus\(\)""")
        val hardcodedStatusHexRegex = Regex(
            """Color\(0xFF(?:DC2626|EF4444|E53935|F44336|10B981|15803D|22C55E|4CAF50|0EA5E9|D97706|F59E0B|B45309)\)""",
            RegexOption.IGNORE_CASE,
        )

        val fullScreenOverlays = setOf(
            "apps/AppsScreen.kt",
            "edit/EditGameScreen.kt",
            "library/GameLibraryScreen.kt",
            "settings/SettingsScreen.kt",
            "settings/ScrapeLogsScreen.kt",
        )

        val violations = mutableListOf<String>()

        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relPath = file.relativeTo(uiRoot).path.replace('\\', '/')
                val content = file.readText()
                val lines = content.lines()

                // 1. 禁止业务 UI 裸写 TextStyle(...) 导致丢失自定义字体（ui/theme 与 DeckMaterialCompat 除外）
                if (!relPath.startsWith("theme/") && relPath != "components/DeckMaterialCompat.kt") {
                    lines.forEachIndexed { idx, line ->
                        if (bareTextStyleRegex.containsMatchIn(line)) {
                            violations += "$relPath:${idx + 1}: 禁止裸写 TextStyle(...)，请基于 DeckType.* 派生"
                        }
                    }
                }

                // 2. 禁止在 Brand.kt 外直接调用 Modifier.shadow(...)，统一走 cardSurface
                if (relPath != "theme/Brand.kt") {
                    lines.forEachIndexed { idx, line ->
                        if (directShadowRegex.containsMatchIn(line)) {
                            violations += "$relPath:${idx + 1}: 禁止直接调用 Modifier.shadow(...)，请使用 Modifier.cardSurface(...)"
                        }
                    }
                }

                // 3. 禁止直接调用 .requestFocus()，统一使用 requestFocusSafely()
                if (relPath != "components/GamepadFocus.kt") {
                    lines.forEachIndexed { idx, line ->
                        if (unsafeRequestFocusRegex.containsMatchIn(line)) {
                            violations += "$relPath:${idx + 1}: 请使用 requestFocusSafely() 替代直接 .requestFocus()"
                        }
                    }
                }

                // 4. 禁止在非 theme/background 模块硬编码已抽离的语义状态色
                if (!relPath.startsWith("theme/") && !relPath.startsWith("components/background/")) {
                    lines.forEachIndexed { idx, line ->
                        hardcodedStatusHexRegex.find(line)?.let { m ->
                            violations += "$relPath:${idx + 1}: 禁止硬编码状态色 ${m.value}，请使用 DeckColors.Status(Danger|Success|Info|Warning)"
                        }
                    }
                }

                // 5. 全屏覆盖页必须挂载 blockOverlayTouchThrough() 防止点击穿透
                if (relPath in fullScreenOverlays && !content.contains(".blockOverlayTouchThrough()")) {
                    violations += "$relPath: 全屏覆盖页根容器缺少 .blockOverlayTouchThrough() 防穿透修饰符"
                }
            }

        assertTrue(
            "发现违反 UI 设计规范基线的代码：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
