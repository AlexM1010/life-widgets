package app.lifelauncher.widgets

import app.lifelauncher.widgets.data.PlannedTask
import app.lifelauncher.widgets.data.UserEvent

/**
 * Widget states for the Life Manager task widget.
 * Simplified per architecture: no energy slider (set in web app only).
 */
sealed class WidgetState {
    
    /** Next task from the plan */
    data class NextTask(
        val task: PlannedTask,
        val position: Int,      // 1-indexed position in queue
        val totalPending: Int   // total pending tasks
    ) : WidgetState()
    
    /** User event within 45 minutes or happening now */
    data class UpcomingEvent(
        val event: UserEvent,
        val minutesUntil: Int,  // 0 if happening now
        val taskUnderneath: PlannedTask? = null  // shown as preview when 15-45 min away
    ) : WidgetState()
    
    /** All tasks done - direct user to web app */
    data class NeedMoreTasks(
        val completedToday: Int
    ) : WidgetState()
    
    /** No plan exists yet - direct user to web app */
    object NoPlan : WidgetState()
    
    /** Sign in required */
    object SignInRequired : WidgetState()
    
    /** Loading */
    object Loading : WidgetState()
    
    /** Offline with cached data */
    data class Offline(
        val cachedState: WidgetState
    ) : WidgetState()
    
    /** Error */
    data class Error(
        val message: String
    ) : WidgetState()
}

/**
 * Widget slot positions on home screen.
 */
enum class WidgetSlot {
    TOP,
    BOTTOM
}

/**
 * Available widget types.
 */
enum class WidgetType {
    NONE,
    TODAYS_PLAN
}
