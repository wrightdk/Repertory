package io.github.wrightdk.repertory

import android.app.Application
import android.content.Context

class RepertoryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Lists whose film details never finished (added before this existed, or the
        // app was closed mid-fetch) carry on in the background.
        container.filmDetailsSync.backfill()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RepertoryApplication).container
