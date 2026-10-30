package com.metrolist.tablefor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalDate

/** The Schedule day to show: [selected] while it's in [week], otherwise the first day, today. */
internal fun scheduleDayToShow(selected: LocalDate?, week: List<LocalDate>): LocalDate? =
    if (selected != null && selected in week) selected else week.firstOrNull()

@Composable
fun ScheduleScreen(viewModel: TableForViewModel) {
    val grid = viewModel.availabilityGrid
    val timeSlots = listOf("5:00 PM", "5:30 PM", "6:00 PM", "6:30 PM", "7:00 PM", "7:30 PM", "8:00 PM", "8:30 PM", "9:00 PM", "9:30 PM", "10:00 PM")
    
    val days = grid.map { it.dayLabel }.distinct()
    // The picker remembers a date, so it stays on that day when the week rolls over at midnight.
    val dates = grid.map { it.date }.distinct()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val selectedDayIndex = dates.indexOf(scheduleDayToShow(selectedDate, dates)).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Set Your Dinner Availability", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap the spreadsheet cells below to set when you are free for dinner.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (selectedDayIndex > 0) selectedDate = dates[selectedDayIndex - 1] },
                    enabled = selectedDayIndex > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                }

                Text(
                    text = if (days.isNotEmpty()) days[selectedDayIndex] else "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = { if (selectedDayIndex < dates.size - 1) selectedDate = dates[selectedDayIndex + 1] },
                    enabled = selectedDayIndex < days.size - 1
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Time Slot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Availability", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                timeSlots.forEach { timeSlot ->
                    val cell = grid.find { it.dayIndex == selectedDayIndex && it.timeSlot == timeSlot }
                    val isAvailable = cell?.isAvailable == true

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeSlot,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isAvailable) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.3f))
                                .clickable {
                                    viewModel.toggleAvailability(selectedDayIndex, timeSlot)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAvailable) "Free ✓" else "Busy",
                                color = if (isAvailable) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Matching happens automatically based on overlapping free slots, cuisine preferences, and trust ratings.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(name = "Schedule Screen", showBackground = true)
@Composable
fun ScheduleScreenPreview() {
    MaterialTheme {
        ScheduleScreen(viewModel = previewViewModel())
    }
}
