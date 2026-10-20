package com.auxo.pos.ui.lock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoIconStyle
import com.auxo.pos.ui.screens.auth.MARK_ASPECT
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthEmeraldBright
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
    val message = state.message

    // One scene: the botanical picture fills the width, fades into the deep emerald below it, and the controls sit straight on top.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(LockDeep).clipToBounds().testTag("auth_screen")) {
        val pictureHeight = maxWidth * BG_ASPECT
        val visibleArt = minOf(pictureHeight, maxHeight)
        Image(
            painter = painterResource(R.drawable.auth_login_bg),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth(),
        )
        // The picture's pale hills would swallow white text, so its lower part fades into the page colour.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = visibleArt * 0.55f)
                .fillMaxWidth()
                .height(visibleArt * 0.45f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, LockDeep.copy(alpha = 0.92f), LockDeep))),
        )

        // The controls start below the saplings where the screen is tall enough, and move up where it is not.
        val controlsTop = minOf(pictureHeight * 0.72f, maxHeight * 0.38f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        ) {
            LockBrand(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height((controlsTop - LOCK_BRAND_HEIGHT).coerceAtLeast(0.dp)))
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .testTag("lock_screen"),
            ) {
                Text(
                    stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.headlineMedium.copy(shadow = LockTextShadow),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Text(
                    stringResource(R.string.lock_body, info.userName),
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = LockTextShadow),
                    color = LockPaleMint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs).testTag("lock_user"),
                )

                if (info.quickUnlockOff) {
                    Text(
                        stringResource(R.string.lock_quick_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LockError,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("lock_quick_off"),
                    )
                }

                if (fingerprintOffered) {
                    FingerprintAction(onClick = onUseFingerprint, modifier = Modifier.testTag("lock_fingerprint"))
                }

                if (info.pinAvailable) {
                    LockField(
                        value = state.pin,
                        onValueChange = onPinChange,
                        placeholder = stringResource(R.string.lock_field_pin),
                        isError = message is LockMessage.WrongPin,
                        keyboardType = KeyboardType.NumberPassword,
                        visualTransformation = PasswordVisualTransformation(),
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { onUnlockWithPin() }),
                        enabled = !state.busy,
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
                    is LockMessage.WrongPin -> LockErrorText(pluralStringResource(R.plurals.lock_wrong_pin, message.triesLeft, message.triesLeft))
                    LockMessage.PinOff -> LockErrorText(stringResource(R.string.lock_pin_off))
                    LockMessage.WrongPassword -> LockErrorText(stringResource(R.string.lock_wrong_password))
                    is LockMessage.LockedOut -> LockErrorText(pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds))
                    LockMessage.Unavailable -> LockErrorText(stringResource(R.string.lock_unavailable))
                }

                if (info.pinAvailable || fingerprintOffered) {
                    LockSeparator(stringResource(R.string.lock_or_password))
                }
                LockField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    placeholder = stringResource(R.string.auth_field_password),
                    isError = message == LockMessage.WrongPassword,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.testTag("password_toggle")) {
                            AuxoIcon(
                                if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                stringResource(if (showPassword) R.string.auth_hide_password else R.string.auth_show_password),
                                size = AuxoIconSize.Mini,
                                style = AuxoIconStyle.Flat,
                                accent = LockMint,
                            )
                        }
                    },
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { onUnlockWithPassword() }),
                    enabled = !state.busy,
                    modifier = Modifier.testTag("lock_password"),
                )
                UnlockButton(
                    enabled = !state.busy && state.password.isNotEmpty(),
                    busy = state.busy,
                    onClick = onUnlockWithPassword,
                    modifier = Modifier.testTag("lock_password_unlock"),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = onSignOut)
                        .testTag("lock_sign_out"),
                ) {
                    AuxoIcon(R.drawable.ic_logout, contentDescription = null, size = AuxoIconSize.Mini, style = AuxoIconStyle.Flat, accent = LockMint)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.lock_sign_out),
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = LockTextShadow),
                        color = LockPaleMint,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(Spacing.md))
            }
        }
    }
}

// The scene's palette: deep emerald page, mint highlights, soft-white text.
private val LockDeep = Color(0xFF062E26)
private val LockEmerald = Color(0xFF168866)
private val LockMint = Color(0xFFBDF5D8)
private val LockPaleMint = Color(0xFFDDF7EA)
private val LockError = Color(0xFFFFB4AB)
private val LockTextShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 6f)

/** Height over width of auth_login_bg. */
private const val BG_ASPECT = 1103f / 941f
private val LOCK_BUTTON_HEIGHT = 52.dp
/** Status bar is not counted: the brand block's own height below it, used to place the controls. */
private val LOCK_BRAND_HEIGHT = 130.dp

/** The Auxo mark, name and tagline over the dark top of the picture. */
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

/** "Use fingerprint": icon, label and arrow straight on the picture; the press shows as a ripple, nothing is drawn at rest. */
@Composable
private fun FingerprintAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        AuxoIcon(R.drawable.ic_fingerprint, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat, accent = LockMint)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.lock_use_fingerprint),
            style = MaterialTheme.typography.titleMedium.copy(shadow = LockTextShadow),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(Modifier.width(10.dp))
        AuxoIcon(R.drawable.ic_arrow_forward, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat, accent = LockMint)
    }
}

/** A borderless input: no container, white text, a thin mint line underneath. The lock icon sits at the left. */
@Composable
private fun LockField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        isError = isError,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        placeholder = { Text(placeholder, color = LockPaleMint.copy(alpha = 0.7f)) },
        leadingIcon = {
            AuxoIcon(R.drawable.ic_lock, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat, accent = LockMint)
        },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.5f),
            errorTextColor = Color.White,
            cursorColor = LockMint,
            errorCursorColor = LockError,
            focusedIndicatorColor = LockMint,
            unfocusedIndicatorColor = LockMint.copy(alpha = 0.45f),
            disabledIndicatorColor = LockMint.copy(alpha = 0.2f),
            errorIndicatorColor = LockError,
        ),
    )
}

/** "Or use your password" between two thin translucent lines. */
@Composable
private fun LockSeparator(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = LockMint.copy(alpha = 0.3f))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(shadow = LockTextShadow), color = LockPaleMint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        HorizontalDivider(modifier = Modifier.weight(1f), color = LockMint.copy(alpha = 0.3f))
    }
}

/** "Unlock": an emerald pill with a light highlight, a soft glow and a circled arrow (a spinner while working). */
@Composable
private fun UnlockButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    val live = enabled || busy
    val fill = if (live) Brush.horizontalGradient(listOf(LockEmerald, AuthEmeraldBright)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.14f)))
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
            .shadow(if (enabled) 8.dp else 0.dp, shape, ambientColor = LockMint, spotColor = LockMint),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fill, shape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (live) 0.24f else 0f), Color.Transparent, Color.Black.copy(alpha = 0.10f))), shape)
                .border(1.dp, LockMint.copy(alpha = if (live) 0.35f else 0.18f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.lock_unlock),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (live) Color.White else Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(start = 40.dp),
                )
                Box(
                    modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = if (live) 0.22f else 0.10f), CircleShape),
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
                            accent = if (live) Color.White else Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockErrorText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(shadow = LockTextShadow),
        color = LockError,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("lock_error"),
    )
}
