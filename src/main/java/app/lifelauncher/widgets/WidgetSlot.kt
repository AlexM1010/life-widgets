package app.lifelauncher.widgets

/**
 * Represents a slot where widgets can be placed on the home screen.
 */
enum class WidgetSlot {
    TOP,    // Above the app list
    BOTTOM  // Below the app list
}

/**
 * Available widget types that can be placed in slots.
 */
enum class WidgetType {
    NONE,
    TODAYS_PLAN,
    // Future widgets:
    // DAILY_QUOTE,
    // WEATHER,
}
