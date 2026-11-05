package com.eazaz.smartnotes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eazaz.smartnotes.R
import com.eazaz.smartnotes.data.Note
import com.eazaz.smartnotes.ui.theme.NoteColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(note: Note, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = NoteColors.of(note.color),
        border = if (note.color == 0) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (note.title.isNotBlank()) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (note.isChecklist) {
                val ordered = note.items.filter { !it.checked } + note.items.filter { it.checked }
                val shown = ordered.filter { it.text.isNotBlank() }
                shown.take(8).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                        Icon(
                            if (item.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (item.checked) muted else Color.Unspecified,
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        )
                    }
                }
                if (shown.size > 8) {
                    Text(
                        stringResource(R.string.more_items, shown.size - 8),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            } else if (note.content.isNotBlank()) {
                Text(
                    note.content.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.labels.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    note.labels.forEach { LabelChip(it) }
                }
            }
        }
    }
}

@Composable
fun LabelChip(text: String, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    val content: @Composable () -> Unit = {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), content = content)
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), content = content)
    }
}
