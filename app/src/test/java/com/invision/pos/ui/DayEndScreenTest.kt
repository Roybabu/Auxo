package com.invision.pos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.Role
import com.invision.pos.domain.dayend.DayClosing
import com.invision.pos.domain.model.Money
import com.invision.pos.testutil.FakeDayEndRepository
import com.invision.pos.ui.screens.more.DayClosingContent
import com.invision.pos.ui.screens.more.DayEndContent
import com.invision.pos.ui.screens.more.MoreActions
import com.invision.pos.ui.screens.more.MoreScreen
import com.invision.pos.ui.screens.more.daySummary
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.DayClosingUiState
import com.invision.pos.viewmodel.DayEndViewModel
import com.invision.pos.viewmodel.ShopUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class DayEndScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 12)

    private fun show(repository: FakeDayEndRepository): DayEndViewModel {
        val vm = DayEndViewModel(repository)
        composeRule.setContent {
            InvisionTheme {
                val state by vm.uiState.collectAsState()
                DayEndContent(
                    state = state,
                    onCountedChange = vm::onCountedChange,
                    onNoteChange = vm::onNoteChange,
                    onRefresh = vm::refresh,
                    onClose = vm::requestClose,
                    onConfirm = vm::confirmClose,
                    onCancel = vm::cancelClose,
                    onRetry = vm::load,
                    onOpenClosing = {},
                )
            }
        }
        return vm
    }

    @Test
    fun showsTheDaysFigures_andTheCashExpected() {
        show(FakeDayEndRepository(today))
        composeRule.onNodeWithTag("day_end_figures").assertIsDisplayed()
        composeRule.onNodeWithTag("day_end_expected").assertTextContains("1,250.00", substring = true)
    }

    @Test
    fun typingTheCount_showsShortOrOver() {
        show(FakeDayEndRepository(today))
        composeRule.onNodeWithTag("day_end_counted").performTextInput("1200")
        composeRule.onNodeWithTag("day_end_difference").assertTextContains("Short by", substring = true)
        composeRule.onNodeWithTag("day_end_difference").assertTextContains("50.00", substring = true)
    }

    @Test
    fun anExactCount_saysTheCashMatches() {
        show(FakeDayEndRepository(today))
        composeRule.onNodeWithTag("day_end_counted").performTextInput("1250")
        composeRule.onNodeWithTag("day_end_difference").assertTextContains("Cash matches")
    }

    @Test
    fun closingWithoutACount_showsAnErrorAndNoDialog() {
        show(FakeDayEndRepository(today))
        composeRule.onNodeWithTag("day_end_close").performClick()
        composeRule.onNodeWithText("Enter the cash you counted").assertIsDisplayed()
        composeRule.onAllNodesWithTag("day_end_closed").assertCountEquals(0)
    }

    @Test
    fun closing_needsConfirmation_thenShowsTheDayAsClosed() {
        val repository = FakeDayEndRepository(today)
        show(repository)
        composeRule.onNodeWithTag("day_end_counted").performTextInput("1200")
        composeRule.onNodeWithTag("day_end_close").performClick()
        composeRule.onNodeWithText("cannot be changed or reopened", substring = true).assertIsDisplayed()
        assertEquals(0, repository.closeCalls)

        composeRule.onNodeWithText("Close day").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("day_end_closed").assertIsDisplayed()
        composeRule.onNodeWithTag("day_end_open_summary").assertIsDisplayed()
        composeRule.onAllNodesWithTag("day_end_close").assertCountEquals(0) // nothing left to edit
        assertEquals(1, repository.closeCalls)
    }

    @Test
    fun aClosedDay_withLaterBills_saysSo() {
        val closed = DayClosing(1, FakeDayEndRepository.sampleFigures(today), Money(1_250_00), null, 5L, "Test Admin", billsAfterClosing = 2)
        show(FakeDayEndRepository(today, initial = listOf(closed)))
        composeRule.onNodeWithTag("day_end_after_closing").assertIsDisplayed()
        composeRule.onAllNodesWithTag("day_end_counted").assertCountEquals(0)
    }

    @Test
    fun earlierClosings_areListed() {
        val older = DayClosing(3, FakeDayEndRepository.sampleFigures(today.minusDays(1)), Money(1_240_00), null, 5L, "Test Admin")
        show(FakeDayEndRepository(today, initial = listOf(older)))
        composeRule.onNodeWithTag("closing_3").assertIsDisplayed()
        composeRule.onAllNodesWithTag("day_end_history_empty").assertCountEquals(0)
    }

    @Test
    fun withNoEarlierClosings_saysSo() {
        show(FakeDayEndRepository(today))
        composeRule.onNodeWithTag("day_end_history_empty").assertIsDisplayed()
    }

    @Test
    fun theClosedSummary_showsTheFiguresAndOffersPrintAndShare() {
        val closing = DayClosing(1, FakeDayEndRepository.sampleFigures(today), Money(1_240_00), "Note here", 5L, "Test Admin")
        composeRule.setContent {
            InvisionTheme {
                val summary = daySummary(closing, LocalMoneyFormatter.current)
                DayClosingContent(DayClosingUiState(isLoading = false, closing = closing), summary, {}, {}, {})
            }
        }
        composeRule.onNodeWithTag("day_closing").assertIsDisplayed()
        composeRule.onNodeWithText("Short by", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Note here").assertIsDisplayed()
        composeRule.onNodeWithTag("day_closing_print").assertIsDisplayed()
        composeRule.onNodeWithTag("day_closing_share").assertIsDisplayed()
    }

    // The More page

    private fun more(role: Role, extras: Set<Permission> = emptySet()) {
        composeRule.setContent {
            InvisionTheme {
                CompositionLocalProvider(
                    LocalCapabilities provides Capabilities(role, "Asha K", com.invision.pos.core.auth.RolePermissions.of(role, extras)),
                ) { MoreScreen(actions = object : MoreActions {}, shop = ShopUiState()) }
            }
        }
    }

    @Test
    fun more_offersDayEnd_toTheSuperAdmin() {
        more(Role.SUPER_ADMIN)
        composeRule.onNodeWithTag("more_day_end").assertIsDisplayed()
    }

    @Test
    fun more_offersDayEnd_toAnEmployeeOnlyWhenGiven() {
        more(Role.EMPLOYEE)
        composeRule.onAllNodesWithTag("more_day_end").assertCountEquals(0)
    }

    @Test
    fun more_offersDayEnd_toAnEmployeeWhoWasGivenIt() {
        more(Role.EMPLOYEE, setOf(Permission.CLOSE_DAY))
        composeRule.onNodeWithTag("more_day_end").assertIsDisplayed()
    }
}
