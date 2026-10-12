package com.iumrah.beta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.util.Base64
import com.iumrah.beta.core.navigation.HotelConfiguratorDeepLink
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageMealSelection
import com.iumrah.beta.domain.trip.SaudiArrivalAirport

class MainActivity : ComponentActivity() {
    private val appContainer get() = (application as IumrahApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent?.data)
        appContainer.pushManager.receiveOpenedIntent(intent)
        setContent { IumrahApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent.data)
        appContainer.pushManager.receiveOpenedIntent(intent)
    }

    private fun handleDeepLink(uri: Uri?) {
        uri ?: return
        val chrome = appContainer.chromeStore
        // iOS WidgetExtension routes translated to Android home-screen widget deep links.
        if (uri.scheme.equals("iumrah", true) && uri.host.equals("widget", true)) {
            val widgetParts = uri.pathSegments
            when {
                widgetParts.size == 2 && widgetParts[0] == "booking" &&
                    widgetParts[1].length in 1..100 && !widgetParts[1].contains('/') ->
                    chrome.openBookingDetail(widgetParts[1])
                widgetParts.size == 1 && widgetParts[0] == "bookings" ->
                    chrome.navigate(com.iumrah.beta.core.navigation.AppTab.BOOKING)
                widgetParts.size == 1 && widgetParts[0] == "planned" -> chrome.openUmrahPlan()
                widgetParts.size == 1 && widgetParts[0] == "account" ->
                    chrome.navigate(com.iumrah.beta.core.navigation.AppTab.ACCOUNT)
            }
            return
        }
        val parts = uri.pathSegments.filter { it.isNotBlank() }

        if (uri.scheme.equals("https", true) && uri.host.equals("iumrah.app", true) &&
            parts.size == 3 && parts[0] == "flights" && parts[1] == "package" && parts[2].matches(Regex("^\\d{10}$"))) {
            chrome.openFlightPackage(parts[2])
            return
        }

        val hotelId = when {
            uri.scheme.equals("https", true) && uri.host.equals("iumrah.app", true) && parts.size == 2 && parts[0] == "hotel" ->
                Uri.decode(parts[1])
            uri.scheme.equals("https", true) && uri.host.equals("iumrah.app", true) && parts.size == 2 && parts[0] == "h" ->
                decodePublicHotelToken(Uri.decode(parts[1]))
            uri.scheme.equals("iumrahapp", true) && uri.host.equals("hotel", true) && parts.isNotEmpty() ->
                decodePublicHotelToken(Uri.decode(parts.first()))
            else -> null
        }?.trim().orEmpty()
        if (hotelId.isBlank()) return

        fun truthy(name: String): Boolean = uri.getQueryParameter(name)?.lowercase() in setOf("1", "true", "yes", "on")
        val openConfigurator = truthy("configurator")
        val hasMealSnapshot = listOf("makkah_lunch", "makkah_dinner", "madinah_dinner").any { uri.getQueryParameter(it) != null }
        val shared = if (openConfigurator) HotelConfiguratorDeepLink(
            hotelId = hotelId,
            adults = uri.getQueryParameter("adults")?.toIntOrNull(),
            children = uri.getQueryParameter("children")?.toIntOrNull(),
            infants = uri.getQueryParameter("infants")?.toIntOrNull(),
            rooms = uri.getQueryParameter("rooms")?.toIntOrNull(),
            scope = when (uri.getQueryParameter("scope")) {
                JourneyScope.MAKKAH_ONLY.wireValue -> JourneyScope.MAKKAH_ONLY
                JourneyScope.MAKKAH_AND_MADINAH.wireValue -> JourneyScope.MAKKAH_AND_MADINAH
                else -> null
            },
            firstSaudiCity = when (uri.getQueryParameter("first_city")?.uppercase()) {
                "MED" -> SaudiArrivalAirport.MADINAH
                "JED" -> SaudiArrivalAirport.JEDDAH
                else -> null
            },
            mealSelection = if (hasMealSnapshot) PackageMealSelection(
                makkahLunch = truthy("makkah_lunch"),
                makkahDinner = truthy("makkah_dinner"),
                madinahDinner = truthy("madinah_dinner"),
            ) else null,
            outboundOptionId = uri.getQueryParameter("outbound"),
            inboundOptionId = uri.getQueryParameter("inbound"),
        ) else null

        chrome.openHotel(hotelId, openConfigurator = openConfigurator, sharedConfiguration = shared)
    }

    private fun decodePublicHotelToken(token: String): String? = runCatching {
        val normalized = token.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
    }.getOrNull()

}
