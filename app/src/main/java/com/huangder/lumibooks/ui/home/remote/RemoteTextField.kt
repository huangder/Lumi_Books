package com.huangder.lumibooks.ui.home.remote

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppType

private val ErrorColor = Color(0xFFD92D3A)

/**
 * Labelled input in the Lumi settings style (as on the translation settings page): label above a
 * tinted rounded field with an accent focus rim, instead of a Material outlined field.
 */
@Composable
internal fun RemoteTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val shape = AppRoundedCornerShape(AppRadius.lg)
    val rimColor = when {
        isError -> ErrorColor.copy(alpha = 0.75f)
        focused -> AppColors.Accent.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (isError && supportingText != null) error(supportingText)
                }
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .lumiCardSurface(color = AppColors.BgGray, shape = shape, controlEdge = true)
                .border(1.dp, rimColor, shape),
            textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary, fontSize = AppType.Body),
            cursorBrush = SolidColor(AppColors.Accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                onDone = { onImeAction?.invoke() ?: focusManager.clearFocus() }
            ),
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .heightIn(min = 54.dp)
                        .padding(start = 16.dp, end = if (trailingContent == null) 16.dp else 4.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                color = AppColors.TextSecondary.copy(alpha = 0.7f),
                                fontSize = AppType.Body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    trailingContent?.invoke()
                }
            }
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = if (isError) ErrorColor else AppColors.TextSecondary,
                fontSize = AppType.Caption
            )
        }
    }
}
