package com.aritiq.calcnote.ui.home

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    RECENTLY_EDITED("Recently edited"),
    ALPHABETICAL("Alphabetical");

    companion object {
        fun fromString(value: String): SortOrder = entries.firstOrNull { it.name == value } ?: NEWEST_FIRST
    }
}

sealed interface GroupedItem {
    data class Header(val label: String) : GroupedItem
    data class NoteItem(val note: com.aritiq.calcnote.domain.Note) : GroupedItem
}

data class MonthYear(val month: kotlinx.datetime.Month, val year: Int) : Comparable<MonthYear> {
    override fun compareTo(other: MonthYear): Int {
        val y = year.compareTo(other.year)
        if (y != 0) return y
        return month.compareTo(other.month)
    }
    val label: String get() = "${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year"
}

fun groupByMonth(notes: List<com.aritiq.calcnote.domain.Note>, descending: Boolean): List<GroupedItem> {
    if (notes.isEmpty()) return emptyList()
    val groups = notes.groupBy { note ->
        val dt = note.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
        MonthYear(dt.month, dt.year)
    }
    val sortedKeys = if (descending) groups.keys.sortedDescending() else groups.keys.sorted()
    return sortedKeys.flatMap { key ->
        listOf(GroupedItem.Header(key.label)) + groups[key]!!.map { GroupedItem.NoteItem(it) }
    }
}
