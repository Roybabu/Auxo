// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos

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
import com.auxo.pos.core.auth.AuthState
import com.auxo.pos.ui.AuthGate
import com.auxo.pos.ui.AuxoApp
import com.auxo.pos.ui.LocalExpiryWarningDays
import com.auxo.pos.ui.LocalStoreLogo
import com.auxo.pos.ui.lock.BiometricFingerprintCheck
import com.auxo.pos.ui.lock.LocalFingerprint
import com.auxo.pos.ui.LocalSuppliersEnabled
import com.auxo.pos.ui.theme.AuxoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainActivity : FragmentActivity() {

    /** Every screen reads its text through this context, so the chosen app language applies to all of them. */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.auxo.pos.core.locale.LocaleContext.wrapForSaved(newBase))
    }

    override fun onStart() {
        super.onStart()
        // Sign out anyone whose account was turned off or changed while the app was away.
        (application as AuxoApplication).container.authRepository.revalidate()
        // Lock the app if it sat untouched for longer than the idle time while it was away.
        (application as AuxoApplication).container.authRepository.checkIdle()
        // Make any automatic backup that came due while the app was closed.
        (application as AuxoApplication).container.checkAutoBackup()
    }

    /** Any touch or key press counts as the app being in use, which restarts the idle time. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as AuxoApplication).container.authRepository.noteActivity()
    }

    private val fingerprint by lazy { BiometricFingerprintCheck(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as AuxoApplication).container
        container.startRestaurantModeSync()
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
            val restaurant by shop.restaurantMenu.collectAsStateWithLifecycle()
            // Settings may only be read by someone signed in, so nothing is read before sign-in (or if it is refused).
            val expiryDays by remember {
                container.authRepository.state.flatMapLatest { auth ->
                    if (auth is AuthState.SignedIn) {
                        container.settingsRepository.observeSettings().map { it.expiryWarningDays }
                            .catch { emit(com.auxo.pos.domain.model.ExpiryWarning.DEFAULT_DAYS) }
                    } else {
                        flowOf(com.auxo.pos.domain.model.ExpiryWarning.DEFAULT_DAYS)
                    }
                }
            }.collectAsStateWithLifecycle(com.auxo.pos.domain.model.ExpiryWarning.DEFAULT_DAYS)
            // Categories may only be read by someone signed in, like the other shop data.
            val categories by remember {
                container.authRepository.state.flatMapLatest { auth ->
                    if (auth is AuthState.SignedIn) {
                        container.categoryRepository.observeCategories().catch { emit(emptyList()) }
                    } else {
                        flowOf(emptyList())
                    }
                }
            }.collectAsStateWithLifecycle(emptyList())
            AuxoTheme(accent = settings.accent, darkTheme = settings.darkMode) {
                CompositionLocalProvider(com.auxo.pos.ui.LocalCategories provides categories, LocalSuppliersEnabled provides suppliers, com.auxo.pos.ui.LocalRestaurantEnabled provides restaurant, LocalExpiryWarningDays provides expiryDays, LocalStoreLogo provides logo, LocalFingerprint provides fingerprint, com.auxo.pos.ui.LocalProductPhotos provides container.productPhotos, com.auxo.pos.ui.LocalReceiptPrinting provides container.receiptPrinting) {
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
                    val categorySetupDone by shop.categorySetupDone.collectAsStateWithLifecycle()
                    val mayChooseCategories = (authState as? AuthState.SignedIn)?.permissions?.contains(com.auxo.pos.core.auth.Permission.MANAGE_SETTINGS) == true
                    val onboardingSeen by shop.onboardingSeen.collectAsStateWithLifecycle()
                    AuthGate(
                        authState,
                        recoveryPrompt = recoveryPrompt,
                        lock = lock,
                        categoryPrompt = mayChooseCategories && !categorySetupDone,
                        onboarding = !onboardingSeen,
                        savedSessionName = remember(authState) { container.authRepository.savedSessionName() },
                    ) { AuxoApp() }
                    // Once someone is signed in, look quietly for a newer version and offer it if there is one.
                    if (authState is AuthState.SignedIn) {
                        LaunchedEffect(Unit) { container.appUpdater.checkOnLaunch() }
                        com.auxo.pos.ui.screens.more.UpdatePrompt()
                    }
                }
            }
        }
    }

    private companion object {
        const val IDLE_CHECK_MILLIS = 5_000L
    }
}
