package com.fuwanime.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** [cacheServedAt]: when the API data on screen was originally fetched, while it is being served from cache. */
data class NetState(val online: Boolean = true, val cacheServedAt: Long? = null)

/** Single source for the offline indicators: connectivity plus whether the API cache is standing in. */
class NetworkStatus {
    private val _state = MutableStateFlow(NetState())
    val state: StateFlow<NetState> = _state.asStateFlow()

    private val hits = AtomicLong()

    /** Rises every time the cache stands in for the API; compare before/after a load to know it was served stale. */
    val cacheHits: Long get() = hits.get()

    fun setOnline(online: Boolean) = _state.update { it.copy(online = online) }

    fun markCacheServed(atMillis: Long) {
        hits.incrementAndGet()
        _state.update { it.copy(cacheServedAt = atMillis) }
    }

    fun markNetworkOk() {
        if (_state.value.cacheServedAt != null) _state.update { it.copy(cacheServedAt = null) }
    }

    fun start(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        setOnline(cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        cm.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = setOnline(true)
                override fun onLost(network: Network) = setOnline(false)
            },
        )
    }
}
