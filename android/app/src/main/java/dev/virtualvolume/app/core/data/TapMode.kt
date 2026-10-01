package dev.virtualvolume.app.core.data

/** How a single tap on the control is interpreted. */
enum class TapMode(val label: String, val description: String) {
    SPLIT(
        label = "Split",
        description = "Tap the upper half to raise, the lower half to lower",
    ),
    ALWAYS_INCREASE(
        label = "Always raise",
        description = "Every tap raises the volume by one step",
    ),
    ALWAYS_DECREASE(
        label = "Always lower",
        description = "Every tap lowers the volume by one step",
    ),
    ;

    companion object {
        val DEFAULT: TapMode = SPLIT

        fun fromName(name: String?): TapMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
