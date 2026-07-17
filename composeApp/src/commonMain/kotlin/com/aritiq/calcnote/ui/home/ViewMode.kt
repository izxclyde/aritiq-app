package com.aritiq.calcnote.ui.home

enum class ViewMode(val label: String) {
    SIMPLE_LIST("Simple list"),
    DETAILED_LIST("Detailed list"),
    SMALL_GRID("Small grid"),
    LARGE_GRID("Large grid");

    companion object {
        fun fromString(value: String): ViewMode = entries.firstOrNull { it.name == value } ?: DETAILED_LIST
    }
}
