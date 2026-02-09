package app.lifelauncher.widgets.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reads today's plan from the local calendar.
 * Looks for a calendar named "Life Manager - Today's Plan" or falls back to events
 * with "[LM]" prefix in any calendar.
 */
class CalendarReader(private val context: Context) {
    
    companion object {
        private const val PLAN_CALENDAR_NAME = "Life Manager - Today's Plan"
        private const val LM_PREFIX = "[LM]"
    }
    
    suspend fun getTodaysPlan(): List<PlanTask> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        
        // Find the plan calendar ID
        val calendarId = findPlanCalendarId(contentResolver)
        
        // Get today's events
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val tasks = mutableListOf<PlanTask>()
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.CALENDAR_ID
        )
        
        val selection = if (calendarId != null) {
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        } else {
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        }
        
        val selectionArgs = if (calendarId != null) {
            arrayOf(calendarId.toString(), startOfDay.toString(), endOfDay.toString())
        } else {
            arrayOf(startOfDay.toString(), endOfDay.toString())
        }
        
        try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val task = cursorToTask(cursor, calendarId == null)
                    if (task != null) tasks.add(task)
                }
            }
        } catch (e: SecurityException) {
            // Calendar permission not granted
        }
        
        tasks
    }
    
    private fun findPlanCalendarId(contentResolver: ContentResolver): Long? {
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
                    val name = cursor.getString(1)
                    if (name == PLAN_CALENDAR_NAME) {
                        return cursor.getLong(0)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Calendar permission not granted
        }
        
        return null
    }
    
    private fun cursorToTask(cursor: Cursor, requirePrefix: Boolean): PlanTask? {
        val title = cursor.getString(1) ?: return null
        
        // If no dedicated calendar, only include events with [LM] prefix
        if (requirePrefix && !title.startsWith(LM_PREFIX)) {
            return null
        }
        
        val displayTitle = if (title.startsWith(LM_PREFIX)) {
            title.removePrefix(LM_PREFIX).trim()
        } else {
            title
        }
        
        val startMillis = cursor.getLong(2)
        val endMillis = cursor.getLong(3)
        val description = cursor.getString(4)
        
        val zone = ZoneId.systemDefault()
        val startTime = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime()
        val endTime = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime()
        
        // Parse domain from description (format: "domain: health" or similar)
        val domain = description?.lines()
            ?.find { it.startsWith("domain:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
        
        return PlanTask(
            id = cursor.getString(0),
            title = displayTitle,
            startTime = startTime,
            endTime = endTime,
            domain = domain
        )
    }
}
