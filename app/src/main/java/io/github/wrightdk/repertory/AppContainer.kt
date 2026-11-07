package io.github.wrightdk.repertory

import android.content.Context
import io.github.wrightdk.repertory.data.local.RepertoryDatabase

/**
 * Manual dependency wiring. One instance lives in [RepertoryApplication]; add
 * the TMDB client and repositories here as they arrive.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: RepertoryDatabase by lazy { RepertoryDatabase.build(appContext) }
}
