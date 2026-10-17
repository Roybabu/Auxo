package com.auxo.pos.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBarsPadding

/** Width over height of auxo_mark.png (the icon glyph alone, cropped from the approved vertical lockup). */
private const val MARK_ASPECT = 261f / 191f

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
            onSetUpBusiness = onSetUpBusiness,
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
private const val ART_ASPECT = 1672f / 941f

/** Height over width of auth_login_leaves, the leaf strip at the foot of the white sheet. */
private const val LEAVES_ASPECT = 372f / 941f

/** Where the white sheet is safe to write on, as a share of the picture's height (its curved edge peaks near 0.54). */
private const val FORM_TOP = 0.585f

/** How much of the picture's height the pitch text may use, so it stays on the dark part. */
private const val PITCH_BOTTOM = 0.50f

/** The widest the page is drawn; on a tablet it is a centred phone-shaped column. */
private val MAX_LOGIN_WIDTH = 480.dp

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
    var rememberMe by rememberSaveable { mutableStateOf(true) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White).testTag("auth_screen")) {
        // The picture sets the page's shape. On a wide or short window the column narrows so the whole picture still fits its height.
        val width = minOf(maxWidth, MAX_LOGIN_WIDTH, maxHeight / ART_ASPECT)
        val artHeight = width * ART_ASPECT
        Box(modifier = Modifier.width(width).fillMaxHeight().align(Alignment.TopCenter)) {
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
                    rememberMe = rememberMe,
                    onToggleRememberMe = { rememberMe = it },
                )
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
            modifier = Modifier.width(56.dp).aspectRatio(MARK_ASPECT),
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
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 26.sp),
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Text(
                stringResource(R.string.auth_login_hero_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Spacing.xs).widthIn(max = 200.dp),
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
    rememberMe: Boolean,
    onToggleRememberMe: (Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("login"),
    ) {
        Text(
            stringResource(R.string.auth_login_welcome),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AuthInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
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
            error = if (state.identifierMissing) stringResource(R.string.auth_error_login_identifier_required) else null,
            keyboardType = KeyboardType.Email,
            enabled = !state.isSigningIn,
            modifier = Modifier.testTag("login_identifier"),
        )
        AuthPillField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = stringResource(R.string.auth_field_password),
            icon = R.drawable.ic_lock,
            error = if (state.passwordMissing) stringResource(R.string.auth_error_login_password_required) else null,
            keyboardType = KeyboardType.Password,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { PasswordToggle(showPassword, onTogglePassword) },
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSignIn() }),
            enabled = !state.isSigningIn,
            modifier = Modifier.testTag("login_password"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = onToggleRememberMe,
                    colors = CheckboxDefaults.colors(checkedColor = AuthEmerald),
                    modifier = Modifier.testTag("login_remember_me"),
                )
                Text(stringResource(R.string.auth_login_remember_me), style = MaterialTheme.typography.bodyMedium, color = AuthInkMuted)
            }
            if (onForgotPassword != null) {
                TextButton(onClick = onForgotPassword, enabled = !state.isSigningIn, modifier = Modifier.testTag("login_forgot")) {
                    Text(stringResource(R.string.auth_login_forgot), color = AuthEmerald, fontWeight = FontWeight.Medium)
                }
            }
        }
        val message = state.message
        if (message != null) {
            Text(
                text = when (message) {
                    LoginMessage.Rejected -> stringResource(R.string.auth_login_rejected)
                    LoginMessage.Deactivated -> stringResource(R.string.auth_login_deactivated)
                    is LoginMessage.LockedOut -> pluralStringResource(R.plurals.auth_login_locked, message.seconds, message.seconds)
                    LoginMessage.NoAccount -> stringResource(R.string.auth_login_no_account)
                    LoginMessage.Unavailable -> stringResource(R.string.auth_login_unavailable)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("login_error"),
            )
        }
        Button(
            onClick = onSignIn,
            enabled = !state.isSigningIn,
            shape = RoundedCornerShape(32.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White.copy(alpha = 0.7f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Brush.horizontalGradient(listOf(AuthEmerald, AuthEmeraldBright)), RoundedCornerShape(32.dp))
                .testTag("login_submit"),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    stringResource(if (state.isSigningIn) R.string.auth_login_working else R.string.auth_login_action),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        // Always present, whether or not a business exists yet: it is how a new business is started.
        if (onSetUpBusiness != null) {
            OrDivider(stringResource(R.string.auth_login_setup_business))
            OutlinedButton(
                onClick = onSetUpBusiness,
                enabled = !state.isSigningIn,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("login_setup_business"),
            ) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(AuthMint), contentAlignment = Alignment.Center) {
                    AuxoIcon(R.drawable.ic_store, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.auth_login_setup_business_action), color = AuthEmerald, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                AuxoIcon(R.drawable.ic_arrow_forward, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
            }
        }
        Spacer(Modifier.height(bottomClearance))
    }
}
