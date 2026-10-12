package com.auxo.pos.viewmodel

import com.auxo.pos.domain.loyalty.LoyaltyRuleError
import com.auxo.pos.domain.loyalty.LoyaltyRules
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.FakeLoyaltyRepository
import com.auxo.pos.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoyaltySettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLoyaltyRepository()

    @Test
    fun aNewShop_opensWithLoyaltyOff_andSensibleNumbers() {
        val state = LoyaltySettingsViewModel(repository).uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.enabled)
        assertEquals("100.00", state.spendText)
        assertEquals("1.00", state.valueText)
        assertFalse(state.dirty)
        assertTrue(state.errors.isEmpty())
    }

    @Test
    fun savedRules_areShownWhenReopened() {
        repository.rules.value = LoyaltyRules(true, Money(5_050), Money(25))
        val state = LoyaltySettingsViewModel(repository).uiState.value
        assertTrue(state.enabled)
        assertEquals("50.50", state.spendText)
        assertEquals("0.25", state.valueText)
    }

    @Test
    fun switchingOn_andSaving_savesTheRules() = runTest {
        val vm = LoyaltySettingsViewModel(repository)
        vm.onEnabledChange(true)
        vm.onSpendChange("200")
        vm.onValueChange("2")
        assertTrue(vm.uiState.value.dirty)
        vm.save()
        assertEquals(LoyaltyRules(true, Money(20_000), Money(200)), repository.rules.value)
        val state = vm.uiState.value
        assertTrue(state.saved)
        assertFalse(state.dirty)
        assertFalse(state.isSaving)
    }

    @Test
    fun nothingIsSaved_untilSaveIsPressed() {
        val vm = LoyaltySettingsViewModel(repository)
        vm.onEnabledChange(true)
        assertEquals(LoyaltyRules.OFF, repository.rules.value)
        assertEquals(0, repository.saves)
    }

    @Test
    fun badNumbers_areNotSaved_andShowTheirReasons() {
        val vm = LoyaltySettingsViewModel(repository)
        vm.onEnabledChange(true)
        vm.onSpendChange("")
        vm.onValueChange("abc") // letters are dropped, leaving nothing
        vm.save()
        val state = vm.uiState.value
        assertEquals(0, repository.saves)
        assertTrue(state.showErrors)
        assertEquals(setOf(LoyaltyRuleError.SPEND_REQUIRED, LoyaltyRuleError.VALUE_REQUIRED), state.errors)
        assertNull(state.preview)
    }

    @Test
    fun aPointWorthMoreThanItsSpend_isRefused() {
        val vm = LoyaltySettingsViewModel(repository)
        vm.onSpendChange("10")
        vm.onValueChange("11")
        vm.save()
        assertEquals(setOf(LoyaltyRuleError.VALUE_ABOVE_SPEND), vm.uiState.value.errors)
        assertEquals(0, repository.saves)
    }

    @Test
    fun theTypedNumbers_areFilteredToDigitsAndOneDecimalPoint() {
        val vm = LoyaltySettingsViewModel(repository)
        vm.onSpendChange("1,2a3.50")
        assertEquals("123.50", vm.uiState.value.spendText)
    }

    @Test
    fun anExampleIsAvailable_whileTheNumbersAreValid() {
        val vm = LoyaltySettingsViewModel(repository)
        assertNotNull(vm.uiState.value.preview)
        vm.onSpendChange("")
        assertNull(vm.uiState.value.preview)
    }

    @Test
    fun aFailedSave_isReported_andCanBeRetried() = runTest {
        val failing = object : com.auxo.pos.data.repository.LoyaltyRepository by repository {
            var fail = true
            override suspend fun saveRules(rules: LoyaltyRules) {
                if (fail) throw IOException("disk full")
                repository.saveRules(rules)
            }
        }
        val vm = LoyaltySettingsViewModel(failing)
        vm.onEnabledChange(true)
        vm.save()
        assertTrue(vm.uiState.value.saveFailed)
        assertTrue(vm.uiState.value.dirty)
        assertFalse(repository.rules.value.enabled)
        failing.fail = false
        vm.save()
        assertFalse(vm.uiState.value.saveFailed)
        assertTrue(repository.rules.value.enabled)
    }

    @Test
    fun aFailedLoad_canBeRetried() {
        val flaky = object : com.auxo.pos.data.repository.LoyaltyRepository by repository {
            var fail = true
            override suspend fun getRules(): LoyaltyRules {
                if (fail) throw IOException("unreadable")
                return repository.getRules()
            }
        }
        val vm = LoyaltySettingsViewModel(flaky)
        assertTrue(vm.uiState.value.loadFailed)
        flaky.fail = false
        vm.retry()
        assertFalse(vm.uiState.value.loadFailed)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun changingAfterASave_clearsTheSavedMessage() = runTest {
        val vm = LoyaltySettingsViewModel(repository)
        vm.onEnabledChange(true)
        vm.save()
        assertTrue(vm.uiState.value.saved)
        vm.onSpendChange("150")
        assertFalse(vm.uiState.value.saved)
        assertTrue(vm.uiState.value.dirty)
    }
}
