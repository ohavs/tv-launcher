package com.ohav.tvhome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.data.AppEntry
import com.ohav.tvhome.data.ImageLoader
import com.ohav.tvhome.data.ShelfItem

data class ShelfState(val app: AppEntry?, val items: List<ShelfItem>)

/**
 * The area above the first row. Shows what the focused first-row app publishes
 * (continue watching + preview channels); empty apps leave the wallpaper in full view.
 */
@Composable
fun TopShelf(
    state: ShelfState,
    height: Dp,
    sidePad: Dp,
    downTarget: FocusRequester?,
    onOpen: (ShelfItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        contentKey = { (it.app?.id ?: "") + "#" + it.items.size },
        transitionSpec = {
            (fadeIn(tween(520, delayMillis = 60)) + slideInHorizontally(tween(620)) { it / 14 }) togetherWith fadeOut(tween(260))
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        label = "shelf",
    ) { s ->
        Box(Modifier.fillMaxSize()) {
            if (s.items.isNotEmpty()) {
                ShelfRow(
                    items = s.items,
                    accent = s.app?.accent ?: Color.DarkGray,
                    sidePad = sidePad,
                    downTarget = downTarget,
                    onOpen = onOpen,
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 22.dp),
                )
            }
        }
    }
}

@Composable
private fun ShelfRow(
    items: List<ShelfItem>,
    accent: Color,
    sidePad: Dp,
    downTarget: FocusRequester?,
    onOpen: (ShelfItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardHeight = 138.dp
    Row(
        modifier
            .fillMaxWidth()
            .focusProperties { if (downTarget != null) down = downTarget }
            .focusGroup()
            .horizontalScroll(rememberScrollState())
            .padding(start = sidePad, end = sidePad, top = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEach { item ->
            ShelfCard(item, cardHeight, accent, onOpen)
        }
    }
}

@Composable
private fun ShelfCard(item: ShelfItem, height: Dp, accent: Color, onOpen: (ShelfItem, Rect) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val width = height * item.aspect.coerceIn(0.6f, 1.9f)
    val context = LocalContext.current
    val maxPx = with(LocalDensity.current) { (width * 1.2f).roundToPx() }
    val image by produceState(item.imageUri?.let { ImageLoader.cached(it) }, item.imageUri) {
        item.imageUri?.let { value = ImageLoader.load(context, it, maxPx) }
    }
    val caption by animateFloatAsState(if (focused) 1f else 0f, spring(1f, Spring.StiffnessMediumLow), label = "caption")

    Column(Modifier.width(width)) {
        ParallaxSurface(
            focused = focused,
            pressed = pressed,
            shape = CardShape,
            focusScale = 1.09f,
            modifier = Modifier
                .size(width, height)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .onFocusChanged { focused = it.isFocused }
                .selectKeys(onPressed = { pressed = it }, onClick = { onOpen(item, bounds) })
                .focusable(),
        ) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.1f), lerp(accent, Color.Black, 0.5f)))))
            image?.let { Image(it, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (image == null) {
                Text(
                    item.title,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                )
            }
            item.progress?.let { p ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(Modifier.fillMaxWidth(p).height(4.dp).background(Color.White))
                }
            }
        }
        Text(
            item.title,
            modifier = Modifier
                .padding(top = 16.dp)
                .graphicsLayer { alpha = caption; translationY = (1f - caption) * -6.dp.toPx() },
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = LegibleShadow),
        )
    }
}
