package com.rrimal.notetaker.ui.orgview.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun OrgPropertyDrawerView(properties: Map<String, String>, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Header (clickable)
        Text(
            text = if (isExpanded) "▼ :PROPERTIES:" else "▶ :PROPERTIES:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { isExpanded = !isExpanded }
        )

        // Content (when expanded)
        if (isExpanded) {
            properties.forEach { (key, value) ->
                Text(
                    text = ":$key: $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Text(
                text = ":END:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
