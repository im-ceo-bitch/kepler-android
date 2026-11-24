package com.iumrah.beta.ui.umrahflow

import androidx.compose.runtime.Composable
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.advisor.UmrahAdvisorScreen

/** Both Advisor entry points share one ritual session UI and checkpoint contract. */
@Composable
fun UmrahFlowScreen(language: AppLanguage, chrome: AppChromeStore) {
    UmrahAdvisorScreen(appLanguage = language, chrome = chrome)
}
