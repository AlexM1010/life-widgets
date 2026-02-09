package app.lifelauncher.widgets.data

import java.time.Instant

/**
 * A task from the Life Manager plan calendar.
 * Read from "Life Manager - Today's Plan" Google Calendar.
 */
data class PlannedTask(
    val eventId: String,
    val taskId: Int?,
    val title: String,
    val domain: String?,
    val priority: Priority,
    val durationMinutes: Int,
    val category: String?,
    val status: TaskStatus,
    val startTime: Instant,
    val endTime: Instant
) {
    companion object {
        /**
         * Parse task title format: [!!!] Task title (15m)
         */
        fun parseTitle(encoded: String): Triple<String, Priority, Int> {
            val priorityMatch = Regex("^\\[(!{1,3})\\]\\s*").find(encoded)
            val priority = when (priorityMatch?.groupValues?.get(1)) {
                "!!!" -> Priority.MUST_DO
                "!!" -> Priority.SHOULD_DO
                "!" -> Priority.NICE_TO_HAVE
                else -> Priority.SHOULD_DO
            }
            
            val withoutPriority = priorityMatch?.let { 
                encoded.removePrefix(it.value) 
            } ?: encoded
            
            val durationMatch = Regex("\\((\\d+)m\\)$").find(withoutPriority)
            val duration = durationMatch?.groupValues?.get(1)?.toIntOrNull() ?: 15
            
            val title = durationMatch?.let {
                withoutPriority.removeSuffix(it.value).trim()
            } ?: withoutPriority.trim()
            
            return Triple(title, priority, duration)
        }
        
        /**
         * Parse description for metadata
         */
        fun parseDescription(description: String?): DescriptionMetadata {
            if (description == null) return DescriptionMetadata()
            
            val lines = description.lines()
            
            fun extract(key: String): String? = lines
                .find { it.startsWith("$key:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
            
            return DescriptionMetadata(
                domain = extract("Domain"),
                taskId = extract("Task ID")?.toIntOrNull(),
                category = extract("Category"),
                status = extract("Status")?.let { TaskStatus.fromString(it) } ?: TaskStatus.PENDING
            )
        }
    }
}

data class DescriptionMetadata(
    val domain: String? = null,
    val taskId: Int? = null,
    val category: String? = null,
    val status: TaskStatus = TaskStatus.PENDING
)

enum class Priority(val weight: Int) {
    MUST_DO(3),
    SHOULD_DO(2),
    NICE_TO_HAVE(1)
}

enum class TaskStatus {
    PENDING,
    COMPLETED,
    SKIPPED,
    SNOOZED;
    
    companion object {
        fun fromString(s: String): TaskStatus = when (s.lowercase()) {
            "completed" -> COMPLETED
            "skipped" -> SKIPPED
            "snoozed" -> SNOOZED
            else -> PENDING
        }
    }
}
