// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.components.AuthGradientBackground
import com.auxo.pos.ui.components.CircleNextButton
import com.auxo.pos.ui.components.LanguageChip
import com.auxo.pos.ui.components.PageDots
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.AuthInkMuted
import com.auxo.pos.ui.theme.AuthMintLight
import com.auxo.pos.viewmodel.OnboardingUiState
import com.auxo.pos.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

/** What the app opens to on the very first launch: the splash and two introduction pages, then first-time setup. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingContent(state = state, onPageChanged = viewModel::onPageChanged, onFinish = viewModel::finish, languageChip = { LanguageChip() })
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onPageChanged: (Int) -> Unit,
    onFinish: () -> Unit,
    /** The language button, pinned to the top right on every page. */
    languageChip: @Composable () -> Unit = {},
) {
    val pager = rememberPagerState(initialPage = state.page) { OnboardingUiState.PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val latestPageChanged by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { latestPageChanged(it) } }
    val page = pager.currentPage
    val last = page == OnboardingUiState.PAGE_COUNT - 1

    AuthGradientBackground(modifier = Modifier.testTag("onboarding"), colors = listOf(Color.White, Color.White, AuthMintLight)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            when (index) {
                0 -> SplashPage()
                1 -> IntroPage()
                else -> CategoriesPage()
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            languageChip()
            if (page > 0) {
                TextButton(onClick = onFinish, modifier = Modifier.testTag("onboarding_skip")) {
                    Text(stringResource(R.string.onboarding_skip), style = TextStyle(fontSize = 15.sp, color = AuthInkMuted))
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            // The same indicator on every page, in the middle: a green line that moves along, with a grey dot for each other page.
            PageDots(
                count = OnboardingUiState.PAGE_COUNT,
                current = page,
                modifier = Modifier.align(Alignment.Center).testTag("onboarding_progress"),
            )
            CircleNextButton(
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(page + 1) } },
                contentDescription = stringResource(R.string.onboarding_next),
                modifier = Modifier.align(Alignment.CenterEnd).testTag("onboarding_next"),
            )
        }
    }
}

private val TitleStyle = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = AuthInk, textAlign = TextAlign.Center)
private val BodyStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = AuthInkMuted, textAlign = TextAlign.Center)

/** Height over width of the splash design (840 x 1871, the shape of a tall phone). */
private const val SCENE_ASPECT = 1871f / 840f

/**
 * The splash, built from separate pictures so it matches the design: the wave background of the second page, the
 * shop's goods round the edges (some running off the screen), leaves between them, and the Auxo logo, the line
 * "Your Business Made Simple" and the three words in the empty middle. Everything is placed in an 840 x 1871 frame
 * that is as wide as the screen and centred upright, so the pieces keep their places relative to each other.
 */
@Composable
private fun SplashPage() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("onboarding_splash")) {
        val w = maxWidth
        val unit = w / 840f
        val top = (maxHeight - unit * 1871f) / 2
        Image(
            painter = painterResource(R.drawable.onb_intro_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Leaves behind the goods.
        ScenePiece(R.drawable.onb_leafset_09, -12, -12, 150, top, unit, rotation = 20f, alpha = 0.95f, blur = 3.dp)
        ScenePiece(R.drawable.onb_leafset_06, 10, 165, 65, top, unit, rotation = -15f)
        ScenePiece(R.drawable.onb_leafpic_01, 288, 338, 80, top, unit, rotation = -20f)
        ScenePiece(R.drawable.onb_leafset_19, 0, 462, 80, top, unit, rotation = 15f)
        ScenePiece(R.drawable.onb_leafset_20, 264, 485, 70, top, unit, rotation = 20f)
        ScenePiece(R.drawable.onb_leafset_20, 520, 195, 72, top, unit, rotation = 15f)
        ScenePiece(R.drawable.onb_leafset_11, 762, 155, 80, top, unit, rotation = -20f)
        ScenePiece(R.drawable.onb_leafset_08, 556, 566, 68, top, unit, rotation = -10f, alpha = 0.9f, blur = 1.dp)
        ScenePiece(R.drawable.onb_leafset_18, 82, 866, 76, top, unit, rotation = 10f, alpha = 0.9f, blur = 1.dp)
        ScenePiece(R.drawable.onb_leafset_11, 700, 910, 82, top, unit, rotation = -15f, alpha = 0.9f, blur = 1.dp)
        ScenePiece(R.drawable.onb_leafset_08, 715, 1145, 100, top, unit, rotation = -10f, alpha = 0.9f, blur = 1.dp)
        ScenePiece(R.drawable.onb_leafset_13, 17, 1432, 100, top, unit, rotation = 20f)
        ScenePiece(R.drawable.onb_leafpic_01, 235, 1378, 117, top, unit, rotation = -20f)
        ScenePiece(R.drawable.onb_leafset_13, 437, 1575, 103, top, unit, rotation = 15f)

        // The goods, each placed and sized as in the design.
        ScenePiece(R.drawable.onb_sp_bag, 8, 192, 248, top, unit)
        ScenePiece(R.drawable.onb_sp_burger, 14, 585, 232, top, unit)
        ScenePiece(R.drawable.onb_sp_shoe_red, 12, 1200, 250, top, unit)
        ScenePiece(R.drawable.onb_sp_shoe_blue, 12, 1545, 250, top, unit)
        ScenePiece(R.drawable.onb_sp_shirt, 515, 205, 305, top, unit)
        ScenePiece(R.drawable.onb_sp_coffee, 596, 646, 244, top, unit)
        ScenePiece(R.drawable.onb_sp_grocery, 462, 1296, 392, top, unit, rotation = -14f)
        // The big soft leaves in the bottom corners, out of focus.
        ScenePiece(R.drawable.onb_leafset_09, -15, 1665, 175, top, unit, rotation = 35f, alpha = 0.95f, blur = 6.dp)
        ScenePiece(R.drawable.onb_leafset_09, 625, 1580, 235, top, unit, rotation = -30f, alpha = 0.95f, blur = 8.dp)

        Column(
            modifier = Modifier.fillMaxSize().padding(top = top + unit * 628f).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.auxo_logo_vertical),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(unit * 370f).aspectRatio(LOGO_ASPECT),
            )
            Spacer(Modifier.height(unit * 14f))
            Text(
                stringResource(R.string.onboarding_splash_title),
                style = TitleStyle.copy(fontSize = unit.toSp(54f), lineHeight = unit.toSp(58f), fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.testTag("onboarding_splash_title"),
            )
            Spacer(Modifier.height(unit * 14f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 24f)) {
                SplashWord(R.string.onboarding_splash_inventory, unit)
                Text("•", style = TextStyle(fontSize = unit.toSp(28f), color = AuthEmerald))
                SplashWord(R.string.onboarding_splash_billing, unit)
                Text("•", style = TextStyle(fontSize = unit.toSp(28f), color = AuthEmerald))
                SplashWord(R.string.onboarding_splash_growth, unit)
            }
        }
    }
}

/** One picture of the splash, placed by its left, top and width in the 1024-wide design (and its top-left shifted by [top]). */
@Composable
private fun ScenePiece(
    @DrawableRes id: Int,
    x: Int,
    y: Int,
    width: Int,
    top: Dp,
    unit: Dp,
    rotation: Float = 0f,
    alpha: Float = 1f,
    blur: Dp = 0.dp,
    height: Int? = null,
) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        contentScale = if (height != null) ContentScale.FillBounds else ContentScale.FillWidth,
        modifier = Modifier
            .offset(x = unit * x.toFloat(), y = top + unit * y.toFloat())
            .requiredWidth(unit * width.toFloat())
            .then(if (height != null) Modifier.requiredHeight(unit * height.toFloat()) else Modifier)
            .rotate(rotation)
            .alpha(alpha)
            .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
    )
}

/** Width over height of auxo_logo_vertical.png. */
private const val LOGO_ASPECT = 345f / 364f

@Composable
private fun SplashWord(@StringRes id: Int, unit: Dp) {
    Text(stringResource(id), style = TextStyle(fontSize = unit.toSp(30f), color = AuthEmerald, fontWeight = FontWeight.Medium))
}

/** [units] design units of the splash as a text size: it grows and shrinks with the picture, not with the font setting. */
@Composable
private fun Dp.toSp(units: Float): TextUnit = with(LocalDensity.current) { (this@toSp * units).toSp() }
