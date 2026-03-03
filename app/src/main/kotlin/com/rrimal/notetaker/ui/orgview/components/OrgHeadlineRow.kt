package com.rrimal.notetaker.ui.orgview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrimal.notetaker.data.orgmode.OrgNode
import com.rrimal.notetaker.ui.orgview.utils.OrgHighlighter
import com.rrimal.notetaker.ui.orgview.utils.OrgTheme

@Composable
fun OrgHeadlineRow(
    headline: OrgNode.Headline,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightedTitle = remember(headline.title) {
        OrgHighlighter.highlight(headline.title)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand/collapse icon
        Icon(
            imageVector = if (isExpanded)
                Icons.Default.ExpandMore
            else
                Icons.Default.ChevronRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(20.dp),
            tint = OrgTheme.levelColor(headline.level).copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Stars
        Text(
            text = "*".repeat(headline.level),
            color = OrgTheme.levelColor(headline.level).copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            ),
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.width(8.dp))

        // TODO state chip
        headline.todoState?.let { state ->
            OrgTodoChip(state)
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Priority badge [#A]
        headline.priority?.let { priority ->
            OrgPriorityBadge(priority)
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Title
        Text(
            text = highlightedTitle,
            style = headlineStyle(headline.level),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Tags
        if (headline.tags.isNotEmpty()) {
            OrgTagsRow(headline.tags)
        }
    }
}

@Composable
private fun headlineStyle(level: Int): TextStyle {
    return when (level) {
        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    }
}
