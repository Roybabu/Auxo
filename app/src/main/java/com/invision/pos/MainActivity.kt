package com.invision.pos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invision.pos.ui.AuthGate
import com.invision.pos.ui.InvisionApp
import com.invision.pos.ui.LocalExpiryWarningDays
import com.invision.pos.ui.LocalStoreLogo
import com.invision.pos.ui.lock.BiometricFingerprintCheck
import com.invision.pos.ui.lock.LocalFingerprint
import com.invision.pos.ui.LocalSuppliersEnabled
import com.invision.pos.ui.theme.InvisionTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

class MainActivity : FragmentActivity() {

    /** Every screen reads its text through this context, so the chosen app language applies to all of them. */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.invision.pos.core.locale.LocaleContext.wrapForSaved(newBase))
    }

    override fun onStart() {
        super.onStart()
        // Sign out anyone whose account was turned off or changed while the app was away.
        (application as InvisionApplication).container.authRepository.revalidate()
        // Lock the app if it sat untouched for longer than the idle time while it was away.
        (application as InvisionApplication).container.authRepository.checkIdle()
        // Make any automatic backup that came due while the app was closed.
        (application as InvisionApplication).container.checkAutoBackup()
    }

    /** Any touch or key press counts as the app being in use, which restarts the idle time. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as InvisionApplication).container.authRepository.noteActivity()
    }

    private val fingerprint by lazy { BiometricFingerprintCheck(this) }

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
            val expiryDays by remember { container.settingsRepository.observeSettings().map { it.expiryWarningDays } }
                .collectAsStateWithLifecycle(com.invision.pos.domain.model.ExpiryWarning.DEFAULT_DAYS)
            InvisionTheme(accent = settings.accent, darkTheme = settings.darkMode) {
                CompositionLocalProvider(LocalSuppliersEnabled provides suppliers, LocalExpiryWarningDays provides expiryDays, LocalStoreLogo provides logo, LocalFingerprint provides fingerprint, com.invision.pos.ui.LocalProductPhotos provides container.productPhotos) {
                    val authState by container.authRepository.state.collectAsStateWithLifecycle()
                    val recoveryPrompt by container.authRepository.recoveryPrompt.collectAsStateWithLifecycle()
                    val lock by container.authRepository.lock.collectAsStateWithLifecycle()
                    // Checks every few seconds whether the idle time has run out.
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(IDLE_CHECK_MILLIS)
                            container.authRepository.checkIdle()
                        }
                    }
                    AuthGate(authState, recoveryPrompt = recoveryPrompt, lock = lock) { InvisionApp() }
                }
            }
        }
    }

    private companion object {
        const val IDLE_CHECK_MILLIS = 5_000L
    }
}
