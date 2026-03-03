package com.rrimal.notetaker.ui.orgview.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rrimal.notetaker.ui.orgview.utils.OrgTheme

@Composable
fun OrgTodoChip(state: String, modifier: Modifier = Modifier) {
    val (backgroundColor, textColor) = OrgTheme.todoColors(state)

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
        modifier = modifier.padding(2.dp)
    ) {
        Text(
            text = state,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold
        )
    }
}
