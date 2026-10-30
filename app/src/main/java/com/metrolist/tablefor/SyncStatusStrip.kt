package com.metrolist.tablefor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One-line strip under the top bar when the app isn't showing live data. Hidden when synced. */
@Composable
fun SyncStatusStrip(status: SyncStatus) {
    val message = when (status) {
        SyncStatus.SampleData -> "Sample data — not connected"
        SyncStatus.SyncError -> "Can't sync — showing saved data"
        SyncStatus.Connecting, SyncStatus.Synced -> return
    }
    val isError = status == SyncStatus.SyncError
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}
