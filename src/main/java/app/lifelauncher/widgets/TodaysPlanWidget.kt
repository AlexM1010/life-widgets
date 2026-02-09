package app.lifelauncher.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lifelauncher.widgets.data.CalendarReader
import app.lifelauncher.widgets.data.PlanTask
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Displays today's plan from Life Manager calendar.
 * Shows current/next task with time remaining.
 */
@Composable
fun TodaysPlanWidget(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    val context = LocalContext.current
    val calendarReader = remember { CalendarReader(context) }
    
    var tasks by remember { mutableStateOf<List<PlanTask>>(emptyList()) }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
    // Refresh tasks periodically
    LaunchedEffect(Unit) {
        while (true) {
            tasks = calendarReader.getTodaysPlan()
            currentTime = LocalTime.now()
            delay(60_000) // Refresh every minute
        }
    }
    
    val currentTask = tasks.find { task ->
        currentTime >= task.startTime && currentTime < task.endTime
    }
    
    val nextTask = tasks.find { task ->
        task.startTime > currentTime
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment
    ) {
        if (currentTask != null) {
            TaskDisplay(
                label = "Now",
                task = currentTask,
                currentTime = currentTime,
                horizontalAlignment = horizontalAlignment
            )
        } else if (nextTask != null) {
            TaskDisplay(
                label = "Next",
                task = nextTask,
                currentTime = currentTime,
                horizontalAlignment = horizontalAlignment
            )
        } else if (tasks.isEmpty()) {
            Text(
                text = "No plan for today",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        } else {
            Text(
                text = "Plan complete ✓",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun TaskDisplay(
    label: String,
    task: PlanTask,
    currentTime: LocalTime,
    horizontalAlignment: Alignment.Horizontal
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    
    val timeInfo = if (label == "Now") {
        val minutesLeft = java.time.Duration.between(currentTime, task.endTime).toMinutes()
        "${minutesLeft}m left"
    } else {
        val minutesUntil = java.time.Duration.between(currentTime, task.startTime).toMinutes()
        if (minutesUntil < 60) "in ${minutesUntil}m" else "at ${task.startTime.format(timeFormatter)}"
    }
    
    Column(horizontalAlignment = horizontalAlignment) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "•",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
            Text(
                text = timeInfo,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        Text(
            text = task.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        task.domain?.let { domain ->
            Text(
                text = domain,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}
