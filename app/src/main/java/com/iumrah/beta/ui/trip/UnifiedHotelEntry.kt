package com.iumrah.beta.ui.trip

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.PackageFlightPath
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.ui.hotels.nuiteeText
import kotlinx.coroutines.CancellationException

/** Compatibility entry point for older configurators, matching PrimaryHotelView.swift. */
@Composable fun UnifiedHotelEntry(language:AppLanguage,journey:JourneyStore,curated:CuratedFlightRecommendationService,chrome:AppChromeStore) {
    val state by journey.state.collectAsState();var retry by remember{mutableIntStateOf(0)};var error by remember{mutableStateOf<String?>(null)}
    if(state.nuitee?.flights?.size==2) { NuiteeJourneyHotelsScreen(language,journey,chrome);return }
    LaunchedEffect(retry) {
        error=null
        val selected=journey.state.value
        if((selected.packageFlightPath==PackageFlightPath.PUBLISHED_DIRECT && !selected.hasCompletePublishedFlightSelection) ||
            (selected.packageFlightPath==PackageFlightPath.AVIASALES_SELECTED && !selected.hasCompleteStagedFlightSelection) ||
            selected.packageFlightPath !in setOf(PackageFlightPath.PUBLISHED_DIRECT,PackageFlightPath.AVIASALES_SELECTED)) {
            chrome.openGuidedFlights();return@LaunchedEffect
        }
        try{journey.prepareUnifiedHotelSelection(curated)}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message ?: "FLIGHT_SELECTION_REQUIRED"}
    }
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
        if(error==null)CircularProgressIndicator() else {Text(error.orEmpty());Button(onClick={retry++}){Text(nuiteeText(language,"Повторить","Retry","Qayta urinish","Қайта уриниш"))};TextButton(onClick=chrome::openGuidedFlights){Text(nuiteeText(language,"Выбрать рейсы","Choose flights","Reyslarni tanlash","Рейсларни танлаш"))}}
    }}
}
