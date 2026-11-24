package de.berlin.transitwatch.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Versioned domain snapshots are Base64-encoded; provider keys remain separate and queryable. */
@Entity(tableName = "events", primaryKeys = ["providerId", "providerEventId"])
data class EventEntity(val providerId: String, val providerEventId: String, val payload: String)

@Entity(tableName = "notification_ledger")
data class NotificationLedgerEntity(@PrimaryKey val eventKey: String, val fingerprint: String)

@Entity(tableName = "pause_places")
data class PausePlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 50.0,
    val enabled: Boolean = true,
)

@Entity(tableName = "demo_state")
data class DemoStateEntity(@PrimaryKey val id: Int = 1, val payload: String)

@Dao
abstract class WatchDao {
    @Query("SELECT * FROM events ORDER BY providerId, providerEventId")
    abstract suspend fun events(): List<EventEntity>

    @Query("SELECT * FROM notification_ledger")
    abstract suspend fun ledger(): List<NotificationLedgerEntity>

    @Query("SELECT * FROM demo_state WHERE id = 1")
    abstract suspend fun demoState(): DemoStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putEvents(events: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putLedger(entries: List<NotificationLedgerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putDemoState(state: DemoStateEntity)

    @Query("DELETE FROM events")
    abstract suspend fun clearEvents()

    @Query("DELETE FROM notification_ledger")
    abstract suspend fun clearLedger()

    @Transaction
    open suspend fun replaceSnapshot(events: List<EventEntity>, ledger: List<NotificationLedgerEntity>, state: DemoStateEntity) {
        clearEvents()
        clearLedger()
        putEvents(events)
        putLedger(ledger)
        putDemoState(state)
    }

    @Query("SELECT * FROM pause_places ORDER BY name, id")
    abstract fun observePausePlaces(): Flow<List<PausePlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun savePausePlace(place: PausePlaceEntity)

    @Query("DELETE FROM pause_places WHERE id = :id")
    abstract suspend fun deletePausePlace(id: String)
}

@Database(
    entities = [EventEntity::class, NotificationLedgerEntity::class, PausePlaceEntity::class, DemoStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WatchDatabase : RoomDatabase() {
    abstract fun watchDao(): WatchDao
}
