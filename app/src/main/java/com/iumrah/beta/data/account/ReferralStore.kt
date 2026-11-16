package com.iumrah.beta.data.account

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReferralState(val code: String? = null, val consent: Boolean = false)

class ReferralStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("iumrah-referral", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(ReferralState(prefs.getString("code", null)?.let(ReferralPolicy::normalize), prefs.getBoolean("consent", false)))
    val state = mutable.asStateFlow()
    fun capture(raw: String): Boolean = ReferralPolicy.capture(raw)?.let { setCode(it); true } ?: false
    fun setCode(raw: String) {
        val code = ReferralPolicy.normalize(raw) ?: return
        if (mutable.value.code != code) save(ReferralState(code, false))
    }
    fun consent(value: Boolean) { save(mutable.value.copy(consent = value && mutable.value.code != null)) }
    fun clear() { save(ReferralState()) }
    fun headers(): Map<String, String> = mutable.value.let { ReferralPolicy.headers(it.code, it.consent) }
    private fun save(value: ReferralState) {
        prefs.edit().putString("code", value.code).putBoolean("consent", value.consent).apply()
        mutable.value = value
    }
}
