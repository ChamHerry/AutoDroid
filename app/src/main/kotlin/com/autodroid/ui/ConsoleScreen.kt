package com.autodroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.autodroid.log.LogEntry
import com.autodroid.log.ConsoleRepository
import com.autodroid.log.LogEvent

@Composable
fun ConsoleScreen(consoleRepo: ConsoleRepository = ConsoleRepository) {
    var logs by remember { mutableStateOf(consoleRepo.logs) }
    val listState = rememberLazyListState()

    // Subscribe to log events for reactive updates
    LaunchedEffect(Unit) {
        consoleRepo.events.collect { event ->
            when (event) {
                is LogEvent.NewEntry -> logs = consoleRepo.logs
                is LogEvent.Cleared -> logs = emptyList()
            }
        }
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Toolbar with clear button
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { consoleRepo.clear() }) {
                Text("清空")
            }
        }

        // Log list
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(logs) { entry ->
                Text(
                    text = "[${entry.level}] ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = when (entry.level) {
                        "error" -> Color.Red
                        "warn" -> Color(0xFFFFA000)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        }
    }
}
