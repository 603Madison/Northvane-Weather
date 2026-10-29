package app.northvane.weather

import android.app.Application
import app.northvane.weather.notify.AlertNotifier
import kotlinx.coroutines.launch

class NorthvaneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.connectivity.start()
        AlertNotifier.createChannels(this)
        container.applicationScope.launch { container.alertScheduler.sync() }
    }
}
