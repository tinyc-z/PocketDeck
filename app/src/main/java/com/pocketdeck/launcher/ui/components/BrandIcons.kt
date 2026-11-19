package com.pocketdeck.launcher.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.pocketdeck.launcher.R

/**
 * PocketDeck 统一品牌图标系统：App 主图标、游戏库、游戏快传三者共用同一块
 * 「日落橙 → 珊瑚 → 亮粉」渐变底板（ic_launcher_background）与同一套白色主体 +
 * 深紫细节 + 黄/粉点缀的扁平游戏风格字形。
 *
 * 字形使用 108dp 自适应图标网格绘制，这里按启动器的裁切方式取中心 72dp 区域，
 * 因此应用内看到的效果与桌面图标完全一致。
 */
@Composable
fun BrandTileIcon(
    @DrawableRes glyph: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val full = size * 1.5f
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.requiredSize(full),
        )
        Image(
            painter = painterResource(glyph),
            contentDescription = null,
            modifier = Modifier.requiredSize(full),
        )
    }
}

/** PocketDeck 应用主图标（竖版口袋掌机）。 */
@Composable
fun PocketDeckAppIcon(size: Dp, modifier: Modifier = Modifier) =
    BrandTileIcon(R.drawable.ic_launcher_foreground, size, modifier)
