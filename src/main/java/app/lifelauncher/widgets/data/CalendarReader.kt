package app.lifelauncher.widgets.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads today's plan from the "Life Manager - Today's Plan" calendar.
 * Also reads user events from primary calendar for 45-min lookahead.
 * Updates task status (completed/skipped/snoozed) back to calendar.
 */
class CalendarReader(private val context: Context) {
    
    companion object {
        const val PLAN_CALENDAR_NAME = "Life Manager - Today's Plan"
    }
    
    /**
     * Fetch today's planned tasks from the plan calendar.
     * Returns tasks ordered by start time (hardest first as set by Life Manager).
     */
    suspend fun getTodaysPlan(): List<PlannedTask> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val calendarId = findCalendarId(contentResolver, PLAN_CALENDAR_NAME) ?: return@withContext emptyList()
        
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val tasks = mutableListOf<PlannedTask>()
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION
        )
        
        try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?",
                arrayOf(calendarId.toString(), startOfDay.toString(), endOfDay.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursorToPlannedTask(cursor)?.let { tasks.add(it) }
                }
            }
        } catch (e: SecurityException) {
            // Calendar permission not granted
        }
        
        tasks
    }
    
    /**
     * Fetch user events from primary calendar for the next N minutes.
     * Excludes events from the plan calendar.
     */
    suspend fun getUpcomingUserEvents(lookaheadMinutes: Int = 45): List<UserEvent> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val planCalendarId = findCalendarId(contentResolver, PLAN_CALENDAR_NAME)
        
        val now = Instant.now().toEpochMilli()
        val lookahead = now + (lookaheadMinutes * 60 * 1000L)
        
        val events = mutableListOf<UserEvent>()
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY
        )
        
        try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(now.toString(), lookahead.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventCalendarId = cursor.getLong(5)
                    // Exclude plan calendar events
                    if (planCalendarId != null && eventCalendarId == planCalendarId) continue
                    
                    cursorToUserEvent(cursor)?.let { events.add(it) }
                }
            }
        } catch (e: SecurityException) {
            // Calendar permission not granted
        }
        
        events
    }
    
    /**
     * Update task status in calendar (completed/skipped/snoozed).
     * Returns true if successful.
     */
    suspend fun updateTaskStatus(
        eventId: String,
        status: TaskStatus,
        snoozedTo: LocalDate? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        
        try {
            // Read current description
            val projection = arrayOf(CalendarContract.Events.DESCRIPTION)
            val currentDesc = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(eventId),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: ""
            
            // Update description with new status
            val timestamp = Instant.now().toString()
            val updatedDesc = updateDescriptionStatus(currentDesc, status, timestamp, snoozedTo)
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.DESCRIPTION, updatedDesc)
            }
            
            val updated = contentResolver.update(
                CalendarContract.Events.CONTENT_URI,
                values,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(eventId)
            )
            
            updated > 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun findCalendarId(contentResolver: ContentResolver, name: String): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        
        try {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) {
                        return cursor.getLong(0)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Calendar permission not granted
        }
        
        return null
    }
    
    private fun cursorToPlannedTask(cursor: Cursor): PlannedTask? {
        val eventId = cursor.getString(0) ?: return null
        val encodedTitle = cursor.getString(1) ?: return null
        val startMillis = cursor.getLong(2)
        val endMillis = cursor.getLong(3)
        val description = cursor.getString(4)
        
        val (title, priority, duration) = PlannedTask.parseTitle(encodedTitle)
        val metadata = PlannedTask.parseDescription(description)
        
        return PlannedTask(
            eventId = eventId,
            taskId = metadata.taskId,
            title = title,
            domain = metadata.domain,
            priority = priority,
            durationMinutes = duration,
            category = metadata.category,
            status = metadata.status,
            startTime = Instant.ofEpochMilli(startMillis),
            endTime = Instant.ofEpochMilli(endMillis)
        )
    }
    
    private fun cursorToUserEvent(cursor: Cursor): UserEvent? {
        val id = cursor.getString(0) ?: return null
        val title = cursor.getString(1) ?: return null
        val startMillis = cursor.getLong(2)
        val endMillis = cursor.getLong(3)
        val location = cursor.getString(4)
        val isAllDay = cursor.getInt(6) == 1
        
        // Skip all-day events for lookahead
        if (isAllDay) return null
        
        return UserEvent(
            id = id,
            title = title,
            startTime = Instant.ofEpochMilli(startMillis),
            endTime = Instant.ofEpochMilli(endMillis),
            location = location
        )
    }
    
    private fun updateDescriptionStatus(
        desc: String,
        status: TaskStatus,
        timestamp: String,
        snoozedTo: LocalDate?
    ): String {
        // Remove existing status lines
        val lines = desc.lines().filter { line ->
            !line.startsWith("Status:", ignoreCase = true) &&
            !line.startsWith("CompletedAt:", ignoreCase = true) &&
            !line.startsWith("SkippedAt:", ignoreCase = true) &&
            !line.startsWith("SnoozedAt:", ignoreCase = true) &&
            !line.startsWith("SnoozedTo:", ignoreCase = true)
        }
        
        // Add new status
        val newLines = mutableListOf<String>()
        newLines.addAll(lines)
        newLines.add("Status: ${status.name.lowercase()}")
        
        when (status) {
            TaskStatus.COMPLETED -> newLines.add("CompletedAt: $timestamp")
            TaskStatus.SKIPPED -> newLines.add("SkippedAt: $timestamp")
            TaskStatus.SNOOZED -> {
                newLines.add("SnoozedAt: $timestamp")
                snoozedTo?.let { newLines.add("SnoozedTo: $it") }
            }
            TaskStatus.PENDING -> {} // No timestamp for pending
        }
        
        return newLines.joinToString("\n")
    }
}
