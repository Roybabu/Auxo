package com.auxo.pos.ui.lock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.LockInfo
import com.auxo.pos.ui.components.AuthPillField
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoIconStyle
import com.auxo.pos.ui.components.OrDivider
import com.auxo.pos.ui.screens.auth.ART_ASPECT
import com.auxo.pos.ui.screens.auth.FORM_TOP
import com.auxo.pos.ui.screens.auth.LEAVES_ASPECT
import com.auxo.pos.ui.screens.auth.MARK_ASPECT
import com.auxo.pos.ui.screens.auth.MAX_LOGIN_WIDTH
import com.auxo.pos.ui.theme.AuthDotIdle
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthEmeraldBright
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.AuthInkMuted
import com.auxo.pos.ui.theme.AuthMint
import com.auxo.pos.ui.theme.AuthMintLight
import com.auxo.pos.ui.screens.auth.PasswordToggle
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.LockMessage
import com.auxo.pos.viewmodel.LockUiState
import com.auxo.pos.viewmodel.LockViewModel

/**
 * Covers the whole app while it is locked. The app underneath stays exactly as it was, so an unfinished bill
 * is still there after unlocking, but it cannot be seen, touched or typed into from here.
 */
@Composable
fun LockOverlay(content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            // Nothing that reaches this layer is passed on to the app below it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .testTag("lock_overlay"),
    ) { content() }
}

@Composable
fun LockScreen(viewModel: LockViewModel = viewModel(factory = LockViewModel.Factory)) {
    val info by viewModel.lock.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fingerprint = LocalFingerprint.current
    val context = LocalContext.current
    val title = stringResource(R.string.lock_fingerprint_title)
    val cancel = stringResource(R.string.lock_fingerprint_cancel)
    val askFingerprint = { fingerprint?.authenticate(title, cancel) { viewModel.fingerprintAccepted() } }
    // Offer the fingerprint as soon as the lock appears; the person can still cancel and type instead.
    LaunchedEffect(info.fingerprintAvailable, context) {
        if (info.fingerprintAvailable && fingerprint?.isAvailable() == true) askFingerprint()
    }
    LockContent(
        info = info,
        state = state,
        fingerprintOffered = info.fingerprintAvailable && fingerprint?.isAvailable() == true,
        onPinChange = viewModel::onPinChange,
        onUnlockWithPin = viewModel::unlockWithPin,
        onPasswordChange = viewModel::onPasswordChange,
        onUnlockWithPassword = viewModel::unlockWithPassword,
        onUseFingerprint = { askFingerprint() },
        onSignOut = viewModel::signOut,
    )
}

@Composable
fun LockContent(
    info: LockInfo,
    state: LockUiState,
    fingerprintOffered: Boolean,
    onPinChange: (String) -> Unit,
    onUnlockWithPin: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onUnlockWithPassword: () -> Unit,
    onUseFingerprint: () -> Unit,
    onSignOut: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }

    // Same picture and proportions as the sign-in page, so locking feels like a continuation of it.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White).testTag("auth_screen")) {
        val width = minOf(maxWidth, MAX_LOGIN_WIDTH, maxHeight / ART_ASPECT)
        val artHeight = width * ART_ASPECT
        val pageHeight = maxHeight
        Box(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.width(width).height(pageHeight).align(Alignment.TopCenter)) {
                // The picture is as tall as the page allows and sits at the bottom, so its curved white sheet and
                // leaves run to the screen's foot. Any spare height above it continues the picture's own top edge.
                val gap = pageHeight - artHeight
                if (gap > 0.dp) {
                    Box(modifier = Modifier.fillMaxWidth().height(gap + 1.dp).background(Brush.horizontalGradient(LOCK_TOP_EDGE)))
                }
                Image(
                    painter = painterResource(R.drawable.auth_lock_bg),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth().offset(y = gap).requiredHeight(artHeight),
                )
                LockBrand(modifier = Modifier.fillMaxWidth().height(gap + artHeight * FORM_TOP))
                // The controls scroll inside the sheet's clear area.
                Box(modifier = Modifier.fillMaxSize().padding(top = gap + artHeight * FORM_TOP)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                            .testTag("lock_screen"),
                    ) {
                        Text(
                            stringResource(R.string.lock_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = AuthInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.lock_body, info.userName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs).testTag("lock_user"),
                        )

                        if (info.quickUnlockOff) {
                            Text(
                                stringResource(R.string.lock_quick_off),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("lock_quick_off"),
                            )
                        }

                        if (fingerprintOffered) {
                            FingerprintButton(onClick = onUseFingerprint, modifier = Modifier.testTag("lock_fingerprint"))
                        }

                        val message = state.message
                        if (info.pinAvailable) {
                            AuthPillField(
                                value = state.pin,
                                onValueChange = onPinChange,
                                placeholder = stringResource(R.string.lock_field_pin),
                                icon = R.drawable.ic_lock,
                                highlightError = message is LockMessage.WrongPin,
                                keyboardType = KeyboardType.NumberPassword,
                                visualTransformation = PasswordVisualTransformation(),
                                imeAction = ImeAction.Done,
                                keyboardActions = KeyboardActions(onDone = { onUnlockWithPin() }),
                                enabled = !state.busy,
                                fieldHeight = LOCK_FIELD_HEIGHT,
                                modifier = Modifier.testTag("lock_pin"),
                            )
                            UnlockButton(
                                enabled = !state.busy && state.pin.isNotEmpty(),
                                busy = state.busy,
                                onClick = onUnlockWithPin,
                                modifier = Modifier.testTag("lock_pin_unlock"),
                            )
                        }

                        when (message) {
                            null -> Unit
                            is LockMessage.WrongPin -> LockError(pluralStringResource(R.plurals.lock_wrong_pin, message.triesLeft, message.triesLeft))
                            LockMessage.PinOff -> LockError(stringResource(R.string.lock_pin_off))
                            LockMessage.WrongPassword -> LockError(stringResource(R.string.lock_wrong_password))
                            is LockMessage.LockedOut -> LockError(pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds))
                            LockMessage.Unavailable -> LockError(stringResource(R.string.lock_unavailable))
                        }

                        if (info.pinAvailable || fingerprintOffered) {
                            OrDivider(stringResource(R.string.lock_or_password))
                        }
                        AuthPillField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            placeholder = stringResource(R.string.auth_field_password),
                            icon = R.drawable.ic_lock,
                            highlightError = message == LockMessage.WrongPassword,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                            imeAction = ImeAction.Done,
                            keyboardActions = KeyboardActions(onDone = { onUnlockWithPassword() }),
                            enabled = !state.busy,
                            fieldHeight = LOCK_FIELD_HEIGHT,
                            modifier = Modifier.testTag("lock_password"),
                        )
                        UnlockButton(
                            enabled = !state.busy && state.password.isNotEmpty(),
                            busy = state.busy,
                            onClick = onUnlockWithPassword,
                            modifier = Modifier.testTag("lock_password_unlock"),
                        )

                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth().testTag("lock_sign_out"),
                        ) {
                            AuxoIcon(R.drawable.ic_logout, contentDescription = null, size = AuxoIconSize.Mini)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.lock_sign_out), color = AuthEmerald, fontWeight = FontWeight.Medium)
                        }
                        // Keeps the last control clear of the leaf strip.
                        Spacer(Modifier.height(width * LEAVES_ASPECT * 0.4f))
                    }
                }
            }
        }
    }
}

private val LOCK_FIELD_HEIGHT = 51.dp

/** The top edge of auth_lock_bg, left to right, so the strip above the picture on a tall phone blends into it. */
private val LOCK_TOP_EDGE = listOf(0xFF022C24, 0xFF03342C, 0xFF064436, 0xFF08513F, 0xFF095A43, 0xFF086148, 0xFF0B634A, 0xFF095A43, 0xFF08523B).map { Color(it) }
private val LOCK_BUTTON_HEIGHT = 52.dp

/** The Auxo mark, name and tagline over the dark part of the picture. */
@Composable
private fun LockBrand(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.statusBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.auxo_mark),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.width(84.dp).aspectRatio(MARK_ASPECT),
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

/** "Use fingerprint": a pale-mint pill with the fingerprint tile at the left and an arrow at the right. */
@Composable
private fun FingerprintButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    OutlinedButton(
        onClick = onClick,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AuthMintLight),
        border = BorderStroke(1.dp, AuthMint),
        modifier = modifier.fillMaxWidth().height(LOCK_BUTTON_HEIGHT),
    ) {
        AuxoIcon(R.drawable.ic_fingerprint, contentDescription = null, size = AuxoIconSize.Compact)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.lock_use_fingerprint),
            color = AuthEmerald,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        AuxoIcon(R.drawable.ic_arrow_forward, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
    }
}

/** "Unlock": an emerald gradient pill with a light highlight, a soft shadow and a circled arrow (a spinner while working). */
@Composable
private fun UnlockButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    val fill = if (enabled || busy) {
        Brush.horizontalGradient(listOf(AuthEmerald, AuthEmeraldBright))
    } else {
        Brush.horizontalGradient(listOf(AuthDotIdle, AuthDotIdle))
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.White,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(LOCK_BUTTON_HEIGHT)
            .shadow(if (enabled) 6.dp else 0.dp, shape, ambientColor = AuthEmerald, spotColor = AuthEmerald),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fill, shape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.10f))), shape),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.lock_unlock),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled || busy) Color.White else AuthInkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(start = 40.dp),
                )
                Box(
                    modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = if (enabled || busy) 0.22f else 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        AuxoIcon(
                            R.drawable.ic_arrow_forward,
                            contentDescription = null,
                            size = AuxoIconSize.Mini,
                            style = AuxoIconStyle.Flat,
                            accent = if (enabled) Color.White else AuthInkMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockError(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lock_error"))
}
