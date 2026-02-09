package app.lifelauncher.widgets

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lifelauncher.widgets.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Today's Plan Widget - displays tasks from Life Manager calendar.
 * 
 * Gestures:
 * - Swipe RIGHT = Complete task
 * - Swipe LEFT = Skip (cycle to easiest)
 * - Swipe DOWN = Snooze to tomorrow
 * 
 * Display logic:
 * - User event < 15 min away: event takes over
 * - User event 15-45 min away: task + event preview
 * - Otherwise: next pending task
 * - All done: "Open Life Manager" prompt
 */
@Composable
fun TodaysPlanWidget(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    onOpenLifeManager: () -> Unit = {},
    onOpenEvent: (UserEvent) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarReader = remember { CalendarReader(context) }
    
    var widgetState by remember { mutableStateOf<WidgetState>(WidgetState.Loading) }
    var taskQueue by remember { mutableStateOf<TaskQueue?>(null) }
    var dismissedEventId by remember { mutableStateOf<String?>(null) }
    
    // Gesture thresholds
    val swipeThreshold = 100f
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    
    // Refresh function
    suspend fun refresh() {
        try {
            val tasks = calendarReader.getTodaysPlan()
            val userEvents = calendarReader.getUpcomingUserEvents(45)
                .filter { it.id != dismissedEventId }
            
            val pendingTasks = tasks.filter { it.status == TaskStatus.PENDING }
            val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }
            
            // Check for imminent user event
            val now = Instant.now()
            val imminentEvent = userEvents.firstOrNull()
            val minutesUntilEvent = imminentEvent?.let {
                Duration.between(now, it.startTime).toMinutes().toInt().coerceAtLeast(0)
            }
            
            widgetState = when {
                // User event < 15 min away or happening now - takes over
                imminentEvent != null && minutesUntilEvent != null && minutesUntilEvent < 15 -> {
                    WidgetState.UpcomingEvent(
                        event = imminentEvent,
                        minutesUntil = minutesUntilEvent,
                        taskUnderneath = null
                    )
                }
                
                // User event 15-45 min away - show task with event preview
                imminentEvent != null && minutesUntilEvent != null && minutesUntilEvent < 45 -> {
                    if (pendingTasks.isEmpty()) {
                        WidgetState.UpcomingEvent(imminentEvent, minutesUntilEvent, null)
                    } else {
                        val queue = taskQueue ?: TaskQueue.fromTasks(pendingTasks)
                        taskQueue = queue
                        queue.currentTask?.let { task ->
                            WidgetState.UpcomingEvent(imminentEvent, minutesUntilEvent, task)
                        } ?: WidgetState.UpcomingEvent(imminentEvent, minutesUntilEvent, null)
                    }
                }
                
                // No plan exists
                tasks.isEmpty() -> WidgetState.NoPlan
                
                // All tasks done
                pendingTasks.isEmpty() -> WidgetState.NeedMoreTasks(completedCount)
                
                // Show next task
                else -> {
                    val queue = taskQueue ?: TaskQueue.fromTasks(pendingTasks)
                    taskQueue = queue
                    queue.currentTask?.let { task ->
                        WidgetState.NextTask(task, queue.position, queue.pendingCount)
                    } ?: WidgetState.NeedMoreTasks(completedCount)
                }
            }
        } catch (e: SecurityException) {
            widgetState = WidgetState.Error("Calendar permission required")
        } catch (e: Exception) {
            widgetState = WidgetState.Error("Failed to load plan")
        }
    }
    
    // Initial load and periodic refresh
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(60_000) // Refresh every minute
        }
    }
    
    // Gesture handlers
    fun onSwipeRight() {
        scope.launch {
            val queue = taskQueue ?: return@launch
            val task = queue.currentTask ?: return@launch
            
            // Update calendar with completed status
            calendarReader.updateTaskStatus(task.eventId, TaskStatus.COMPLETED)
            
            // Advance queue
            taskQueue = queue.complete()
            refresh()
        }
    }
    
    fun onSwipeLeft() {
        // Skip is local only - just cycle the queue
        val queue = taskQueue ?: return
        taskQueue = queue.skip()
        
        scope.launch { refresh() }
    }
    
    fun onSwipeDown() {
        scope.launch {
            val queue = taskQueue ?: return@launch
            val task = queue.currentTask ?: return@launch
            
            // Update calendar with snoozed status
            val tomorrow = LocalDate.now().plusDays(1)
            calendarReader.updateTaskStatus(task.eventId, TaskStatus.SNOOZED, tomorrow)
            
            // Remove from queue
            taskQueue = queue.snooze()
            refresh()
        }
    }
    
    fun onDismissEvent(event: UserEvent) {
        dismissedEventId = event.id
        scope.launch { refresh() }
    }
    
    // Render
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffsetX > swipeThreshold -> onSwipeRight()
                            dragOffsetX < -swipeThreshold -> onSwipeLeft()
                        }
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetX += dragAmount
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > swipeThreshold) onSwipeDown()
                        dragOffsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        dragOffsetY += dragAmount
                    }
                )
            }
    ) {
        when (val state = widgetState) {
            is WidgetState.Loading -> LoadingDisplay(horizontalAlignment)
            is WidgetState.Error -> ErrorDisplay(state.message, horizontalAlignment)
            is WidgetState.NoPlan -> NoPlanDisplay(horizontalAlignment, onOpenLifeManager)
            is WidgetState.NeedMoreTasks -> NeedMoreTasksDisplay(state.completedToday, horizontalAlignment, onOpenLifeManager)
            is WidgetState.NextTask -> TaskDisplay(state.task, state.position, state.totalPending, horizontalAlignment)
            is WidgetState.UpcomingEvent -> EventDisplay(state, horizontalAlignment, { onDismissEvent(state.event) }, { onOpenEvent(state.event) })
            is WidgetState.SignInRequired -> SignInDisplay(horizontalAlignment)
            is WidgetState.Offline -> OfflineDisplay(state.cachedState, horizontalAlignment)
        }
    }
}

@Composable
private fun TaskDisplay(
    task: PlannedTask,
    position: Int,
    total: Int,
    horizontalAlignment: Alignment.Horizontal
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$position of $total",
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
                text = "${task.durationMinutes}m",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        Text(
            text = task.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
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

@Composable
private fun EventDisplay(
    state: WidgetState.UpcomingEvent,
    horizontalAlignment: Alignment.Horizontal,
    onDismiss: () -> Unit,
    onTap: () -> Unit
) {
    Column(horizontalAlignment = horizontalAlignment) {
        // Event info
        val timeText = if (state.minutesUntil == 0) "now" else "in ${state.minutesUntil}m"
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📅",
                fontSize = 14.sp
            )
            Text(
                text = timeText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (state.minutesUntil < 5) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        
        Text(
            text = state.event.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Show task underneath if 15-45 min away
        state.taskUnderneath?.let { task ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Then: ${task.title}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NeedMoreTasksDisplay(
    completedToday: Int,
    horizontalAlignment: Alignment.Horizontal,
    onOpenLifeManager: () -> Unit
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = if (completedToday > 0) "✓ $completedToday done today" else "All caught up",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Text(
            text = "Tap for more tasks",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun NoPlanDisplay(
    horizontalAlignment: Alignment.Horizontal,
    onOpenLifeManager: () -> Unit
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = "No plan yet",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Text(
            text = "Open Life Manager to plan your day",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun LoadingDisplay(horizontalAlignment: Alignment.Horizontal) {
    Text(
        text = "Loading...",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    )
}

@Composable
private fun ErrorDisplay(message: String, horizontalAlignment: Alignment.Horizontal) {
    Text(
        text = message,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    )
}

@Composable
private fun SignInDisplay(horizontalAlignment: Alignment.Horizontal) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = "Sign in required",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Text(
            text = "Tap to connect Google Calendar",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun OfflineDisplay(cachedState: WidgetState, horizontalAlignment: Alignment.Horizontal) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = "Offline",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        // Render cached state
    }
}
