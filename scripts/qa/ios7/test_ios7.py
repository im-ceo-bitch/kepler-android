#!/usr/bin/env python3
"""Compile real guide DTO and rewards-cache methods, with fake account transport."""
from pathlib import Path
import subprocess, tempfile, shutil
root=Path(__file__).resolve().parents[3]
src=root/'app/src/main/java/com/iumrah/beta'
chat=(src/'data/chat/ChatService.kt').read_text()
profile=chat[chat.index('@Serializable\ndata class IumrahGuideReview'):chat.index('@Serializable private data class ChatMessageSendRequest')]
profile=profile.replace('com.iumrah.beta.core.settings.AppLanguage','AppLanguage').replace('@Serializable\n','')
account=(src/'data/account/IumrahAccountStore.kt').read_text()
fields=account[account.index('    data class DashboardSnapshot'):account.index('    private val initialLoad')]
methods=account[account.index('    suspend fun refreshDashboardRewards'):account.index('    private suspend fun <T> scopedSecurity')]
code='''import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import java.util.Locale

enum class AppLanguage(val code:String){RUSSIAN("ru"),ENGLISH("en"),UZBEK("uz"),UZBEK_CYRILLIC("uz-cyrl")}
data class IumrahFriendsDashboard(val ok:Boolean,val availableCreditUsd:Double)
sealed class APIException:Exception(){class Status(val code:Int):APIException();class Server(val code:Int):APIException()}
data class AccountState(val isAuthenticated:Boolean)
class Service {var calls=0;var fail:Exception?=null;var credit=0.0;var latency=40L
    suspend fun friendsDashboard(token:String):IumrahFriendsDashboard {calls++;delay(latency);fail?.let{throw it};return IumrahFriendsDashboard(true,credit)}
    suspend fun registerCurrentSession(token:String,locale:String){}
}
class TestStore(val service:Service){
    val state=MutableStateFlow(AccountState(false))
'''+fields+'''
    fun setOwner(value:String?){token=value;state.value=AccountState(value!=null)}
    private fun requireToken()=token?:throw APIException.Status(401)
'''+methods+'''
}
'''+profile+'''
fun main()=runBlocking{
    val p=IumrahPublicProfile(id="guide",bio="original",bioRU="рус",bioUZ="  lotin  ",bioUZCyrl="кирилл")
    check(p.localizedBio(AppLanguage.RUSSIAN)=="рус");check(p.localizedBio(AppLanguage.UZBEK)=="lotin");check(p.localizedBio(AppLanguage.UZBEK_CYRILLIC)=="кирилл");check(p.localizedBio(AppLanguage.ENGLISH)=="рус");check(p.copy(bioUZ=" ").localizedBio(AppLanguage.UZBEK)=="original");check(p.copy(bio="",bioRU=null,bioUZ=null,bioUZCyrl=null).localizedBio(AppLanguage.UZBEK)=="")
    val service=Service();val store=TestStore(service)
    store.refreshDashboardRewards();check(service.calls==0)
    store.setOwner("a");val both=awaitAll(async{store.friendsDashboard()},async{store.friendsDashboard()});check(service.calls==1);check(both.all{it.availableCreditUsd==0.0});check(store.dashboardRewards.value.data!=null)
    store.refreshDashboardRewards();check(service.calls==1)
    service.fail=IllegalStateException("network");store.refreshDashboardRewards(true);check(store.dashboardRewards.value.stale);check(store.dashboardRewards.value.data?.availableCreditUsd==0.0)
    service.fail=APIException.Status(401);store.refreshDashboardRewards(true);check(store.dashboardRewards.value.data==null);check(!store.dashboardRewards.value.stale)
    service.fail=null;service.credit=42.0;store.refreshDashboardRewards(true);check(store.dashboardRewards.value.data?.availableCreditUsd==42.0)
    service.latency=100;val old=async{runCatching{store.friendsDashboard()}};delay(20);store.setOwner("b");check(store.dashboardRewards.value.data==null);check(old.await().isFailure);check(store.dashboardRewards.value.data==null)
    service.credit=17.0;store.refreshDashboardRewards(true);check(store.dashboardRewards.value.data?.availableCreditUsd==17.0);store.setOwner(null);check(store.dashboardRewards.value.data==null)
    println("iOS 7 actual source-fragment JVM smoke PASS: 6 localized bios and 14 reward cache/privacy assertions")
}
'''
compiler=Path(shutil.which('kotlinc')).resolve();cp=compiler.parents[1]/'lib/kotlinx-coroutines-core-jvm.jar'
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'Smoke.kt').write_text(code);subprocess.run(['kotlinc','-nowarn','-cp',str(cp),str(p/'Smoke.kt'),'-d',str(p/'smoke.jar')],check=True);subprocess.run(['kotlin','-cp',f'{cp}:{p}/smoke.jar','SmokeKt'],check=True)
