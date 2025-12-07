package ai.rever.bossterm.compose.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlin.math.max
import kotlin.math.min

// UI Constants - aligned with TabBar.kt theme
private val SurfaceColor = Color(0xFF2B2B2B)
private val BackgroundColor = Color(0xFF1E1E1E)
private val AccentColor = Color(0xFF4A90E2)
private val BorderColor = Color(0xFF404040)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFB0B0B0)
private val TextMuted = Color(0xFF707070)

/**
 * HSV color picker dialog.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    // Convert initial color to HSV
    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var alpha by remember { mutableStateOf(initialColor.alpha) }

    // Current color from HSV
    val currentColor = remember(hue, saturation, value, alpha) {
        hsvToColor(hue, saturation, value, alpha)
    }

    // Hex input
    var hexInput by remember(currentColor) {
        mutableStateOf(colorToHex(currentColor))
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "Select Color",
        resizable = false,
        state = rememberDialogState(size = DpSize(340.dp, 480.dp))
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Saturation/Value canvas
                Text(
                    text = "Saturation / Brightness",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                SaturationValuePicker(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSaturationValueChange = { s, v ->
                        saturation = s
                        value = v
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                // Hue slider
                Text(
                    text = "Hue",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                // Alpha slider
                Text(
                    text = "Alpha",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                AlphaSlider(
                    alpha = alpha,
                    color = hsvToColor(hue, saturation, value, 1f),
                    onAlphaChange = { alpha = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                // Preview and hex input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color preview
                    Column {
                        Text(
                            text = "Preview",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // New color
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(currentColor)
                                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                            )
                            // Original color
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(initialColor)
                                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    // Hex input
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hex (ARGB)",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        BasicTextField(
                            value = hexInput,
                            onValueChange = { newHex ->
                                hexInput = newHex
                                parseHexColor(newHex)?.let { parsed ->
                                    val hsv = colorToHsv(parsed)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                    alpha = parsed.alpha
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(AccentColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceColor, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = SurfaceColor
                        ),
                        modifier = Modifier.width(80.dp)
                    ) {
                        Text("Cancel", color = TextPrimary, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onColorSelected(currentColor) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AccentColor
                        ),
                        modifier = Modifier.width(80.dp)
                    ) {
                        Text("OK", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Saturation/Value 2D picker canvas.
 */
@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = hsvToColor(hue, 1f, 1f, 1f)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val s = (offset.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        onSaturationValueChange(s, v)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        onSaturationValueChange(s, v)
                    }
                }
        ) {
            // White to hue gradient (horizontal - saturation)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, baseColor)
                )
            )
            // Transparent to black gradient (vertical - value)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )
            // Border
            drawRect(
                color = BorderColor,
                style = Stroke(width = 1.dp.toPx())
            )

            // Selection indicator
            val indicatorX = saturation * size.width
            val indicatorY = (1f - value) * size.height
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(indicatorX, indicatorY),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color.Black,
                radius = 6.dp.toPx(),
                center = Offset(indicatorX, indicatorY),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Horizontal hue slider (0-360).
 */
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onHueChange((offset.x / size.width * 360f).coerceIn(0f, 360f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onHueChange((change.position.x / size.width * 360f).coerceIn(0f, 360f))
                    }
                }
        ) {
            // Hue gradient
            drawRect(
                brush = Brush.horizontalGradient(colors = hueColors)
            )
            // Border
            drawRect(
                color = BorderColor,
                style = Stroke(width = 1.dp.toPx())
            )

            // Selection indicator
            val indicatorX = (hue / 360f) * size.width
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(indicatorX - 4.dp.toPx(), 0f),
                size = Size(8.dp.toPx(), size.height),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * Alpha slider (0-1).
 */
@Composable
private fun AlphaSlider(
    alpha: Float,
    color: Color,
    onAlphaChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onAlphaChange((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onAlphaChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            // Checkerboard pattern for transparency
            val checkerSize = 8.dp.toPx()
            for (row in 0 until (size.height / checkerSize).toInt() + 1) {
                for (col in 0 until (size.width / checkerSize).toInt() + 1) {
                    val isLight = (row + col) % 2 == 0
                    drawRect(
                        color = if (isLight) Color(0xFFCCCCCC) else Color(0xFF999999),
                        topLeft = Offset(col * checkerSize, row * checkerSize),
                        size = Size(checkerSize, checkerSize)
                    )
                }
            }

            // Alpha gradient
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(color.copy(alpha = 0f), color.copy(alpha = 1f))
                )
            )
            // Border
            drawRect(
                color = BorderColor,
                style = Stroke(width = 1.dp.toPx())
            )

            // Selection indicator
            val indicatorX = alpha * size.width
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(indicatorX - 4.dp.toPx(), 0f),
                size = Size(8.dp.toPx(), size.height),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

// Color conversion utilities

/**
 * Convert Color to HSV array [hue, saturation, value].
 */
private fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue

    val maxC = max(max(r, g), b)
    val minC = min(min(r, g), b)
    val delta = maxC - minC

    val h = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6)
        maxC == g -> 60f * (((b - r) / delta) + 2)
        else -> 60f * (((r - g) / delta) + 4)
    }.let { if (it < 0) it + 360 else it }

    val s = if (maxC == 0f) 0f else delta / maxC
    val v = maxC

    return floatArrayOf(h, s, v)
}

/**
 * Convert HSV to Color.
 */
private fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
    val c = value * saturation
    val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
    val m = value - c

    val (r, g, b) = when {
        hue < 60 -> Triple(c, x, 0f)
        hue < 120 -> Triple(x, c, 0f)
        hue < 180 -> Triple(0f, c, x)
        hue < 240 -> Triple(0f, x, c)
        hue < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Convert Color to hex string (ARGB format).
 */
private fun colorToHex(color: Color): String {
    val a = (color.alpha * 255).toInt()
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return "#%02X%02X%02X%02X".format(a, r, g, b)
}

/**
 * Parse hex string to Color. Supports:
 * - #AARRGGBB (8 chars)
 * - #RRGGBB (6 chars)
 * - 0xAARRGGBB
 * - 0xRRGGBB
 */
private fun parseHexColor(hex: String): Color? {
    val cleanHex = hex.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
        .uppercase()

    return try {
        when (cleanHex.length) {
            6 -> {
                val rgb = cleanHex.toLong(16)
                Color(
                    red = ((rgb shr 16) and 0xFF) / 255f,
                    green = ((rgb shr 8) and 0xFF) / 255f,
                    blue = (rgb and 0xFF) / 255f,
                    alpha = 1f
                )
            }
            8 -> {
                val argb = cleanHex.toLong(16)
                Color(
                    alpha = ((argb shr 24) and 0xFF) / 255f,
                    red = ((argb shr 16) and 0xFF) / 255f,
                    green = ((argb shr 8) and 0xFF) / 255f,
                    blue = (argb and 0xFF) / 255f
                )
            }
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}
