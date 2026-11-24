package de.berlin.transitwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import de.berlin.transitwatch.ui.WatchApp
import de.berlin.transitwatch.ui.WatchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: WatchViewModel = viewModel(factory = WatchViewModel.factory(application as TransitWatchApplication))
            WatchApp(vm)
        }
    }
}
