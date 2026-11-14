package com.iumrah.beta.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.iumrah.beta.core.config.AppConfig
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

enum class IumrahConnectivityStatus(val title: String) {
    CHECKING("Checking"),
    ONLINE("Online"),
    OFFLINE("Offline"),
}

/**
 * Android mirror of iOS IumrahConnectivityMonitor.
 * ConnectivityManager reacts to radio/Wi-Fi changes immediately; a unified backend health probe
 * prevents a connected-but-unusable network from being presented as online.
 */
class IumrahConnectivityMonitor(context: Context) : Closeable {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probeGeneration = AtomicLong(0)
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val _status = MutableStateFlow(IumrahConnectivityStatus.CHECKING)
    val status: StateFlow<IumrahConnectivityStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = evaluate(network)
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = evaluate(network, capabilities)
        override fun onLost(network: Network) = evaluate(manager.activeNetwork)
        override fun onUnavailable() { setOffline() }
    }

    init {
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onFailure { refresh() }
        refresh()
    }

    fun refresh() {
        evaluate(manager.activeNetwork)
    }

    private fun evaluate(network: Network?, provided: NetworkCapabilities? = null) {
        if (network == null) {
            setOffline()
            return
        }
        val capabilities = provided ?: manager.getNetworkCapabilities(network)
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!hasInternet) {
            setOffline()
            return
        }
        if (_status.value == IumrahConnectivityStatus.OFFLINE) _status.value = IumrahConnectivityStatus.CHECKING
        probe()
    }

    private fun probe() {
        val generation = probeGeneration.incrementAndGet()
        scope.launch {
            val request = Request.Builder()
                .url(AppConfig.HEALTH_URL)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "iumrah-android-beta/connectivity")
                .build()
            val reachable = runCatching {
                client.newCall(request).execute().use { response ->
                    response.isSuccessful && runCatching {
                        org.json.JSONObject(response.body.string()).optBoolean("ok", false)
                    }.getOrDefault(false)
                }
            }.getOrDefault(false)
            if (probeGeneration.get() == generation) {
                _status.value = if (reachable) IumrahConnectivityStatus.ONLINE else IumrahConnectivityStatus.OFFLINE
            }
        }
    }

    private fun setOffline() {
        probeGeneration.incrementAndGet()
        _status.value = IumrahConnectivityStatus.OFFLINE
    }

    override fun close() {
        runCatching { manager.unregisterNetworkCallback(callback) }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
