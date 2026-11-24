package de.berlin.transitwatch

import android.app.Application
import androidx.room.Room
import de.berlin.transitwatch.data.DemoRepository
import de.berlin.transitwatch.data.WatchDatabase
import de.berlin.transitwatch.data.WatchPreferences

class TransitWatchApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, WatchDatabase::class.java, "transit-watch.db").build()
    }
    val demoRepository by lazy { DemoRepository(database.watchDao()) }
    val preferences by lazy { WatchPreferences(this) }
}
