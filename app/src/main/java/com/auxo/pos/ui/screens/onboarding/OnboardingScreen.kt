package com.auxo.pos.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.components.AuthGradientBackground
import com.auxo.pos.ui.components.CircleNextButton
import com.auxo.pos.ui.components.Illustration
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

/** The picture that sits behind the text on the splash, placed by fractions of the screen so it fits any size. */
@Composable
private fun SplashPage() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("onboarding_splash")) {
        val w = maxWidth
        val h = maxHeight
        // White page with soft mint blobs in the corners.
        Image(
            painter = painterResource(R.drawable.onb_splash_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // The pictures are smaller than before, each tilted a little and carrying a soft shadow that is part of the file.
        Illustration(R.drawable.onb_s_bag, Modifier.align(Alignment.TopStart).offset(x = w * -0.02f, y = h * 0.10f).width(w * 0.32f).rotate(-8f))
        Illustration(R.drawable.onb_s_apparel, Modifier.align(Alignment.TopEnd).offset(x = w * 0.06f, y = h * 0.09f).width(w * 0.41f).rotate(6f))
        Illustration(R.drawable.onb_s_burger, Modifier.align(Alignment.CenterStart).offset(x = w * -0.08f, y = h * -0.07f).width(w * 0.33f).rotate(5f))
        Illustration(R.drawable.onb_s_coffee, Modifier.align(Alignment.CenterEnd).offset(x = w * 0.07f, y = h * -0.05f).width(w * 0.30f).rotate(-6f))
        Illustration(R.drawable.onb_s_sneakers, Modifier.align(Alignment.BottomStart).offset(x = w * -0.04f, y = h * -0.17f).width(w * 0.41f).rotate(-5f))
        Illustration(R.drawable.onb_s_grocery_bag, Modifier.align(Alignment.BottomEnd).offset(x = w * 0.06f, y = h * -0.11f).width(w * 0.41f).rotate(4f))

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1.05f))
            Image(
                painter = painterResource(R.drawable.auxo_logo_vertical),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(w * 0.40f).aspectRatio(LOGO_ASPECT),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.onboarding_splash_title),
                style = TitleStyle.copy(fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.testTag("onboarding_splash_title"),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SplashWord(R.string.onboarding_splash_inventory)
                Text("•", style = TextStyle(fontSize = 14.sp, color = AuthEmerald))
                SplashWord(R.string.onboarding_splash_billing)
                Text("•", style = TextStyle(fontSize = 14.sp, color = AuthEmerald))
                SplashWord(R.string.onboarding_splash_growth)
            }
            Spacer(Modifier.weight(1.35f))
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** Width over height of auxo_logo_vertical.png. */
private const val LOGO_ASPECT = 345f / 364f

@Composable
private fun SplashWord(@StringRes id: Int) {
    Text(stringResource(id), style = TextStyle(fontSize = 15.sp, color = AuthEmerald, fontWeight = FontWeight.Medium))
}
