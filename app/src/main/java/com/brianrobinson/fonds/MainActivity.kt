package com.brianrobinson.fonds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.ui.FondsNavHost
import com.brianrobinson.fonds.ui.theme.FondsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FondsApp).container
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalContainer provides container) {
                FondsTheme(themeMode = settings.theme, dynamicColor = settings.dynamicColor) {
                    FondsNavHost()
                }
            }
        }
    }
}
