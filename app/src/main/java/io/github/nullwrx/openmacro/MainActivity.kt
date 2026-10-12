package io.github.nullwrx.openmacro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nullwrx.openmacro.data.settings.UserSettings
import io.github.nullwrx.openmacro.ui.AppNavHost
import io.github.nullwrx.openmacro.ui.theme.OpenMacroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OpenMacroApp).container
        setContent {
            val settings: UserSettings? by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val current = settings
            OpenMacroTheme(
                themeMode = current?.theme ?: io.github.nullwrx.openmacro.data.settings.ThemeMode.SYSTEM,
                dynamicColor = current?.dynamicColor ?: false,
            ) {
                // Wait for settings before choosing the start screen, so onboarding never flashes.
                if (current != null) AppNavHost(startOnboarding = !current.onboarded)
            }
        }
    }
}
