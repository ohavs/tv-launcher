package com.ohav.tvhome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.R
import com.ohav.tvhome.data.AppEntry
import com.ohav.tvhome.data.ImageLoader
import com.ohav.tvhome.data.ShelfItem

data class ShelfState(val app: AppEntry?, val items: List<ShelfItem>)

/**
 * Content from the focused app (continue watching + its preview channels), under the apps row.
 * Card focus ids are "c:<key>" so the home screen can move focus here directly.
 */
@Composable
fun ContentRow(
    state: ShelfState,
    sidePad: Dp,
    cardHeight: Dp,
    req: (String) -> FocusRequester,
    onFocused: (Int) -> Unit,
    onOpen: (ShelfItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    captions: Boolean = true,
) {
    AnimatedContent(
        targetState = state,
        contentKey = { (it.app?.id ?: "") + "#" + it.items.size },
        transitionSpec = {
            (fadeIn(tween(420, delayMillis = 80)) + slideInVertically(tween(520)) { it / 10 }) togetherWith fadeOut(tween(200))
        },
        modifier = modifier.fillMaxWidth(),
        label = "content",
    ) { s ->
        if (s.items.isEmpty()) return@AnimatedContent Box(Modifier.fillMaxWidth())
        Column {
            val resuming = s.items.any { it.progress != null }
            val label = s.app?.label.orEmpty()
            if (showTitle) Text(
                if (resuming) stringResource(R.string.continue_in, label) else label,
                modifier = Modifier.padding(horizontal = sidePad),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = sidePad, end = sidePad, top = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // A screenful first and the rest over the next frames (while the row fades in), so a new
                // row never stalls a frame.
                var count by remember(s) { mutableIntStateOf(minOf(s.items.size, 4)) }
                LaunchedEffect(s) {
                    while (count < s.items.size) {
                        withFrameNanos { }
                        count = minOf(s.items.size, count + 2)
                    }
                }
                s.items.take(count).forEachIndexed { i, item ->
                    ContentCard(item, cardHeight, s.app?.accent ?: Color.DarkGray, captions, req("c:" + item.key), { onFocused(i) }, onOpen)
                }
            }
        }
    }
}

@Composable
private fun ContentCard(
    item: ShelfItem,
    height: Dp,
    accent: Color,
    captions: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: (ShelfItem, Rect) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val width = height * item.aspect.coerceIn(0.6f, 1.9f)
    val context = LocalContext.current
    val maxPx = with(LocalDensity.current) { (width * 1.2f).roundToPx() }
    val image by produceState(item.imageUri?.let { ImageLoader.cached(it, maxPx) }, item.imageUri) {
        item.imageUri?.let { value = ImageLoader.load(context, it, maxPx) }
    }
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(1f, Spring.StiffnessMediumLow), label = "caption")

    Column(Modifier.width(width)) {
        ParallaxSurface(
            focused = focused,
            pressed = pressed,
            shape = CardShape,
            focusScale = 1.08f,
            modifier = Modifier
                .size(width, height)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .selectKeys(onPressed = { pressed = it }, onClick = { onOpen(item, bounds) })
                .focusable(),
        ) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.1f), lerp(accent, Color.Black, 0.5f)))))
            image?.let { Image(it, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (image == null) {
                Text(
                    item.title,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                )
            }
        }
        item.progress?.let { p ->
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            ) {
                Box(Modifier.fillMaxWidth(p).height(3.dp).background(Color.White))
            }
        }
        if (captions) Text(
            item.title,
            modifier = Modifier.padding(top = 8.dp),
            color = Color.White.copy(alpha = 0.7f + 0.3f * f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (captions) item.subtitle?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.45f + 0.2f * f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
