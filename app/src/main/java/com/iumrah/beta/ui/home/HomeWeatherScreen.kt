package com.iumrah.beta.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.booking.JourneyTravelInfoPanel
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

/** Home weather destination, using the same live MET Norway data as Booking. */
@Composable
fun HomeWeatherScreen(language: AppLanguage, chrome: AppChromeStore) {
    val title = when (language) {
        AppLanguage.RUSSIAN -> "Погода"
        AppLanguage.ENGLISH -> "Weather"
        AppLanguage.UZBEK -> "Ob-havo"
        AppLanguage.UZBEK_CYRILLIC -> "Об-ҳаво"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IumrahPressable(
                    onClick = { chrome.back() }, modifier = Modifier.size(46.dp),
                    background = MaterialTheme.colorScheme.surface,
                    cornerRadius = 16.dp,
                ) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CupertinoIcon(CupertinoSymbol.ChevronLeft, null, Modifier.size(19.dp))
                } }
                Text(title, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
            }
        }
        item { Column(Modifier.padding(horizontal = 18.dp)) {
            // PRAYER=0, WEATHER=1, CLOCKS=2; the weather tool opens on WEATHER.
            JourneyTravelInfoPanel(language, initialPage = 1)
        } }
    }
}
