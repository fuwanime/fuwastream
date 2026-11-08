package com.fuwanime.ui.components

import android.content.res.Resources
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.fuwanime.R

/** An icon-only button that names itself on long-press, so its meaning isn't a guess. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconWithTooltip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = tint) }
    }
}

/** What TalkBack reads for one poster card, e.g. "Frieren, Episode 5, Baru, 2 baru, dipilih". */
fun posterDescription(res: Resources, title: String, caption: String?, badge: String?, isNew: Boolean, airing: Boolean, selected: Boolean): String =
    listOfNotNull(
        title,
        caption?.takeIf { it.isNotBlank() },
        res.getString(R.string.common_new).takeIf { isNew },
        res.getString(R.string.common_airing_now).takeIf { airing && !isNew },
        badge?.takeIf { it.isNotBlank() },
        res.getString(R.string.common_selected).takeIf { selected },
    ).joinToString(", ")
