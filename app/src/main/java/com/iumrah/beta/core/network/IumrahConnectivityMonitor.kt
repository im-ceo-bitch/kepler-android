package com.iumrah.beta.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.iumrah.beta.core.config.AppConfig
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

enum class IumrahConnectivityStatus(val title: String) {
    CHECKING("Checking"), ONLINE("Online"), OFFLINE("Offline"),
}

/** Presentation-only reachability; one active probe, like the iOS path monitor. */
class IumrahConnectivityMonitor(context: Context) : Closeable {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var probeJob: Job? = null
    private var generation = 0L
    private var lastNetwork: Network? = null
    private var lastProbe: Long? = null
    private var closed = false
    private val _status = MutableStateFlow(IumrahConnectivityStatus.CHECKING)
    val status: StateFlow<IumrahConnectivityStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onUnavailable() = refresh()
    }

    init {
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        refresh()
    }

    @Synchronized
    fun refresh() {
        if (closed) return
        val network = runCatching { manager?.activeNetwork }.getOrNull()
        val hasInternet = network != null && runCatching {
            manager?.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(false)
        if (!hasInternet) {
            generation++
            probeJob?.cancel()
            probeJob = null
            lastNetwork = null
            lastProbe = null
            _status.value = IumrahConnectivityStatus.OFFLINE
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (network == lastNetwork && lastProbe?.let { now - it in 0 until 20_000L } == true) return
        lastNetwork = network
        lastProbe = now
        if (_status.value == IumrahConnectivityStatus.OFFLINE) _status.value = IumrahConnectivityStatus.CHECKING
        probeJob?.cancel()
        val current = ++generation
        probeJob = scope.launch {
            val reachable = try {
                val request = Request.Builder().url(AppConfig.HEALTH_URL).get()
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "iumrah-android-beta/connectivity").build()
                client.newCall(request).awaitResult { response ->
                    response.isSuccessful && org.json.JSONObject(
                        readBounded(response.body.byteStream(), 64 * 1024).toString(Charsets.UTF_8)
                    ).optBoolean("ok", false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            publish(current, reachable)
        }
    }

    @Synchronized
    private fun publish(current: Long, reachable: Boolean) {
        if (!closed && generation == current) {
            _status.value = if (reachable) IumrahConnectivityStatus.ONLINE else IumrahConnectivityStatus.OFFLINE
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        generation++
        probeJob?.cancel()
        scope.cancel()
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }

    // Process-lifetime transport, not invalidated while callbacks may still be queued.
    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    }
}
