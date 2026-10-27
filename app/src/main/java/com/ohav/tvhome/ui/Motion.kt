package com.ohav.tvhome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class Dir(val dx: Int, val dy: Int, val focus: FocusDirection) {
    Left(-1, 0, FocusDirection.Left),
    Right(1, 0, FocusDirection.Right),
    Up(0, -1, FocusDirection.Up),
    Down(0, 1, FocusDirection.Down),
}

/** Shared D-pad motion: last direction (for entry tilt) and edge "bumps". */
class FocusMotion {
    var lastDir: Dir? = null
    var bumpDir: Dir? by mutableStateOf(null)
        private set
    var bumpCount by mutableIntStateOf(0)
        private set

    fun bump(dir: Dir) {
        bumpDir = dir
        bumpCount++
    }
}

val LocalFocusMotion = staticCompositionLocalOf { FocusMotion() }

private val tiltSpring = spring<Float>(dampingRatio = 0.38f, stiffness = 160f)

/**
 * tvOS-style focus surface: springy lift, entry tilt in the direction of travel,
 * a specular sheen that follows the tilt,
 * a soft shadow, an edge bump when focus can't move, and an optional edit-mode jiggle.
 */
@Composable
fun ParallaxSurface(
    focused: Boolean,
    pressed: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    focusScale: Float = 1.16f,
    jiggle: Boolean = false,
    /** Off for see-through surfaces: the shadow would show through the glass. */
    shadow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = LocalFocusMotion.current
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> focusScale - 0.07f
            focused -> focusScale
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "scale",
    )
    val lift by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        label = "lift",
    )
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val nudgeX = remember { Animatable(0f) }
    val nudgeY = remember { Animatable(0f) }

    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        val d = motion.lastDir ?: return@LaunchedEffect
        launch { tiltY.snapTo(d.dx * 10f); tiltY.animateTo(0f, tiltSpring) }
        launch { tiltX.snapTo(-d.dy * 10f); tiltX.animateTo(0f, tiltSpring) }
    }
    LaunchedEffect(motion.bumpCount) {
        val d = motion.bumpDir
        if (!focused || d == null) return@LaunchedEffect
        launch {
            nudgeX.animateTo(d.dx * 9f, tween(90, easing = FastOutSlowInEasing))
            nudgeX.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 380f))
        }
        launch {
            nudgeY.animateTo(d.dy * 9f, tween(90, easing = FastOutSlowInEasing))
            nudgeY.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 380f))
        }
        launch { tiltY.animateTo(d.dx * 7f, tween(90)); tiltY.animateTo(0f, tiltSpring) }
        launch { tiltX.animateTo(-d.dy * 7f, tween(90)); tiltX.animateTo(0f, tiltSpring) }
    }

    // No idle sway: a still home screen costs no frames (performance budget).
    var wiggle: State<Float>? = null
    if (jiggle) {
        val w = rememberInfiniteTransition(label = "jiggle")
        wiggle = w.animateFloat(-1.8f, 1.8f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "w")
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = tiltX.value
                rotationY = tiltY.value
                rotationZ = wiggle?.value ?: 0f
                translationX = nudgeX.value.dp.toPx()
                translationY = nudgeY.value.dp.toPx()
                cameraDistance = 16f * density
                shadowElevation = if (shadow) lift * 26.dp.toPx() else 0f
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
                this.shape = shape
                clip = true
            }
            .drawWithContent {
                drawContent()
                if (lift > 0.01f) {
                    // Sheen slides across as the surface tilts.
                    val t = (tiltY.value / 10f).coerceIn(-1f, 1f)
                    val cx = size.width * (0.5f - t * 0.6f)
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.22f * lift),
                            1f to Color.Transparent,
                            start = Offset(cx - size.width * 0.55f, 0f),
                            end = Offset(cx + size.width * 0.55f, size.height),
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.10f * lift),
                            0.35f to Color.Transparent,
                        )
                    )
                }
            },
        content = content,
    )
}
