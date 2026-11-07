package io.github.wrightdk.repertory

import android.app.Application
import android.content.Context

class RepertoryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RepertoryApplication).container
