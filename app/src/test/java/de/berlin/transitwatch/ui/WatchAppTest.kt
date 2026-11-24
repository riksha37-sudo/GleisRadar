package de.berlin.transitwatch.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.berlin.transitwatch.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test fun overviewOffersManualPreviewAndOfficialLinksWithSeparateDiagnosis() {
        compose.onNodeWithText("Alexanderplatz").assertIsDisplayed()
        compose.onNodeWithText("Meldungen abrufen").assertIsDisplayed()
        compose.onNodeWithText("S-Bahn Berlin").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("BVG · U-Bahn").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Diagnose").performClick()
        compose.onNodeWithText("Demo und Testdaten").assertIsDisplayed()
        compose.onNodeWithText("Testmeldungen laden").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Übersicht").performClick()
        compose.onNodeWithText("Meldungen abrufen").performScrollTo().assertIsDisplayed()
    }
}
