package com.auxo.pos.ui.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import com.auxo.pos.ui.components.AuthProgressBar
import com.auxo.pos.ui.components.CircleNextButton
import com.auxo.pos.ui.components.Illustration
import com.auxo.pos.ui.components.PageDots
import com.auxo.pos.ui.theme.AuthCard
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.AuthInkMuted
import com.auxo.pos.viewmodel.OnboardingUiState
import com.auxo.pos.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

/** What the app opens to on the very first launch: the splash and two introduction pages, then first-time setup. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingContent(state = state, onPageChanged = viewModel::onPageChanged, onFinish = viewModel::finish)
}

@Composable
fun OnboardingContent(state: OnboardingUiState, onPageChanged: (Int) -> Unit, onFinish: () -> Unit) {
    val pager = rememberPagerState(initialPage = state.page) { OnboardingUiState.PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val latestPageChanged by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { latestPageChanged(it) } }
    val page = pager.currentPage
    val last = page == OnboardingUiState.PAGE_COUNT - 1

    AuthGradientBackground(modifier = Modifier.testTag("onboarding")) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            when (index) {
                0 -> SplashPage()
                1 -> IntroPage()
                else -> CategoriesPage()
            }
        }
        if (page > 0) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).testTag("onboarding_skip"),
            ) {
                Text(stringResource(R.string.onboarding_skip), style = TextStyle(fontSize = 15.sp, color = AuthInkMuted))
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // The splash shows a line, the pages after it show dots; the third dot stands for first-time setup.
            if (page == 0) {
                AuthProgressBar(fraction = 1f / OnboardingUiState.PAGE_COUNT, modifier = Modifier.width(120.dp).testTag("onboarding_progress"))
            } else {
                PageDots(count = OnboardingUiState.PAGE_COUNT, current = page - 1, modifier = Modifier.testTag("onboarding_dots"))
            }
            CircleNextButton(
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(page + 1) } },
                contentDescription = stringResource(R.string.onboarding_next),
                modifier = Modifier.testTag("onboarding_next"),
            )
        }
    }
}

private val TitleStyle = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = AuthInk, textAlign = TextAlign.Center)
private val BodyStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = AuthInkMuted, textAlign = TextAlign.Center)

/** The picture that sits behind the text on the splash, placed by fractions of the screen so it fits any size. */
@Composable
private fun SplashPage() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("onboarding_splash")) {
        val w = maxWidth
        val h = maxHeight
        Illustration(R.drawable.onb_bag, Modifier.align(Alignment.TopStart).offset(x = w * -0.04f, y = h * 0.04f).width(w * 0.40f))
        Illustration(R.drawable.onb_apparel, Modifier.align(Alignment.TopEnd).offset(x = w * 0.10f, y = h * 0.02f).width(w * 0.52f))
        Illustration(R.drawable.onb_leaves, Modifier.align(Alignment.TopCenter).offset(y = h * 0.02f).width(w * 0.34f))
        Illustration(R.drawable.onb_burger, Modifier.align(Alignment.CenterStart).offset(x = w * -0.16f, y = h * -0.10f).width(w * 0.42f))
        Illustration(R.drawable.onb_coffee, Modifier.align(Alignment.CenterEnd).offset(x = w * 0.14f, y = h * -0.07f).width(w * 0.38f))
        Illustration(R.drawable.onb_sneakers, Modifier.align(Alignment.BottomStart).offset(x = w * -0.10f, y = h * -0.14f).width(w * 0.52f))
        Illustration(R.drawable.onb_grocery_bag, Modifier.align(Alignment.BottomEnd).offset(x = w * 0.12f, y = h * -0.09f).width(w * 0.52f))

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.9f))
            Image(
                painter = painterResource(R.drawable.auxo_logo_vertical),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(w * 0.5f).aspectRatio(LOGO_ASPECT),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.onboarding_splash_title),
                style = TitleStyle.copy(fontSize = 26.sp, lineHeight = 31.sp),
                modifier = Modifier.widthIn(max = w * 0.62f).testTag("onboarding_splash_title"),
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SplashWord(R.string.onboarding_splash_inventory)
                Text("•", style = TextStyle(fontSize = 14.sp, color = AuthEmerald))
                SplashWord(R.string.onboarding_splash_billing)
                Text("•", style = TextStyle(fontSize = 14.sp, color = AuthEmerald))
                SplashWord(R.string.onboarding_splash_growth)
            }
            Spacer(Modifier.weight(1.4f))
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

@Composable
private fun IntroPage() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("onboarding_intro").statusBarsPadding().padding(start = 36.dp, end = 36.dp, top = 84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.onboarding_intro_title), style = TitleStyle, modifier = Modifier.widthIn(max = 300.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_intro_body), style = BodyStyle, modifier = Modifier.widthIn(max = 320.dp))
        Spacer(Modifier.weight(1f))
        Illustration(R.drawable.onb_pos, Modifier.fillMaxWidth())
        Spacer(Modifier.weight(0.6f))
        Spacer(Modifier.height(96.dp))
    }
}

private class CategoryCard(@DrawableRes val picture: Int, @StringRes val label: Int)

/** Examples only: the shop's real categories are chosen after setup. */
private val CATEGORY_CARDS = listOf(
    CategoryCard(R.drawable.onb_apparel, R.string.onboarding_cat_apparel),
    CategoryCard(R.drawable.onb_burger, R.string.onboarding_cat_food),
    CategoryCard(R.drawable.onb_cat_grocery, R.string.onboarding_cat_grocery),
    CategoryCard(R.drawable.onb_cat_electronics, R.string.onboarding_cat_electronics),
    CategoryCard(R.drawable.onb_cat_beauty, R.string.onboarding_cat_beauty),
    CategoryCard(R.drawable.onb_cat_general, R.string.onboarding_cat_general),
)

@Composable
private fun CategoriesPage() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("onboarding_categories").statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.onboarding_cats_title), style = TitleStyle, modifier = Modifier.widthIn(max = 320.dp))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.onboarding_cats_body), style = BodyStyle, modifier = Modifier.widthIn(max = 320.dp))
        Spacer(Modifier.height(20.dp))
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CATEGORY_CARDS.chunked(2).forEachIndexed { row, cards ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    cards.forEachIndexed { column, card -> CategoryTile(card, Modifier.weight(1f).testTag("onboarding_category_${row * 2 + column}")) }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(card: CategoryCard, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxHeight(), shape = RoundedCornerShape(18.dp), color = AuthCard, shadowElevation = 3.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Illustration(card.picture, Modifier.weight(1f).fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp))
            Text(
                stringResource(card.label),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AuthInk, textAlign = TextAlign.Center),
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}
