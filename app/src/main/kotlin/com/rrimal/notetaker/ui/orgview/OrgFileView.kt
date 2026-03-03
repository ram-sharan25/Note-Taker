package com.rrimal.notetaker.ui.orgview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rrimal.notetaker.data.orgmode.OrgFile
import com.rrimal.notetaker.ui.orgview.utils.OrgHighlighter

@Composable
fun OrgFileView(
    orgFile: OrgFile,
    modifier: Modifier = Modifier
) {
    val foldingState = remember { mutableStateMapOf<String, Boolean>() }
    
    val highlightedPreamble = remember(orgFile.preamble) {
        if (orgFile.preamble.isNotBlank()) {
            OrgHighlighter.highlight(orgFile.preamble)
        } else null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        // Preamble
        highlightedPreamble?.let { preamble ->
            item {
                Text(
                    text = preamble,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Top-level headlines
        items(
            items = orgFile.headlines,
            key = { it.headlineId() }
        ) { headline ->
            val headlineId = headline.headlineId()
            OrgHeadlineView(
                headline = headline,
                isExpanded = foldingState[headlineId] ?: true,
                onToggleExpand = { id ->
                    foldingState[id] = !(foldingState[id] ?: true)
                },
                foldingState = foldingState
            )
        }
    }
}
