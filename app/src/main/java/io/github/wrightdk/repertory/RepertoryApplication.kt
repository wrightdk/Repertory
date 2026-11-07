package io.github.wrightdk.repertory

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

class RepertoryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Lists whose film details never finished (added before this existed, or the
        // app was closed mid-fetch) carry on in the background.
        container.filmDetailsSync.backfill()
        // Lists whose fetch stopped because the phone was offline carry on once it's back online.
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = container.filmDetailsSync.retryOffline()
            },
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RepertoryApplication).container
