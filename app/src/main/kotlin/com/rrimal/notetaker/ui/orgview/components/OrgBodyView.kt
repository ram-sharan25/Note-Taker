package com.rrimal.notetaker.ui.orgview.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rrimal.notetaker.ui.orgview.utils.OrgHighlighter

@Composable
fun OrgBodyView(
    body: String,
    modifier: Modifier = Modifier
) {
    if (body.isNotBlank()) {
        val highlightedText = remember(body) {
            OrgHighlighter.highlight(body)
        }
        
        Text(
            text = highlightedText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f
        )
    }
}
