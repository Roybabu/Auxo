package com.auxo.pos.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {

    @Test
    fun startsOnTheSplash_andFollowsTheVisiblePage() {
        val viewModel = OnboardingViewModel(FakeShopPreferences())
        assertEquals(0, viewModel.uiState.value.page)
        assertFalse(viewModel.uiState.value.isLastPage)

        viewModel.onPageChanged(1)
        assertEquals(1, viewModel.uiState.value.page)
        viewModel.onPageChanged(2)
        assertTrue(viewModel.uiState.value.isLastPage)
    }

    @Test
    fun aPageOutsideTheIntroduction_isHeldToTheNearestOne() {
        val viewModel = OnboardingViewModel(FakeShopPreferences())
        viewModel.onPageChanged(9)
        assertEquals(OnboardingUiState.PAGE_COUNT - 1, viewModel.uiState.value.page)
        viewModel.onPageChanged(-3)
        assertEquals(0, viewModel.uiState.value.page)
    }

    @Test
    fun finishing_remembersThatTheIntroductionWasSeen() {
        val prefs = FakeShopPreferences()
        val viewModel = OnboardingViewModel(prefs)
        assertFalse(prefs.onboardingSeen.value)
        viewModel.finish()
        assertTrue(prefs.onboardingSeen.value)
    }
}
