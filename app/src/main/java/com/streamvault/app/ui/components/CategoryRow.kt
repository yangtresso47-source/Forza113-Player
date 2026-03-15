package com.streamvault.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamvault.app.ui.theme.LocalSpacing
import com.streamvault.app.ui.theme.Primary
import com.streamvault.app.ui.theme.TextPrimary
import com.streamvault.app.ui.theme.TextTertiary
import androidx.compose.ui.res.stringResource
import com.streamvault.app.R

// ── Netflix-style horizontal category row ─────────────────────────

@Composable
fun <T : Any> CategoryRow(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    keySelector: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Row header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            if (onSeeAll != null) {
                Surface(
                    onClick = onSeeAll,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary.copy(alpha = 0.12f),
                        focusedContainerColor = Primary.copy(alpha = 0.22f),
                        contentColor = TextTertiary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.category_see_all),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = items,
                key = keySelector ?: { it.hashCode() }
            ) { item ->
                itemContent(item)
            }
        }
    }
}
