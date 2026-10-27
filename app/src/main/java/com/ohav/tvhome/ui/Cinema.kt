package com.ohav.tvhome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ohav.tvhome.R
import com.ohav.tvhome.data.AppEntry
import com.ohav.tvhome.data.ImageLoader
import com.ohav.tvhome.data.ShelfItem
import com.ohav.tvhome.data.TileStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BACKDROP_PX = 1280

private data class Backdrop(val appId: String?, val brand: Color, val logo: ImageBitmap?, val image: ImageBitmap?)

/**
 * Full-screen preview of the focused app: artwork of its featured content, or its colour and logo when it
 * publishes none. Crossfades as focus moves.
 */
@Composable
fun CinemaBackdrop(app: AppEntry?, item: ShelfItem?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uri = item?.imageUri
    var image by remember(uri) { mutableStateOf(uri?.let { ImageLoader.cached(it, BACKDROP_PX) }) }
    LaunchedEffect(uri) {
        if (uri != null && image == null) image = ImageLoader.load(context, uri, BACKDROP_PX)
    }
    val target = Backdrop(app?.id, app?.brand ?: Color.Black, app?.glyph ?: app?.icon, image)
    Crossfade(target, modifier.fillMaxSize(), animationSpec = tween(700), label = "backdrop") { b ->
        if (b.image != null) {
            Image(b.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(lerp(b.brand, Color.Black, 0.35f), lerp(b.brand, Color.Black, 0.88f))))
            ) {
                b.logo?.let {
                    Image(
                        it,
                        contentDescription = null,
                        alpha = 0.16f,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 70.dp).fillMaxWidth(0.34f).fillMaxHeight(0.36f),
                    )
                }
            }
        }
    }
}

/** Legibility over any artwork: dark top (clock), start side (titles) and bottom (carousel). */
@Composable
fun CinemaScrims(isRtl: Boolean) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.62f),
                0.36f to Color.Black.copy(alpha = 0.18f),
                0.55f to Color.Black.copy(alpha = 0.28f),
                0.78f to Color.Black.copy(alpha = 0.82f),
                1f to Color.Black.copy(alpha = 0.95f),
            )
        )
    )
    val side = listOf(Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.18f), Color.Transparent)
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(if (isRtl) side.reversed() else side)))
}

/** The focused app's name and what it would play. */
@Composable
fun CinemaHero(app: AppEntry?, item: ShelfItem?, sidePad: Dp, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = app to item,
        contentKey = { (it.first?.id ?: "") + "#" + (it.second?.key ?: "") },
        transitionSpec = { (fadeIn(tween(380, delayMillis = 60)) + slideInVertically(tween(460)) { it / 6 }) togetherWith fadeOut(tween(160)) },
        modifier = modifier.fillMaxWidth().padding(horizontal = sidePad),
        label = "hero",
    ) { (a, it) ->
        Column {
            Text(
                a?.label.orEmpty(),
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = TextStyle(shadow = LegibleShadow, letterSpacing = (-0.5).sp),
            )
            if (it != null) {
                Text(
                    if (it.progress != null) stringResource(R.string.continue_item, it.title) else it.title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = LegibleShadow),
                    modifier = Modifier.padding(top = 2.dp).fillMaxWidth(0.6f),
                )
                it.subtitle?.let { sub ->
                    Text(
                        sub,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = LegibleShadow),
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                }
            }
        }
    }
}

/**
 * Apps on a rail that keeps the focused one in the middle: the whole rail glides as focus moves, and tiles
 * shrink and fade with distance from the centre. Only tiles near the centre are composed.
 */
@Composable
fun AppCarousel(
    apps: List<AppEntry>,
    style: TileStyle,
    center: Int,
    movingId: String?,
    req: (String) -> FocusRequester,
    onFocused: (Int, AppEntry) -> Unit,
    onClick: (AppEntry, Rect) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val banner = style == TileStyle.Banner
    val tileW = if (banner) 150.dp else 104.dp
    val spacing = tileW + if (banner) 28.dp else 22.dp
    val pos = remember { Animatable(center.toFloat()) }
    LaunchedEffect(center) { pos.animateTo(center.toFloat(), spring(dampingRatio = 0.86f, stiffness = 240f)) }
    val density = LocalDensity.current
    val tileWPx = with(density) { tileW.toPx() }
    val spacingPx = with(density) { spacing.toPx() }

    BoxWithConstraints(modifier.fillMaxWidth().height(tileHeight(style, tileW) + 56.dp)) {
        val midPx = constraints.maxWidth / 2f
        apps.forEachIndexed { i, app ->
            if (abs(i - center) <= VISIBLE_EACH_SIDE) {
                key(app.id) {
                    AppTile(
                        app = app,
                        style = style,
                        width = tileW,
                        focusRequester = req(app.id),
                        moving = movingId == app.id,
                        onFocused = { onFocused(i, app) },
                        onClick = { onClick(app, it) },
                        onLongPress = { onLongPress(app) },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .offset { IntOffset((midPx + (i - pos.value) * spacingPx - tileWPx / 2).roundToInt(), 0) }
                            .graphicsLayer {
                                val d = abs(i - pos.value)
                                val s = 1f - 0.11f * d.coerceAtMost(3f)
                                scaleX = s
                                scaleY = s
                                alpha = (1.2f - 0.24f * d).coerceIn(0f, 1f)
                            }
                            .zIndex(-abs(i - center).toFloat()),
                    )
                }
            }
        }
    }
}

private const val VISIBLE_EACH_SIDE = 6
