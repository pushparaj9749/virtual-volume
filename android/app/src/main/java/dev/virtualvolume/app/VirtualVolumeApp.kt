package dev.virtualvolume.app

import android.app.Application
import android.content.Context
import dev.virtualvolume.app.di.AppContainer

class VirtualVolumeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Accessor used by components that are not `ViewModel`s (service, tile, receiver). */
val Context.appContainer: AppContainer
    get() = (applicationContext as VirtualVolumeApp).container
