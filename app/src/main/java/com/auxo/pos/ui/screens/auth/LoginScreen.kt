// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.auth

import com.auxo.pos.ui.components.liquidGlass
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.components.AuthPillField
import com.auxo.pos.ui.components.LanguageChip
import com.auxo.pos.ui.components.OrDivider
import com.auxo.pos.ui.components.crystalGlass
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.AuthCream
import com.auxo.pos.ui.theme.AuthMint
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.LoginMessage
import com.auxo.pos.viewmodel.LoginUiState
import com.auxo.pos.viewmodel.LoginViewModel
import com.auxo.pos.ui.components.AuxoIconStyle
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoIcon
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBarsPadding
import com.auxo.pos.viewmodel.FindBackupViewModel
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult

/** Width over height of auxo_mark.png (the icon glyph alone, cropped from the approved vertical lockup). */
internal const val MARK_ASPECT = 261f / 191f

/** How the button under the sign-in slider looks and what it says; null keeps the usual "Get started". */
class SetUpButton(
    val label: String,
    @DrawableRes val icon: Int,
    val divider: String,
    val enabled: Boolean = true,
    /** A short line under the button, such as "No backups found in that folder." */
    val note: String? = null,
    /** A smaller second choice under the button, such as "Choose another folder". */
    val secondaryLabel: String? = null,
    val onSecondary: () -> Unit = {},
)

/** The sign-in screen: the first screen on every launch. [hasBusiness] is false until a business has been set up on this device. */
@Composable
fun LoginScreen(
    hasBusiness: Boolean = true,
    onSetUpBusiness: () -> Unit = {},
    /** On a phone with no business, the button first asks for the folder holding old backups (see [BackupAwareLogin]). */
    findBackups: Boolean = false,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    if (findBackups && !hasBusiness) {
        BackupAwareLogin(onSetUpBusiness = onSetUpBusiness, loginViewModel = viewModel)
        return
    }
    LoginScreenBody(hasBusiness, onSetUpBusiness, null, viewModel)
}

@Composable
private fun LoginScreenBody(
    hasBusiness: Boolean,
    onSetUpBusiness: () -> Unit,
    setUpButton: SetUpButton?,
    viewModel: LoginViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var resetting by rememberSaveable { mutableStateOf(false) }
    if (resetting && hasBusiness) {
        PasswordResetScreen(onBack = { resetting = false })
    } else {
        LoginContent(
            state, viewModel::onIdentifierChange, viewModel::onPasswordChange, viewModel::signIn,
            // Nothing to recover when this device has no business yet.
            onForgotPassword = if (hasBusiness) ({ resetting = true }) else null,
            // Once a business exists on this device, "Get started" is hidden.
            onSetUpBusiness = if (hasBusiness) null else onSetUpBusiness,
            setUpButton = setUpButton,
            topEnd = { LanguageChip() },
        )
    }
}

/**
 * The sign-in page of a phone with no business. The button under the slider first asks for the folder holding old
 * backups. With backups in it, they are listed to restore; with none (or if the picker is closed), the button
 * becomes "Set up a new shop".
 */
/** Documents/Auxo on the phone's main storage, as a hint for where the folder picker should open. */
private val AUXO_BACKUP_FOLDER: android.net.Uri =
    android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/Auxo")

@Composable
private fun BackupAwareLogin(onSetUpBusiness: () -> Unit, loginViewModel: LoginViewModel) {
    val finder: FindBackupViewModel = viewModel(factory = FindBackupViewModel.Factory)
    val found by finder.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) {
            finder.onPickerClosed()
        } else {
            // Keep read access for as long as the phone allows, so a restore that starts later can still open the file.
            runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            finder.onFolderChosen(tree)
        }
    }
    val hasBackups = found.found.orEmpty().isNotEmpty()
    val noneFound = found.found != null && !found.scanning && !hasBackups
    val newShop = noneFound || found.skipped
    // Opens the folder picker at Documents/Auxo, where Auxo saves backups; the phone falls back to its usual start if it is missing.
    val openPicker = { picker.launch(AUXO_BACKUP_FOLDER) }
    // A new owner can set up straight away; restoring from a backup is the other choice and is the only thing that opens the picker.
    val button = if (found.scanning) {
        SetUpButton(stringResource(R.string.find_scanning), R.drawable.ic_search, stringResource(R.string.find_divider), enabled = false)
    } else {
        SetUpButton(
            label = stringResource(R.string.find_new),
            icon = R.drawable.ic_store,
            divider = stringResource(R.string.auth_login_setup_business),
            note = if (noneFound) stringResource(R.string.find_none_short) else null,
            secondaryLabel = stringResource(if (newShop) R.string.find_another else R.string.find_restore),
            onSecondary = openPicker,
        )
    }
    LoginScreenBody(
        hasBusiness = false,
        onSetUpBusiness = onSetUpBusiness,
        setUpButton = button,
        viewModel = loginViewModel,
    )
    // Backups found in the folder: a pop-up over this page lists them as blocks, with a new shop as the other choice.
    if (hasBackups || found.restoring) {
        FindBackupDialog(
            viewModel = finder,
            onSetUpNewShop = { finder.onChooseAnother(); onSetUpBusiness() },
            onChooseAnother = { finder.onChooseAnother(); openPicker() },
        )
    }
}

/*
 * Layers, back to front, on one portrait page:
 *   1. the green picture (auth_login_bg), drawn at its own shape across the width and never covered or cropped on
 *      the sides: it holds the dark landscape, the saplings and its curved white sheet at the bottom;
 *   2. the Auxo mark, name and the pitch, over the dark part of the picture;
 *   3. the form, inside the picture's white sheet only. It has no card or background of its own, and it scrolls
 *      inside that area when the keyboard is up or the phone is short, so the picture never moves or hides.
 */

/** Height over width of auth_login_bg. */
internal const val ART_ASPECT = 1672f / 941f

/** Height over width of auth_login_leaves, the leaf strip at the foot of the white sheet. */
internal const val LEAVES_ASPECT = 372f / 941f

/** Where the white sheet is safe to write on, as a share of the picture's height (its curved edge peaks near 0.54). */
internal const val FORM_TOP = 0.585f

/** How much of the picture's height the pitch text may use, so it stays on the dark part. */
private const val PITCH_BOTTOM = 0.50f

/** The sign-in fields' height: 5 dp under the 56 dp Material default. */
private val LOGIN_FIELD_HEIGHT = 51.dp

/** Windows shorter than this get the plain scrolling page instead of the picture. */
internal val COMPACT_BELOW = 560.dp

/** How much height the sign-in form needs, without and with the "Get started" button. */
private val FORM_HEIGHT = 340.dp
private val FORM_HEIGHT_WITH_SETUP = 420.dp

/** The widest the page is drawn; on a tablet it is a centred phone-shaped column. */
internal val MAX_LOGIN_WIDTH = 480.dp

@Composable
fun LoginContent(
    state: LoginUiState,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: (() -> Unit)? = null,
    onSetUpBusiness: (() -> Unit)? = null,
    setUpButton: SetUpButton? = null,
    topEnd: (@Composable () -> Unit)? = null,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AuthCream).testTag("auth_screen")) {
        // The picture sets the page's shape. On a wide or short window the column narrows so the whole picture still fits its height.
        // A short window (split screen, a landscape phone) cannot hold the picture and the form, so it gets a plain
        // page that scrolls: a green band with the logo, then the form.
        if (maxHeight < COMPACT_BELOW) {
            CompactLogin(
                state = state,
                onIdentifierChange = onIdentifierChange,
                onPasswordChange = onPasswordChange,
                onSignIn = onSignIn,
                onForgotPassword = onForgotPassword,
                onSetUpBusiness = onSetUpBusiness,
                setUpButton = setUpButton,
                showPassword = showPassword,
                onTogglePassword = { showPassword = !showPassword },
            )
            if (topEnd != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)) { topEnd() }
            }
            return@BoxWithConstraints
        }
        val width = minOf(maxWidth, MAX_LOGIN_WIDTH, maxHeight / ART_ASPECT)
        val artHeight = width * ART_ASPECT
        // Never shorter than the form needs below the picture's curve, so on a smaller phone the page scrolls
        // instead of cutting the form off.
        val formNeed = if (onSetUpBusiness != null) FORM_HEIGHT_WITH_SETUP else FORM_HEIGHT
        val pageHeight = maxOf(maxHeight, artHeight * FORM_TOP + formNeed)
        // The page keeps its exact shape. Only when the keyboard is up does the viewport shrink and scroll, so the field being typed in is never hidden.
        Box(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.width(width).height(pageHeight).align(Alignment.TopCenter)) {
            Image(
                painter = painterResource(R.drawable.auth_login_bg),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().requiredHeight(artHeight),
            )
            LoginPitch(modifier = Modifier.fillMaxWidth().height(artHeight * PITCH_BOTTOM))
            // The form region: from the top of the white sheet to the bottom of the screen, and nothing above it.
            Box(modifier = Modifier.fillMaxSize().padding(top = artHeight * FORM_TOP)) {
                // The leaf strip sits behind the form, flush with the bottom of the screen. The form scrolls clear of it.
                Image(
                    painter = painterResource(R.drawable.auth_login_leaves),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                )
                LoginForm(
                    bottomClearance = width * LEAVES_ASPECT * 0.4f,
                    state = state,
                    onIdentifierChange = onIdentifierChange,
                    onPasswordChange = onPasswordChange,
                    onSignIn = onSignIn,
                    onForgotPassword = onForgotPassword,
                    onSetUpBusiness = onSetUpBusiness,
                    setUpButton = setUpButton,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword },
                )
            }
        }
        }
        if (topEnd != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)) { topEnd() }
        }
    }
}

/** The sign-in page for a short window: the logo on a green band, then the form, all in one scrolling column. */
@Composable
private fun CompactLogin(
    state: LoginUiState,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: (() -> Unit)?,
    onSetUpBusiness: (() -> Unit)?,
    setUpButton: SetUpButton?,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("login_compact"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(AuthInk, AuthEmerald)))
                .statusBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Image(
                painter = painterResource(R.drawable.auxo_mark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.width(52.dp).aspectRatio(MARK_ASPECT),
            )
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
            }
        }
        LoginForm(
            bottomClearance = Spacing.lg,
            state = state,
            onIdentifierChange = onIdentifierChange,
            onPasswordChange = onPasswordChange,
            onSignIn = onSignIn,
            onForgotPassword = onForgotPassword,
            onSetUpBusiness = onSetUpBusiness,
            setUpButton = setUpButton,
            showPassword = showPassword,
            onTogglePassword = onTogglePassword,
            modifier = Modifier.widthIn(max = MAX_LOGIN_WIDTH).fillMaxWidth(),
        )
    }
}

/** The Auxo mark, name and tagline, and the "Manage / Sell / Grow" pitch, written over the dark part of the picture. */
@Composable
private fun LoginPitch(modifier: Modifier = Modifier) {
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
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.auth_login_hero_headline),
                // Larger than before, with a little air between Manage, Sell and Grow.
                style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 36.sp),
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Text(
                stringResource(R.string.auth_login_hero_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Spacing.xs).widthIn(max = 260.dp),
            )
        }
        Spacer(Modifier.weight(0.6f))
    }
}

/** The sign-in fields and a way to set up a new business, scrolling inside the picture's white sheet. */
@Composable
private fun LoginForm(
    bottomClearance: Dp,
    state: LoginUiState,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: (() -> Unit)?,
    onSetUpBusiness: (() -> Unit)?,
    setUpButton: SetUpButton?,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    /** The picture layout gives the form the whole white sheet; the compact page lets it be as tall as it is. */
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("login"),
    ) {
        Text(
            stringResource(R.string.auth_login_welcome),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AuthInk,
            textAlign = TextAlign.Center,
            // Nudged 2 dp down without moving anything below it.
            modifier = Modifier.fillMaxWidth().offset(y = 2.dp),
        )
        Text(
            stringResource(R.string.auth_login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        )
        AuthPillField(
            value = state.identifier,
            onValueChange = onIdentifierChange,
            placeholder = stringResource(R.string.auth_field_identifier),
            icon = R.drawable.ic_mail,
            highlightError = state.identifierMissing || state.message == LoginMessage.Rejected,
            keyboardType = KeyboardType.Email,
            enabled = !state.isSigningIn,
            fieldHeight = LOGIN_FIELD_HEIGHT,
            modifier = Modifier.testTag("login_identifier"),
        )
        AuthPillField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = stringResource(R.string.auth_field_password),
            icon = R.drawable.ic_lock,
            highlightError = state.passwordMissing || state.message == LoginMessage.Rejected,
            keyboardType = KeyboardType.Password,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { PasswordToggle(showPassword, onTogglePassword) },
            imeAction = ImeAction.Done,
            enabled = !state.isSigningIn,
            fieldHeight = LOGIN_FIELD_HEIGHT,
            modifier = Modifier.testTag("login_password"),
        )
        // One shared line under the password for every sign-in problem, to save space.
        val message = state.message
        val errorText = when {
            state.identifierMissing -> stringResource(R.string.auth_error_login_identifier_required)
            state.passwordMissing -> stringResource(R.string.auth_error_login_password_required)
            message == null -> null
            else -> when (message) {
                LoginMessage.Rejected -> stringResource(R.string.auth_login_rejected)
                LoginMessage.Deactivated -> stringResource(R.string.auth_login_deactivated)
                is LoginMessage.LockedOut -> pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds)
                LoginMessage.NoAccount -> stringResource(R.string.auth_login_no_account)
                LoginMessage.Unavailable -> stringResource(R.string.auth_login_unavailable)
            }
        }
        // The error shares the row with "Forgot password?" so it costs no extra height.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f).testTag("login_error"),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (onForgotPassword != null) {
                TextButton(onClick = onForgotPassword, enabled = !state.isSigningIn, modifier = Modifier.testTag("login_forgot")) {
                    Text(stringResource(R.string.auth_login_forgot), color = AuthEmerald, fontWeight = FontWeight.Medium)
                }
            }
        }
        SlideToSignIn(isSigningIn = state.isSigningIn, onSignIn = onSignIn, modifier = Modifier.padding(horizontal = 2.dp).testTag("login_submit"))
        // Only offered until a business has been set up on this device.
        if (onSetUpBusiness != null) {
            OrDivider(setUpButton?.divider ?: stringResource(R.string.auth_login_setup_business))
            OutlinedButton(
                onClick = onSetUpBusiness,
                enabled = !state.isSigningIn && (setUpButton?.enabled ?: true),
                shape = RoundedCornerShape(32.dp),
                // Half the form's width, centred; the padding is trimmed so the icon, label and arrow still fit.
                contentPadding = PaddingValues(horizontal = 8.dp),
                // The glass draws its own rim, so the button's outline is switched off.
                border = null,
                // As tall as the sign-in slider including its raised edge.
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(if (setUpButton != null) 0.8f else 0.5f)
                    .padding(horizontal = 1.dp) // 2 dp narrower overall, still centred
                    .height(62.dp)
                    .crystalGlass(RoundedCornerShape(32.dp), rgb = true)
                    .testTag("login_setup_business"),
            ) {
                AuxoIcon(setUpButton?.icon ?: R.drawable.ic_store, contentDescription = null, size = AuxoIconSize.Compact)
                Spacer(Modifier.width(12.dp))
                Text(setUpButton?.label ?: stringResource(R.string.auth_login_setup_business_action), color = AuthEmerald, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                AuxoIcon(R.drawable.ic_arrow_forward, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
            }
            setUpButton?.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("login_setup_note"),
                )
            }
            setUpButton?.secondaryLabel?.let {
                TextButton(onClick = setUpButton.onSecondary, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("login_setup_secondary")) {
                    Text(it, color = AuthEmerald)
                }
            }
        }
        Spacer(Modifier.height(bottomClearance))
    }
}

private val SLIDER_HEIGHT = 50.dp
private val SLIDER_DEPTH = 6.dp
private val THUMB_SIZE = 38.dp
private val THUMB_INSET = 6.dp

/** How far along the track (0..1) a release still counts as a sign-in. */
private const val SLIDE_THRESHOLD = 0.75f

/**
 * The sign-in control: a raised, 3D pill with a thumb to slide to the end. Letting go early springs the thumb back
 * with a bounce; a tap nudges it as a hint. Accessibility services get a plain click that signs in.
 */
@Composable
internal fun SlideToSignIn(
    isSigningIn: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.auth_login_slide),
    workingLabel: String = stringResource(R.string.auth_login_working),
    thumbIcon: Int = R.drawable.ic_arrow_forward,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val bounce = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val signIn by rememberUpdatedState(onSignIn)
    val signingIn by rememberUpdatedState(isSigningIn)
    // When a sign-in finishes without leaving the screen, the thumb bounces back to the start.
    LaunchedEffect(isSigningIn) { if (!isSigningIn) offset.animateTo(0f, bounce) }

    val density = LocalDensity.current
    val shape = RoundedCornerShape(32.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SLIDER_HEIGHT + SLIDER_DEPTH)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (isSigningIn || !enabled) disabled()
                onClick { if (!signingIn && enabled) signIn(); true }
            },
    ) {
        val travel = with(density) { (maxWidth - THUMB_SIZE - THUMB_INSET * 2).toPx() }.coerceAtLeast(1f)
        val progress = (offset.value / travel).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(SLIDER_HEIGHT)
                // A slider that cannot be used yet (nothing typed) is dimmed and does not move.
                .alpha(if (enabled || isSigningIn) 1f else 0.5f)
                // Liquid glass, like the other sign-in buttons: the body, highlight and rim are drawn behind the sliding thumb.
                .liquidGlass(shape)
                .clip(shape)
                .pointerInput(travel, isSigningIn, enabled) {
                    if (isSigningIn) return@pointerInput
                    // The slider takes the whole gesture. It looks at the finger in the Initial pass, before the page's
                    // vertical scrolling can see it, and keeps every move once the finger has gone past the touch slop in
                    // any direction. Left to the usual horizontal-drag detector, a slightly slanted slide was taken over by
                    // the scrolling page and the thumb sprang back, so it took several tries.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!enabled) return@awaitEachGesture
                        var position = offset.value
                        var moved = false
                        var fired = false
                        var travelled = Offset.Zero
                        // The sign-in fires the moment the thumb reaches the end (or is released past the threshold), not after the settle animation.
                        fun fire() {
                            if (fired) return
                            fired = true
                            signIn()
                            scope.launch {
                                offset.animateTo(travel, tween(80))
                                delay(400)
                                // While the sign-in runs the thumb waits at the end; the effect above brings it back when it stops.
                                if (!signingIn) offset.animateTo(0f, bounce)
                            }
                        }
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                if (!fired) scope.launch { offset.animateTo(0f, bounce) }
                                break
                            }
                            if (!change.pressed) {
                                when {
                                    // A tap nudges the thumb as a hint.
                                    !moved -> scope.launch {
                                        offset.animateTo(travel * 0.25f, tween(120))
                                        offset.animateTo(0f, bounce)
                                    }
                                    fired -> Unit // already on its way back, see fire()
                                    position >= travel * SLIDE_THRESHOLD -> fire()
                                    else -> scope.launch { offset.animateTo(0f, bounce) }
                                }
                                break
                            }
                            val delta = change.positionChange()
                            travelled += delta
                            if (!moved && travelled.getDistance() <= viewConfiguration.touchSlop) continue
                            moved = true
                            change.consume()
                            // Once the sign-in has fired, a finger still moving must not drag the thumb: that would cancel
                            // the animation that returns it, and it would stay stuck on the right.
                            if (fired) continue
                            position = (position + delta.x).coerceIn(0f, travel)
                            scope.launch { offset.snapTo(position) }
                            if (position >= travel) fire()
                        }
                    }
                },
        ) {
            Text(
                if (isSigningIn) workingLabel else label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).alpha(1f - progress),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = THUMB_INSET)
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .shadow(6.dp, CircleShape)
                    .size(THUMB_SIZE)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White, AuthMint))),
                contentAlignment = Alignment.Center,
            ) {
                AuxoIcon(thumbIcon, contentDescription = null, size = AuxoIconSize.Medium, style = AuxoIconStyle.Flat)
            }
        }
    }
}
