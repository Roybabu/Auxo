package com.invision.pos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invision.pos.ui.AuthGate
import com.invision.pos.ui.InvisionApp
import com.invision.pos.ui.LocalStoreLogo
import com.invision.pos.ui.LocalSuppliersEnabled
import com.invision.pos.ui.theme.InvisionTheme

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        // Sign out anyone whose account was turned off or changed while the app was away.
        (application as InvisionApplication).container.authRepository.revalidate()
        // Make any automatic backup that came due while the app was closed.
        (application as InvisionApplication).container.checkAutoBackup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as InvisionApplication).container
        val appearance = container.appearanceRepository
        val shop = container.shopPreferences
        setContent {
            val settings by appearance.settings.collectAsStateWithLifecycle()
            // System bar icons are dark on the white theme and light on the black one.
            DisposableEffect(settings.darkMode) {
                val style = if (settings.darkMode) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val logo by container.storeLogoRepository.logo.collectAsStateWithLifecycle()
            val suppliers by shop.suppliers.collectAsStateWithLifecycle()
            InvisionTheme(accent = settings.accent, darkTheme = settings.darkMode) {
                CompositionLocalProvider(LocalSuppliersEnabled provides suppliers, LocalStoreLogo provides logo) {
                    val authState by container.authRepository.state.collectAsStateWithLifecycle()
                    val recoveryPrompt by container.authRepository.recoveryPrompt.collectAsStateWithLifecycle()
                    AuthGate(authState, recoveryPrompt = recoveryPrompt) { InvisionApp() }
                }
            }
        }
    }
}
