package app.lifelauncher.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.lifelauncher.widgets.data.UserEvent

/**
 * Renders the appropriate widget based on type.
 */
@Composable
fun WidgetHost(
    widgetType: WidgetType,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    onOpenLifeManager: () -> Unit = {},
    onOpenEvent: (UserEvent) -> Unit = {}
) {
    when (widgetType) {
        WidgetType.NONE -> { /* Empty slot */ }
        WidgetType.TODAYS_PLAN -> TodaysPlanWidget(
            modifier = modifier,
            horizontalAlignment = horizontalAlignment,
            onOpenLifeManager = onOpenLifeManager,
            onOpenEvent = onOpenEvent
        )
    }
}
