# life-widgets

Shared widget library for the Open-Closed life management system. Provides Jetpack Compose widgets that display data from Life Manager's calendar.

## Architecture

Part of the hub-and-spoke architecture:
- **life-manager** (web) - Intelligence hub, planning algorithm
- **life-launcher** (Android) - Simple display layer
- **life-widgets** (this) - Shared widget components

## Widgets

- **TodaysPlanWidget** - Shows current/next task from "Life Manager - Today's Plan" calendar

## Usage

Include via Gradle composite build in your Android project:

```kotlin
// settings.gradle.kts
includeBuild("../life-widgets") {
    dependencySubstitution {
        substitute(module("app.lifelauncher:widgets")).using(project(":"))
    }
}

// app/build.gradle.kts
dependencies {
    implementation("app.lifelauncher:widgets")
}
```

## License

GNU GPLv3
