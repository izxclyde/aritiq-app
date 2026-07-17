package com.aritiq.calcnote.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ponytail: minimal nav, no react-stack library. Uses a mutable list as a back-stack.
 * Swap for `voyager-navigator` or `androidx.navigation:navigation-compose` when
 * deep-linking/back-stack restoration actually influences UX.
 */
sealed class Route {
    data object Home : Route()
    data class Editor(val noteId: String?) : Route()
    data object Settings : Route()
    data object About : Route()
    data object ManageFolders : Route()
    data object LockedFolder : Route()
}

class Navigator(initial: Route = Route.Home) {
    private val stack = mutableListOf(initial)
    private val _current = MutableStateFlow(initial)
    val current: StateFlow<Route> = _current.asStateFlow()

    fun navigate(route: Route) {
        stack.add(route)
        _current.value = route
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        _current.value = stack.last()
        return true
    }
}