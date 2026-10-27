package com.ohav.tvhome.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.ui.MenuRow

/**
 * A [MenuRow] that turns into a text field on OK, so moving through the list doesn't pop the keyboard.
 * Done, Back or moving away saves.
 */
@Composable
fun TextRow(
    title: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    onDone: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var refocusRow by remember { mutableStateOf(false) }
    val row = remember { FocusRequester() }
    if (!editing) {
        val shown = when {
            value.isEmpty() -> placeholder
            password -> "•".repeat(value.length.coerceAtMost(10))
            else -> value
        }
        MenuRow(title, Modifier.focusRequester(row), value = shown) { editing = true }
        LaunchedEffect(Unit) {
            if (refocusRow) runCatching { row.requestFocus() }
            refocusRow = false
        }
        return
    }

    var text by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var hadFocus by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun finish(backToRow: Boolean) {
        if (!editing) return
        onDone(text.text)
        refocusRow = backToRow
        editing = false
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color(0xFF111111), fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(170.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFF111111), fontSize = 19.sp, textDirection = TextDirection.Ltr),
            cursorBrush = SolidColor(Color(0xFF3B6FE0)),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { finish(true) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(field)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true
                    else if (hadFocus) finish(false)
                }
                .onPreviewKeyEvent { ev ->
                    if (ev.key != Key.Back) return@onPreviewKeyEvent false
                    if (ev.type == KeyEventType.KeyUp) finish(true)
                    true
                },
        )
    }
    LaunchedEffect(Unit) {
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
}
