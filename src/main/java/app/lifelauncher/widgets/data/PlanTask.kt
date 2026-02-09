package app.lifelauncher.widgets.data

import java.time.LocalTime

/**
 * A task from the Life Manager plan calendar.
 */
data class PlanTask(
    val id: String,
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val domain: String? = null,  // e.g., "health", "work", "relationships"
    val isCompleted: Boolean = false
)
