package com.iumrah.beta.core.navigation

/** Route state identity is per owning tab and route instance. Keys intentionally
 * contain no booking IDs, share tokens, or JourneyStore data. In-memory only;
 * process-death restoration of active business flows is a separate concern. */
internal class RouteStateKeys {
    private val instance = java.util.UUID.randomUUID().toString()
    private var serial = 0L
    private val keys = linkedMapOf<Pair<AppTab, AppRoute>, String>()
    companion object {
        /** Do not add SavedState retention to identity, payment or token-based routes. */
        fun canRetain(route: AppRoute): Boolean = when (route) {
            AppRoute.Root, AppRoute.AccountSecurity, AppRoute.AccountPasswordRecovery,
            AppRoute.AccountPasswordSecurity, AppRoute.AccountProfileEditor, AppRoute.AccountUserData,
            AppRoute.BookingCheckout, is AppRoute.SmartCheckout, is AppRoute.PilgrimCheckout,
            is AppRoute.AccountKyc, is AppRoute.BusinessOffer, is AppRoute.PublicTrip -> false
            else -> true
        }
    }
    fun key(tab: AppTab, route: AppRoute): String = keys.getOrPut(tab to route) { "destination-$instance-${++serial}" }
    fun prune(state: AppChromeState): List<String> {
        val active = state.tabNavigation.toMutableMap().apply {
            put(state.currentTab, TabNavigationState(state.route, state.backStack))
        }
        val retained = active.flatMap { (tab, stack) -> (stack.backStack + stack.route).map { tab to it } }.toSet()
        val expired = keys.filterKeys { it !in retained }.keys.toList()
        return expired.mapNotNull { keys.remove(it) }
    }
}
