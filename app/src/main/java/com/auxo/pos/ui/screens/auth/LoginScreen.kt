package com.auxo.pos.ui.screens.auth

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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.foundation.text.KeyboardActions
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
import com.auxo.pos.ui.theme.AuthEmeraldBright
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.AuthInkMuted
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

/** Width over height of auxo_mark.png (the icon glyph alone, cropped from the approved vertical lockup). */
internal const val MARK_ASPECT = 261f / 191f

/** The sign-in screen: the first screen on every launch. [hasBusiness] is false until a business has been set up on this device. */
@Composable
fun LoginScreen(
    hasBusiness: Boolean = true,
    onSetUpBusiness: () -> Unit = {},
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
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
            topEnd = { LanguageChip() },
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
    topEnd: (@Composable () -> Unit)? = null,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White).testTag("auth_screen")) {
        // The picture sets the page's shape. On a wide or short window the column narrows so the whole picture still fits its height.
        val width = minOf(maxWidth, MAX_LOGIN_WIDTH, maxHeight / ART_ASPECT)
        val artHeight = width * ART_ASPECT
        val pageHeight = maxHeight
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
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxSize()
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
            keyboardActions = KeyboardActions(onDone = { onSignIn() }),
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
            OrDivider(stringResource(R.string.auth_login_setup_business))
            OutlinedButton(
                onClick = onSetUpBusiness,
                enabled = !state.isSigningIn,
                shape = RoundedCornerShape(32.dp),
                // Half the form's width, centred; the padding is trimmed so the icon, label and arrow still fit.
                contentPadding = PaddingValues(horizontal = 8.dp),
                // The glass draws its own rim, so the button's outline is switched off.
                border = null,
                // As tall as the sign-in slider including its raised edge.
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.5f)
                    .padding(horizontal = 1.dp) // 2 dp narrower overall, still centred
                    .height(62.dp)
                    .crystalGlass(RoundedCornerShape(32.dp), rgb = true)
                    .testTag("login_setup_business"),
            ) {
                AuxoIcon(R.drawable.ic_store, contentDescription = null, size = AuxoIconSize.Compact)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.auth_login_setup_business_action), color = AuthEmerald, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                AuxoIcon(R.drawable.ic_arrow_forward, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
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
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val bounce = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val signIn by rememberUpdatedState(onSignIn)
    val signingIn by rememberUpdatedState(isSigningIn)
    // When a sign-in finishes without leaving the screen, the thumb bounces back to the start.
    LaunchedEffect(isSigningIn) { if (!isSigningIn) offset.animateTo(0f, bounce) }

    val density = LocalDensity.current
    val depthColor = lerp(AuthEmerald, Color.Black, 0.4f)
    val shape = RoundedCornerShape(32.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SLIDER_HEIGHT + SLIDER_DEPTH)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (isSigningIn) disabled()
                onClick { if (!signingIn) signIn(); true }
            },
    ) {
        val travel = with(density) { (maxWidth - THUMB_SIZE - THUMB_INSET * 2).toPx() }.coerceAtLeast(1f)
        val progress = (offset.value / travel).coerceIn(0f, 1f)

        // The darker slab under the track is what makes it look raised.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(SLIDER_HEIGHT).background(depthColor, shape))
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(SLIDER_HEIGHT)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(AuthEmerald, AuthEmeraldBright)))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.10f))))
                .pointerInput(travel, isSigningIn) {
                    if (isSigningIn) return@pointerInput
                    // The sign-in fires the moment the thumb reaches the end (or is released past the threshold), not after the settle animation.
                    var fired = false
                    var position = offset.value
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
                    detectHorizontalDragGestures(
                        onDragStart = {
                            fired = false
                            position = offset.value
                        },
                        onDragEnd = {
                            when {
                                fired -> Unit // already on its way back, see fire()
                                position >= travel * SLIDE_THRESHOLD -> fire()
                                else -> scope.launch { offset.animateTo(0f, bounce) }
                            }
                        },
                        onDragCancel = { if (!fired) scope.launch { offset.animateTo(0f, bounce) } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            // Once the sign-in has fired, a finger still moving must not drag the thumb: that would cancel
                            // the animation that returns it, and it would stay stuck on the right.
                            if (fired) return@detectHorizontalDragGestures
                            position = (position + dx).coerceIn(0f, travel)
                            scope.launch { offset.snapTo(position) }
                            if (position >= travel) fire()
                        },
                    )
                }
                .pointerInput(travel, isSigningIn) {
                    if (isSigningIn) return@pointerInput
                    detectTapGestures(onTap = {
                        scope.launch {
                            offset.animateTo(travel * 0.25f, tween(120))
                            offset.animateTo(0f, bounce)
                        }
                    })
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
