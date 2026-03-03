package com.rrimal.notetaker.ui.orgview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rrimal.notetaker.data.orgmode.OrgNode
import com.rrimal.notetaker.ui.orgview.components.OrgBodyView
import com.rrimal.notetaker.ui.orgview.components.OrgHeadlineRow
import com.rrimal.notetaker.ui.orgview.components.OrgPlanningView
import com.rrimal.notetaker.ui.orgview.components.OrgPropertyDrawerView

@Composable
fun OrgHeadlineView(
    headline: OrgNode.Headline,
    isExpanded: Boolean,
    onToggleExpand: (String) -> Unit,
    foldingState: Map<String, Boolean>,
    modifier: Modifier = Modifier
) {
    val headlineId = headline.headlineId()

    Column(modifier = modifier) {
        // Headline row
        OrgHeadlineRow(
            headline = headline,
            isExpanded = isExpanded,
            onClick = { onToggleExpand(headlineId) }
        )

        // Content (only shown when expanded)
        if (isExpanded) {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                // Planning lines
                if (headline.hasPlanning()) {
                    OrgPlanningView(headline)
                }

                // Property drawer
                if (headline.properties.isNotEmpty()) {
                    OrgPropertyDrawerView(headline.properties)
                }

                // Body content
                if (headline.body.isNotBlank()) {
                    OrgBodyView(body = headline.body)
                }

                // Recursive children
                headline.children.forEach { child ->
                    val childId = child.headlineId()
                    OrgHeadlineView(
                        headline = child,
                        isExpanded = foldingState[childId] ?: true,
                        onToggleExpand = onToggleExpand,
                        foldingState = foldingState
                    )
                }
            }
        }
    }
}

/**
 * Extension to check if headline has planning lines
 */
private fun OrgNode.Headline.hasPlanning(): Boolean {
    return scheduled != null || deadline != null || closed != null
}

/**
 * Generate stable ID for headline
 */
fun OrgNode.Headline.headlineId(): String {
    return properties["ID"] ?: "${level}_${title.hashCode()}_${body.hashCode()}"
}
