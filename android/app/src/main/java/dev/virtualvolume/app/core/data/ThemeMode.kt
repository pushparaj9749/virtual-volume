package dev.virtualvolume.app.core.data

/** App colour scheme selection. The app is dark-first, light is opt-in. */
enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    DARK("Dark"),
    LIGHT("Light"),
    ;

    companion object {
        val DEFAULT: ThemeMode = DARK

        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
