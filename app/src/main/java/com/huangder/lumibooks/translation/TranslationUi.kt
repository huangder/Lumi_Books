package com.huangder.lumibooks.translation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.settings.DetailActivity
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType

fun openTranslationSettings(context: Context) {
    context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "ai_translation"))
}

@StringRes
fun TranslationError.messageResource(): Int = when (this) {
    TranslationError.CONFIGURATION -> R.string.translation_error_configuration
    TranslationError.MISSING_KEY -> R.string.translation_error_key
    TranslationError.UNAUTHORIZED -> R.string.translation_error_auth
    TranslationError.RATE_LIMITED -> R.string.translation_error_limit
    TranslationError.MODEL_UNAVAILABLE -> R.string.translation_error_model
    TranslationError.EMPTY_RESPONSE -> R.string.translation_error_empty
    TranslationError.NETWORK -> R.string.translation_error_network
    TranslationError.TIMEOUT -> R.string.translation_error_timeout
    TranslationError.SERVICE -> R.string.translation_error_service
    TranslationError.STORAGE -> R.string.translation_error_storage
}

@Composable
fun TranslationResultCard(state: TranslationState, onRetry: () -> Unit) {
    if (state == TranslationState.Hidden) return
    val context = LocalContext.current
    val title = stringResource(R.string.translation_title)
    Column(Modifier.fillMaxWidth().testTag("translation-result").padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = AppType.Section, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            if (state == TranslationState.Loading) {
                val loading = stringResource(R.string.translation_loading)
                CircularProgressIndicator(Modifier.size(14.dp).semantics { contentDescription = loading },
                    strokeWidth = 1.5.dp, color = AppColors.TextSecondary)
            }
        }
        when (state) {
            TranslationState.Loading, TranslationState.Manual -> Unit
            is TranslationState.Success -> {
                SelectionContainer {
                    Text(state.text, fontSize = AppType.Body, color = AppColors.TextPrimary)
                }
            }
            is TranslationState.Failure -> {
                Text(stringResource(state.error.messageResource()), color = AppColors.TextSecondary, fontSize = AppType.Body)
            }
            TranslationState.Hidden -> Unit
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                TranslationState.Manual -> LiquidGlassTextButton(stringResource(R.string.translation_translate), onRetry,
                    modifier = Modifier.weight(1f))
                is TranslationState.Success -> LiquidGlassTextButton(stringResource(R.string.translation_copy), {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(title, state.text))
                }, modifier = Modifier.weight(1f))
                is TranslationState.Failure -> LiquidGlassTextButton(stringResource(R.string.translation_retry), onRetry,
                    modifier = Modifier.weight(1f))
                else -> Unit
            }
            LiquidGlassTextButton(stringResource(R.string.translation_settings), { openTranslationSettings(context) },
                modifier = Modifier.weight(1f))
        }
    }
}
