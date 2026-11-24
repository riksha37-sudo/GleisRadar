package de.berlin.transitwatch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.berlin.transitwatch.TransitWatchApplication
import de.berlin.transitwatch.core.FixtureScenario
import de.berlin.transitwatch.data.DeparturePreview
import de.berlin.transitwatch.data.DeparturePreviewClient
import de.berlin.transitwatch.data.PreviewCategories
import de.berlin.transitwatch.data.PreviewCategory
import de.berlin.transitwatch.data.StationCatalog
import de.berlin.transitwatch.data.readStationCatalog
import de.berlin.transitwatch.data.retainUnconfirmedFrom
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OverviewState(
    val catalog: StationCatalog? = null,
    val catalogLoading: Boolean = true,
    val now: Instant = Instant.now(),
    val preview: DeparturePreview? = null,
    val previewLoading: Boolean = false,
    val previewFailed: Boolean = false,
)

class WatchViewModel(private val app: TransitWatchApplication) : ViewModel() {
    val state = app.demoRepository.state
    val categories = app.preferences.categories.stateIn(viewModelScope, SharingStarted.Eagerly, PreviewCategories())
    private val mutableSettingsError = MutableStateFlow<String?>(null)
    val settingsError: StateFlow<String?> = mutableSettingsError
    private val mutableOverview = MutableStateFlow(OverviewState())
    val overview: StateFlow<OverviewState> = mutableOverview

    init {
        viewModelScope.launch { app.demoRepository.load() }
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) {
                runCatching {
                    app.assets.open("station-index.json").bufferedReader().use { readStationCatalog(it.readText()) }
                }.getOrNull()
            }
            mutableOverview.value = mutableOverview.value.copy(catalog = catalog, catalogLoading = false)
        }
    }

    fun updateTime() {
        mutableOverview.value = mutableOverview.value.copy(now = Instant.now())
    }

    fun refreshDepartures() {
        if (mutableOverview.value.previewLoading) return
        mutableOverview.value = mutableOverview.value.copy(previewLoading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeparturePreviewClient().read() } }
            val current = mutableOverview.value
            mutableOverview.value = current.copy(
                preview = result.getOrNull()?.retainUnconfirmedFrom(current.preview) ?: current.preview,
                previewLoading = false,
                previewFailed = result.isFailure,
                now = Instant.now(),
            )
        }
    }

    fun run(scenario: FixtureScenario) {
        viewModelScope.launch { app.demoRepository.run(scenario, app.preferences.categories.first()) }
    }
    fun reset() { viewModelScope.launch { app.demoRepository.reset() } }
    fun setCategory(category: PreviewCategory, enabled: Boolean) {
        viewModelScope.launch {
            try {
                app.preferences.setCategory(category, enabled)
                mutableSettingsError.value = null
            } catch (error: java.io.IOException) {
                mutableSettingsError.value = "Die Auswahl konnte nicht gespeichert werden. Bitte erneut versuchen."
            }
        }
    }

    companion object {
        fun factory(app: TransitWatchApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(WatchViewModel::class.java))
                return WatchViewModel(app) as T
            }
        }
    }
}
