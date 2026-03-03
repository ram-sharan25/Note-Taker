package com.rrimal.notetaker.ui.orgview.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rrimal.notetaker.data.orgmode.OrgNode
import com.rrimal.notetaker.ui.orgview.utils.OrgTheme

@Composable
fun OrgPlanningView(headline: OrgNode.Headline, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        headline.closed?.let { closed ->
            PlanningLine(
                label = "CLOSED:",
                timestamp = closed,
                color = OrgTheme.PlanningColors.closed
            )
        }
        headline.scheduled?.let { scheduled ->
            PlanningLine(
                label = "SCHEDULED:",
                timestamp = scheduled,
                color = OrgTheme.PlanningColors.scheduled
            )
        }
        headline.deadline?.let { deadline ->
            PlanningLine(
                label = "DEADLINE:",
                timestamp = deadline,
                color = OrgTheme.PlanningColors.deadline
            )
        }
    }
}

@Composable
private fun PlanningLine(label: String, timestamp: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
