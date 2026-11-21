package com.pocketdeck.launcher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt

/**
 * 轻量 Foundation 基础组件层，完全替代 `compose-material3` 依赖，
 * 避免为少数几个基础控件引入整套 M3 Token / Ripple / Scaffold 代码。
 */

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = DeckType.settingsLabel,
) {
    val resolvedColor = color.takeOrElse { style.color.takeOrElse { DeckColors.TextPrimary } }
    val merged = style.merge(
        TextStyle(
            color = resolvedColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeight = lineHeight,
        ),
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = merged,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = DeckType.settingsLabel,
) {
    val resolvedColor = color.takeOrElse { style.color.takeOrElse { DeckColors.TextPrimary } }
    val merged = style.merge(
        TextStyle(
            color = resolvedColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeight = lineHeight,
        ),
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = merged,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = DeckColors.Line,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = DeckColors.Ruby,
    strokeWidth: Dp = 3.dp,
    trackColor: Color = Color.Transparent,
) {
    val reduceMotion = LocalReduceMotion.current
    val startAngle = if (!reduceMotion) {
        val inf = rememberInfiniteTransition(label = "deck_cpi")
        val a by inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 950, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "deck_cpi_angle",
        )
        a
    } else 0f

    Canvas(modifier.defaultMinSize(24.dp, 24.dp)) {
        val sw = strokeWidth.toPx()
        val inset = sw / 2f
        val arcSize = Size((size.width - sw).coerceAtLeast(1f), (size.height - sw).coerceAtLeast(1f))
        if (trackColor != Color.Transparent) {
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = DeckColors.Ruby,
    trackColor: Color = DeckColors.SurfaceAlt,
    @Suppress("UNUSED_PARAMETER") gapSize: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") drawStopIndicator: () -> Unit = {},
) {
    val frac = progress().coerceIn(0f, 1f)
    Box(
        modifier
            .defaultMinSize(minHeight = 4.dp)
            .background(trackColor),
    ) {
        if (frac > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .background(color),
            )
        }
    }
}

@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = DeckColors.Ruby,
    trackColor: Color = DeckColors.SurfaceAlt,
) {
    val reduceMotion = LocalReduceMotion.current
    val phase = if (!reduceMotion) {
        val inf = rememberInfiniteTransition(label = "deck_lpi")
        val p by inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "deck_lpi_phase",
        )
        p
    } else 0.35f

    Canvas(
        modifier
            .defaultMinSize(minWidth = 80.dp, minHeight = 4.dp)
            .background(trackColor),
    ) {
        val barW = size.width * 0.35f
        val x = (size.width + barW) * phase - barW
        val start = x.coerceIn(0f, size.width)
        val end = (x + barW).coerceIn(0f, size.width)
        if (end > start) {
            drawRect(color = color, topLeft = Offset(start, 0f), size = Size(end - start, size.height))
        }
    }
}

data class SwitchColors(
    val checkedThumbColor: Color,
    val checkedTrackColor: Color,
    val uncheckedThumbColor: Color,
    val uncheckedTrackColor: Color,
)

object SwitchDefaults {
    @Composable
    fun colors(
        checkedThumbColor: Color = Color.White,
        checkedTrackColor: Color = DeckColors.Ruby,
        uncheckedThumbColor: Color = Color.White,
        uncheckedTrackColor: Color = DeckColors.PillSelected,
        @Suppress("UNUSED_PARAMETER") uncheckedBorderColor: Color = Color.Transparent,
        @Suppress("UNUSED_PARAMETER") checkedBorderColor: Color = Color.Transparent,
    ): SwitchColors = SwitchColors(
        checkedThumbColor = checkedThumbColor,
        checkedTrackColor = checkedTrackColor,
        uncheckedThumbColor = uncheckedThumbColor,
        uncheckedTrackColor = uncheckedTrackColor,
    )
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors? = null,
) {
    DeckSwitch(
        checked = checked,
        onCheckedChange = if (enabled) onCheckedChange else null,
        modifier = modifier,
    )
}

data class TextFieldColors(
    val focusedContainerColor: Color,
    val unfocusedContainerColor: Color,
    val focusedTextColor: Color,
    val unfocusedTextColor: Color,
    val cursorColor: Color,
    val focusedIndicatorColor: Color,
    val unfocusedIndicatorColor: Color,
    val focusedLabelColor: Color,
    val unfocusedLabelColor: Color,
    val focusedPlaceholderColor: Color,
    val unfocusedPlaceholderColor: Color,
)

object TextFieldDefaults {
    @Composable
    fun colors(
        focusedContainerColor: Color = DeckColors.Surface,
        unfocusedContainerColor: Color = DeckColors.Surface,
        focusedTextColor: Color = DeckColors.TextPrimary,
        unfocusedTextColor: Color = DeckColors.TextPrimary,
        cursorColor: Color = DeckColors.Ruby,
        focusedIndicatorColor: Color = DeckColors.Ruby,
        unfocusedIndicatorColor: Color = DeckColors.Line,
        focusedLabelColor: Color = DeckColors.Ruby,
        unfocusedLabelColor: Color = DeckColors.TextSecondary,
        focusedPlaceholderColor: Color = DeckColors.TextSecondary,
        unfocusedPlaceholderColor: Color = DeckColors.TextSecondary,
    ): TextFieldColors = TextFieldColors(
        focusedContainerColor = focusedContainerColor,
        unfocusedContainerColor = unfocusedContainerColor,
        focusedTextColor = focusedTextColor,
        unfocusedTextColor = unfocusedTextColor,
        cursorColor = cursorColor,
        focusedIndicatorColor = focusedIndicatorColor,
        unfocusedIndicatorColor = unfocusedIndicatorColor,
        focusedLabelColor = focusedLabelColor,
        unfocusedLabelColor = unfocusedLabelColor,
        focusedPlaceholderColor = focusedPlaceholderColor,
        unfocusedPlaceholderColor = unfocusedPlaceholderColor,
    )
}

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = DeckType.settingsLabel,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    shape: Shape = RoundedCornerShape(10.dp),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) colors.focusedContainerColor else colors.unfocusedContainerColor
    val fg = if (focused) colors.focusedTextColor else colors.unfocusedTextColor
    val borderColor = if (focused) colors.focusedIndicatorColor else colors.unfocusedIndicatorColor

    Column(
        modifier
            .clip(shape)
            .background(bg, shape)
            .border(if (focused) 1.2.dp else 0.8.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (label != null) {
            Box(Modifier.padding(bottom = 1.dp)) {
                label()
            }
        }
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder != null) {
                placeholder()
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = textStyle.copy(color = fg),
                cursorBrush = SolidColor(colors.cursorColor),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = DeckType.settingsLabel,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    shape: Shape = RoundedCornerShape(10.dp),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) colors.focusedContainerColor else colors.unfocusedContainerColor
    val fg = if (focused) colors.focusedTextColor else colors.unfocusedTextColor
    val borderColor = if (focused) colors.focusedIndicatorColor else colors.unfocusedIndicatorColor

    Column(
        modifier
            .clip(shape)
            .background(bg, shape)
            .border(if (focused) 1.2.dp else 0.8.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (label != null) {
            Box(Modifier.padding(bottom = 1.dp)) {
                label()
            }
        }
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.text.isEmpty() && placeholder != null) {
                placeholder()
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = textStyle.copy(color = fg),
                cursorBrush = SolidColor(colors.cursorColor),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

data class SliderColors(
    val thumbColor: Color,
    val activeTrackColor: Color,
    val inactiveTrackColor: Color,
)

object SliderDefaults {
    @Composable
    fun colors(
        thumbColor: Color = DeckColors.Ruby,
        activeTrackColor: Color = DeckColors.Ruby,
        inactiveTrackColor: Color = DeckColors.SurfaceAlt,
    ): SliderColors = SliderColors(
        thumbColor = thumbColor,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
    )
}

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    colors: SliderColors = SliderDefaults.colors(),
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val thumbSize = 16.dp
    val thumbPx = with(density) { thumbSize.toPx() }

    fun updateFromX(x: Float) {
        val usable = (widthPx - thumbPx).coerceAtLeast(1f)
        val frac = ((x - thumbPx / 2f) / usable).coerceIn(0f, 1f)
        onValueChange(valueRange.start + frac * span)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange) {
                detectTapGestures { offset -> updateFromX(offset.x) }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.inactiveTrackColor),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(colors.activeTrackColor),
                )
            }
        }
        val offsetX = ((widthPx - thumbPx).coerceAtLeast(0f) * fraction).roundToInt()
        Box(
            Modifier
                .offset { IntOffset(offsetX, 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(colors.thumbColor),
        )
    }
}

data class ButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
)

object ButtonDefaults {
    @Composable
    fun buttonColors(
        containerColor: Color = DeckColors.Ruby,
        contentColor: Color = Color.White,
        disabledContainerColor: Color = DeckColors.PillSelected,
        disabledContentColor: Color = DeckColors.TextSecondary,
    ): ButtonColors = ButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val bg = if (enabled) colors.containerColor else colors.disabledContainerColor
    Row(
        modifier
            .clip(shape)
            .background(bg, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    border: BorderStroke? = BorderStroke(1.dp, DeckColors.Line),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .clip(shape)
            .background(DeckColors.Surface, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}
