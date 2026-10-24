package com.ohav.tvhome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohav.tvhome.R
import com.ohav.tvhome.data.AppEntry

@Composable
fun AppMenu(
    app: AppEntry,
    canHide: Boolean,
    onMove: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        val pop = remember { Animatable(0.9f) }
        LaunchedEffect(Unit) {
            first.requestFocus()
            pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(440.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.9f) * 10f).coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xF21C1C1F))
                    .padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(Modifier.size(96.dp, 54.dp).clip(TileShape)) { TileArt(app) }
                    Text(app.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp))
                }
                MenuRow(stringResource(R.string.menu_move), Modifier.focusRequester(first), onClick = onMove)
                if (canHide) MenuRow(stringResource(R.string.menu_hide), onClick = onHide)
                MenuRow(stringResource(R.string.menu_info), onClick = onInfo)
                MenuRow(stringResource(R.string.menu_cancel), onClick = onDismiss)
            }
        }
    }
}
