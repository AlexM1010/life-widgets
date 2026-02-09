package app.lifelauncher.widgets.data

/**
 * Skip-cycling queue for tasks.
 * 
 * Tasks are ordered hardest → easiest by Life Manager.
 * - Skip = jump to easiest (back of queue)
 * - Complete easy = return to hardest (front)
 * - Snooze = remove entirely
 * 
 * This is LOCAL state only - skipped tasks remain "pending" in calendar.
 * Only complete/snooze updates the calendar.
 */
data class TaskQueue(
    val tasks: List<PlannedTask>,
    val frontIndex: Int = 0,
    val backIndex: Int = tasks.lastIndex,
    val showingFront: Boolean = true
) {
    val currentTask: PlannedTask?
        get() = if (isEmpty) null else tasks[currentIndex]
    
    val currentIndex: Int
        get() = if (showingFront) frontIndex else backIndex
    
    val isEmpty: Boolean
        get() = frontIndex > backIndex || tasks.isEmpty()
    
    val pendingCount: Int
        get() = if (isEmpty) 0 else backIndex - frontIndex + 1
    
    val position: Int
        get() = if (showingFront) 1 else pendingCount
    
    /**
     * Skip current task - jump to opposite end of queue.
     * Does NOT update calendar (skip is local cycling only).
     */
    fun skip(): TaskQueue {
        if (isEmpty) return this
        
        return if (showingFront) {
            // Was showing hard task, jump to easy
            copy(showingFront = false)
        } else {
            // Was showing easy task, go back to hard
            copy(showingFront = true)
        }
    }
    
    /**
     * Complete current task - remove from queue, return to front.
     * Caller should update calendar with Status: completed.
     */
    fun complete(): TaskQueue {
        if (isEmpty) return this
        
        return if (showingFront) {
            // Completed hard task, advance front
            copy(frontIndex = frontIndex + 1, showingFront = true)
        } else {
            // Completed easy task, shrink back, return to front
            copy(backIndex = backIndex - 1, showingFront = true)
        }
    }
    
    /**
     * Snooze current task - remove entirely, return to front.
     * Caller should update calendar with Status: snoozed.
     */
    fun snooze(): TaskQueue {
        if (isEmpty) return this
        
        // Remove current task by adjusting indices
        return if (showingFront) {
            copy(frontIndex = frontIndex + 1, showingFront = true)
        } else {
            copy(backIndex = backIndex - 1, showingFront = true)
        }
    }
    
    companion object {
        fun fromTasks(tasks: List<PlannedTask>): TaskQueue {
            val pending = tasks.filter { it.status == TaskStatus.PENDING }
            return TaskQueue(
                tasks = pending,
                frontIndex = 0,
                backIndex = pending.lastIndex.coerceAtLeast(0),
                showingFront = true
            )
        }
    }
}
