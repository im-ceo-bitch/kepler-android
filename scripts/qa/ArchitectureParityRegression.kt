package com.iumrah.beta.data.flight

import com.iumrah.beta.data.nuitee.NuiteeDailyCachePolicy
import com.iumrah.beta.data.nuitee.NuiteeHotCache
import com.iumrah.beta.core.network.APIClient
import java.time.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    var passed = 0
    fun verify(value: Boolean) { check(value); passed++ }
    val zone = ZoneId.of("Asia/Tashkent")
    val midnight = LocalDate.of(2026,11,14).atStartOfDay(zone).toInstant().toEpochMilli()
    verify(NuiteeDailyCachePolicy.fresh(midnight, midnight, zone))
    verify(NuiteeDailyCachePolicy.fresh(midnight, midnight+86_399_999L, zone))
    verify(!NuiteeDailyCachePolicy.fresh(midnight, midnight+86_400_000L, zone))
    verify(!NuiteeDailyCachePolicy.fresh(midnight-1, midnight, zone))
    verify(!NuiteeDailyCachePolicy.fresh(midnight+1, midnight, zone))
    val from=LocalDate.of(2026,11,20); val to=from.plusDays(1)
    val key=NuiteeDailyCachePolicy.key("MAKKAH",from,to,2,false)
    verify(key=="makkah-2026-11-20-2026-11-21-2-rates")
    verify(key!=NuiteeDailyCachePolicy.key("makkah",from,to,2,true))
    verify(key!=NuiteeDailyCachePolicy.key("makkah",from,to,3,false))
    verify(key!=NuiteeDailyCachePolicy.key("makkah",from,to.plusDays(1),2,false))
    verify(NuiteeDailyCachePolicy.cacheable(true,false,null))
    verify(!NuiteeDailyCachePolicy.cacheable(true,false,"provider_error"))
    verify(NuiteeDailyCachePolicy.cacheable(true,true,"provider_error"))
    verify(!NuiteeDailyCachePolicy.cacheable(false,true,null))
    val hot=NuiteeHotCache<Int>(); (1..4).forEach { hot.put("$it",it) }; verify(hot.get("1")==1); hot.put("5",5)
    verify(hot.get("2")==null); verify(hot.get("1")==1); verify(hot.get("5")==5)
    verify(FlightOperationalPolicy.normalize("HY-337")=="HY 337")
    verify(FlightOperationalPolicy.normalize("337","HY")=="HY 337")
    verify(FlightOperationalPolicy.normalize("C6321")=="C6 321")
    verify(FlightOperationalPolicy.normalize("REF-123456")==null)
    verify(FlightOperationalPolicy.normalize("337")==null)
    verify(FlightOperationalPolicy.key("HY337","2026-11-20","09:00","TAS","JED")=="HY 337|2026-11-20|09:00|TAS|JED")
    verify(FlightOperationalPolicy.key("HY337","2026-11-31","09:00","TAS","JED")==null)
    verify(FlightOperationalPolicy.key("HY337","2026-11-20","25:00","TAS","JED")==null)
    verify(FlightOperationalPolicy.fresh(0,299999)); verify(!FlightOperationalPolicy.fresh(0,300000)); verify(!FlightOperationalPolicy.fresh(1,0))
    val fake=APIClient(); val service=IumrahFlightDetailService(fake)
    suspend fun fetch(force:Boolean=false)=service.detail("HY337","2026-11-20","09:00","TAS","JED",force)
    val jobs=(1..5).map { async { fetch() } }; verify(jobs.awaitAll().all { it.detail?.aircraft=="A320" }); verify(fake.calls.get()==1)
    verify(fetch().detail!=null); verify(fake.calls.get()==1)
    verify(fetch(true).detail!=null); verify(fake.calls.get()==2)
    fake.fail=true
    suspend fun noMatch()=service.detail("HY338","2026-11-21","09:00","JED","TAS")
    verify(noMatch().reason=="no_match"); verify(noMatch().reason=="no_match"); verify(fake.calls.get()==4)
    verify(service.detail("REF-123456","2026-11-21","09:00","JED","TAS").reason=="invalid_request"); verify(fake.calls.get()==4)
    println("Architecture parity: $passed Kotlin checks passed (mock transport; no Android SDK).")
}
