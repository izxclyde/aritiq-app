package com.aritiq.calcnote.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ponytail: minimal nav, no react-stack library. State is one current [Route] + a push-count
 * used to derive back-enabled behaviour. Phase 1 has three destinations + optional editor
 * `noteId`. Swap for `voyager-navigator` or `androidx.navigation:navigation-compose` when
 * deep-linking/back-stack restoration actually influences UX.
 */
sealed class Route {
    data object Home : Route()
    data class Editor(val noteId: String?) : Route()    // null = new note
    data object Settings : Route()
    data object About : Route()
}

class Navigator(initial: Route = Route.Home) {
    private val _current = MutableStateFlow(initial)
    val current: StateFlow<Route> = _current.asStateFlow()

    private var depth = 1

    fun navigate(route: Route) {
        _current.value = route
        depth++
    }

    fun pop(): Boolean {
        if (depth <= 1) return false
        depth--
        // ponytail: pop returns to Home (no deep back-stack yet). Real back history is a
        // Phase 2 improvement once we replace this trivial Navigator with one that keeps
        // a stack of routes and supports BackHandler integration.
        _current.value = Route.Home
        return true
    }
}