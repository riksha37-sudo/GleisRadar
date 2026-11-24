package de.berlin.transitwatch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class PreviewCategories(
    val acute: Boolean = true,
    val planned: Boolean = true,
    val changes: Boolean = true,
    val resolution: Boolean = true,
)

enum class PreviewCategory { ACUTE, PLANNED, CHANGES, RESOLUTION }

class WatchPreferences(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val keys = PreviewCategory.entries.associateWith { booleanPreferencesKey("preview_${it.name.lowercase()}") }

    val categories = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { prefs ->
        PreviewCategories(
            acute = prefs[keys.getValue(PreviewCategory.ACUTE)] ?: true,
            planned = prefs[keys.getValue(PreviewCategory.PLANNED)] ?: true,
            changes = prefs[keys.getValue(PreviewCategory.CHANGES)] ?: true,
            resolution = prefs[keys.getValue(PreviewCategory.RESOLUTION)] ?: true,
        )
    }

    suspend fun setCategory(category: PreviewCategory, enabled: Boolean) {
        store.edit { it[keys.getValue(category)] = enabled }
    }
}
