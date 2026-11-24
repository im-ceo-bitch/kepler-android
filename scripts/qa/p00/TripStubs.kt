package com.iumrah.beta.domain.trip

enum class JourneyScope(val wireValue: String) { MAKKAH_ONLY("makkahOnly"), MAKKAH_AND_MADINAH("makkahAndMadinah") }
data class PackageMealSelection(val makkahLunch: Boolean = false, val makkahDinner: Boolean = false, val madinahDinner: Boolean = false)
enum class SaudiArrivalAirport { JEDDAH, MADINAH }
