package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.icons.AppIcons

@Composable
internal fun ReaderRememberChoice(
    checked: Boolean,
    ink: Color,
    paper: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .heightIn(min = 48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(22.dp)
                .background(if (checked) ink else Color.Transparent, CircleShape)
                .border(1.5.dp, ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(AppIcons.Check, contentDescription = null, tint = paper,
                    modifier = Modifier.size(16.dp))
            }
        }
        Text(stringResource(R.string.tts_start_remember_choice), color = ink,
            modifier = Modifier.padding(start = 12.dp))
    }
}
