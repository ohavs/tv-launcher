package com.ohav.tvhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.ohav.tvhome.data.Sky
import kotlin.math.cos
import kotlin.math.sin

enum class Glyph { Apps, Settings, Play, Pause, Home, Camera }

/** Small line icons drawn in code (no icon font or extra dependency). */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.09f
        when (glyph) {
            Glyph.Apps -> {
                val g = s * 0.12f
                val c = (s - g) / 2 - s * 0.08f
                val o = s * 0.08f
                for (x in 0..1) for (y in 0..1) {
                    drawRoundRect(color, Offset(o + x * (c + g), o + y * (c + g)), Size(c, c), CornerRadius(c * 0.3f))
                }
            }
            Glyph.Settings -> {
                val ctr = center
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val p1 = Offset(ctr.x + cos(a).toFloat() * s * 0.30f, ctr.y + sin(a).toFloat() * s * 0.30f)
                    val p2 = Offset(ctr.x + cos(a).toFloat() * s * 0.42f, ctr.y + sin(a).toFloat() * s * 0.42f)
                    drawLine(color, p1, p2, strokeWidth = s * 0.13f, cap = StrokeCap.Round)
                }
                drawCircle(color, s * 0.25f, ctr, style = Stroke(s * 0.12f))
            }
            Glyph.Play -> {
                val p = Path().apply {
                    moveTo(s * 0.30f, s * 0.18f); lineTo(s * 0.82f, s * 0.5f); lineTo(s * 0.30f, s * 0.82f); close()
                }
                drawPath(p, color)
                drawPath(p, color, style = Stroke(w, join = StrokeJoin.Round))
            }
            Glyph.Pause -> {
                val bw = s * 0.2f
                drawRoundRect(color, Offset(s * 0.24f, s * 0.18f), Size(bw, s * 0.64f), CornerRadius(bw * 0.3f))
                drawRoundRect(color, Offset(s * 0.56f, s * 0.18f), Size(bw, s * 0.64f), CornerRadius(bw * 0.3f))
            }
            Glyph.Home -> {
                val p = Path().apply {
                    moveTo(s * 0.12f, s * 0.48f); lineTo(s * 0.5f, s * 0.14f); lineTo(s * 0.88f, s * 0.48f)
                    moveTo(s * 0.22f, s * 0.40f); lineTo(s * 0.22f, s * 0.86f); lineTo(s * 0.78f, s * 0.86f); lineTo(s * 0.78f, s * 0.40f)
                    moveTo(s * 0.42f, s * 0.86f); lineTo(s * 0.42f, s * 0.62f); lineTo(s * 0.58f, s * 0.62f); lineTo(s * 0.58f, s * 0.86f)
                }
                drawPath(p, color, style = Stroke(w * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Glyph.Camera -> {
                val stroke = Stroke(w * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRect(color, Offset(s * 0.08f, s * 0.28f), Size(s * 0.58f, s * 0.44f), CornerRadius(s * 0.1f), style = stroke)
                val lens = Path().apply {
                    moveTo(s * 0.66f, s * 0.44f); lineTo(s * 0.92f, s * 0.3f); lineTo(s * 0.92f, s * 0.7f); lineTo(s * 0.66f, s * 0.56f)
                }
                drawPath(lens, color, style = stroke)
            }
        }
    }
}

private val Sun = Color(0xFFFFD166)
private val Moon = Color(0xFFE6E9F5)
private val CloudWhite = Color(0xFFF1F4F8)
private val CloudGrey = Color(0xFFC9D1DC)
private val RainBlue = Color(0xFF7CC4FF)

@Composable
fun WeatherGlyph(sky: Sky, day: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (sky) {
            Sky.Clear -> if (day) sun(Offset(s / 2, s / 2), s * 0.22f, s) else moon(Offset(s / 2, s / 2), s * 0.3f)
            Sky.Partly -> {
                if (day) sun(Offset(s * 0.36f, s * 0.36f), s * 0.16f, s * 0.72f) else moon(Offset(s * 0.38f, s * 0.36f), s * 0.22f)
                cloud(Rect(s * 0.22f, s * 0.42f, s * 0.96f, s * 0.86f), CloudWhite)
            }
            Sky.Cloudy -> {
                cloud(Rect(s * 0.30f, s * 0.20f, s * 0.98f, s * 0.60f), CloudGrey)
                cloud(Rect(s * 0.04f, s * 0.38f, s * 0.82f, s * 0.84f), CloudWhite)
            }
            Sky.Fog -> for (i in 0..2) {
                val y = s * (0.34f + i * 0.17f)
                drawLine(CloudWhite, Offset(s * (0.14f + i * 0.06f), y), Offset(s * (0.86f - (2 - i) * 0.06f), y), s * 0.08f, StrokeCap.Round)
            }
            Sky.Drizzle, Sky.Rain, Sky.Snow, Sky.Storm -> {
                cloud(Rect(s * 0.06f, s * 0.10f, s * 0.94f, s * 0.58f), if (sky == Sky.Storm) CloudGrey else CloudWhite)
                when (sky) {
                    Sky.Rain -> for (i in 0..2) {
                        val x = s * (0.32f + i * 0.18f)
                        drawLine(RainBlue, Offset(x, s * 0.66f), Offset(x - s * 0.07f, s * 0.90f), s * 0.07f, StrokeCap.Round)
                    }
                    Sky.Drizzle -> for (i in 0..2) drawCircle(RainBlue, s * 0.045f, Offset(s * (0.32f + i * 0.18f), s * (0.72f + (i % 2) * 0.1f)))
                    Sky.Snow -> for (i in 0..2) drawCircle(Color.White, s * 0.055f, Offset(s * (0.30f + i * 0.2f), s * (0.72f + (i % 2) * 0.12f)))
                    else -> {
                        val bolt = Path().apply {
                            moveTo(s * 0.55f, s * 0.52f); lineTo(s * 0.38f, s * 0.76f); lineTo(s * 0.52f, s * 0.76f)
                            lineTo(s * 0.44f, s * 0.96f); lineTo(s * 0.66f, s * 0.68f); lineTo(s * 0.52f, s * 0.68f); close()
                        }
                        drawPath(bolt, Sun)
                    }
                }
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, s: Float) {
    for (i in 0 until 8) {
        rotate(i * 45f, c) {
            drawLine(Sun, Offset(c.x, c.y - r * 1.45f), Offset(c.x, c.y - r * 1.85f), s * 0.06f, StrokeCap.Round)
        }
    }
    drawCircle(Sun, r, c)
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val disc = Path().apply { addOval(Rect(c, r)) }
    val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, disc, bite), Moon)
}

private fun DrawScope.cloud(b: Rect, color: Color) {
    val h = b.height
    val base = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(b.left, b.top + h * 0.45f, b.right, b.bottom), CornerRadius(h * 0.28f))) }
    val left = Path().apply { addOval(Rect(Offset(b.left + b.width * 0.30f, b.top + h * 0.52f), h * 0.30f)) }
    val right = Path().apply { addOval(Rect(Offset(b.left + b.width * 0.60f, b.top + h * 0.42f), h * 0.40f)) }
    drawPath(Path.combine(PathOperation.Union, Path.combine(PathOperation.Union, base, left), right), color)
}
