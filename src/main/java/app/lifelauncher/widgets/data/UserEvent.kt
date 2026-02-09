package app.lifelauncher.widgets.data

import java.time.Instant

/**
 * A user's calendar event (meeting, appointment).
 * Read from primary Google Calendar (NOT the plan calendar).
 */
data class UserEvent(
    val id: String,
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val location: String? = null,
    val meetingLink: String? = null,
    val isAllDay: Boolean = false
) {
    val durationMinutes: Int
        get() = ((endTime.epochSecond - startTime.epochSecond) / 60).toInt()
}
