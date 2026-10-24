package dev.virtualvolume.app.core.data

/** The physical edge in the display's natural orientation (usually portrait). */
enum class ScreenEdge(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
    ;

    val opposite: ScreenEdge
        get() = when (this) {
            LEFT -> RIGHT
            RIGHT -> LEFT
        }

    companion object {
        val DEFAULT: ScreenEdge = RIGHT

        fun fromName(name: String?): ScreenEdge =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
