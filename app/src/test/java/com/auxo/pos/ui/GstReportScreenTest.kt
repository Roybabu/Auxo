package com.auxo.pos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.auth.Role
import com.auxo.pos.domain.gst.GstReportBuilder
import com.auxo.pos.domain.gst.GstTestData
import com.auxo.pos.ui.screens.more.MoreActions
import com.auxo.pos.ui.screens.more.MoreScreen
import com.auxo.pos.ui.screens.reports.GstReportActions
import com.auxo.pos.ui.screens.reports.GstReportContent
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.viewmodel.GstPeriodMode
import com.auxo.pos.viewmodel.GstReportUiState
import com.auxo.pos.viewmodel.ShopUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h3200dp-xhdpi")
class GstReportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val october = YearMonth.of(2026, 10)

    private fun state(withReport: Boolean = true, mode: GstPeriodMode = GstPeriodMode.MONTH, canGoNext: Boolean = true): GstReportUiState {
        val base = GstReportUiState(mode = mode, month = october, from = october.atDay(1), to = october.atEndOfMonth(), isLoading = false, canGoNext = canGoNext)
        return if (withReport) {
            base.copy(report = GstReportBuilder.build(GstTestData.all, base.period, GstTestData.zone))
        } else {
            base.copy(report = GstReportBuilder.build(emptyList(), base.period, GstTestData.zone))
        }
    }

    private fun show(state: GstReportUiState, actions: GstReportActions = object : GstReportActions {}) {
        composeRule.setContent { AuxoTheme { GstReportContent(state, actions) } }
    }

    @Test
    fun showsTheNetFigures_withCreditNotesTakenOff() {
        show(state())
        composeRule.onNodeWithTag("gst_net_taxable").assertTextContains("3,200.00", substring = true)
        composeRule.onNodeWithTag("gst_net_cgst").assertTextContains("42.50", substring = true)
        composeRule.onNodeWithTag("gst_net_sgst").assertTextContains("42.50", substring = true)
        composeRule.onNodeWithTag("gst_net_igst").assertTextContains("250.00", substring = true)
        composeRule.onNodeWithTag("gst_net_tax").assertTextContains("335.00", substring = true)
        composeRule.onNodeWithTag("gst_net_value").assertTextContains("3,535.00", substring = true)
    }

    @Test
    fun anEmptyMonth_saysSo_andOffersNoExport() {
        show(state(withReport = false))
        composeRule.onNodeWithTag("gst_empty").assertIsDisplayed()
        composeRule.onAllNodesWithTag("gst_export_excel").assertCountEquals(0)
        composeRule.onAllNodesWithTag("gst_headline").assertCountEquals(0)
    }

    @Test
    fun theMonthIsShown_andNextIsOffOnTheCurrentMonth() {
        show(state(canGoNext = false))
        composeRule.onNodeWithTag("gst_month").assertTextContains("October 2026")
        composeRule.onNodeWithTag("gst_next").assertIsNotEnabled()
    }

    @Test
    fun theButtons_callTheActions() {
        val calls = mutableListOf<String>()
        show(
            state(),
            object : GstReportActions {
                override fun onPrevious() { calls += "previous" }
                override fun onNext() { calls += "next" }
                override fun onModeChange(mode: GstPeriodMode) { calls += "mode:$mode" }
                override fun onExportExcel() { calls += "excel" }
                override fun onExportCsv() { calls += "csv" }
            },
        )
        composeRule.onNodeWithTag("gst_previous").performClick()
        composeRule.onNodeWithTag("gst_next").performClick()
        composeRule.onNodeWithTag("gst_mode_range").performClick()
        composeRule.onNodeWithTag("gst_export_excel").performClick()
        composeRule.onNodeWithTag("gst_export_csv").performClick()
        assertEquals(listOf("previous", "next", "mode:RANGE", "excel", "csv"), calls)
    }

    @Test
    fun rangeMode_showsTheChosenDays_andTheFinancialYearShortcut() {
        show(state(mode = GstPeriodMode.RANGE))
        composeRule.onNodeWithTag("gst_financial_year").assertIsDisplayed()
        composeRule.onAllNodesWithTag("gst_month").assertCountEquals(0)
        composeRule.onNodeWithTag("gst_period").assertIsDisplayed()
    }

    @Test
    fun theMorePage_offersTheGstReport_onlyToTheSuperAdmin() {
        fun more(role: Role) = composeRule.setContent {
            AuxoTheme {
                CompositionLocalProvider(LocalCapabilities provides Capabilities(role, "Asha K")) {
                    MoreScreen(actions = object : MoreActions {}, shop = ShopUiState())
                }
            }
        }
        more(Role.SUPER_ADMIN)
        composeRule.onNodeWithTag("more_gst_report").assertIsDisplayed()
    }

    @Test
    fun theMorePage_hidesTheGstReport_fromEmployees() {
        composeRule.setContent {
            AuxoTheme {
                CompositionLocalProvider(LocalCapabilities provides Capabilities(Role.EMPLOYEE, "Asha K")) {
                    MoreScreen(actions = object : MoreActions {}, shop = ShopUiState())
                }
            }
        }
        composeRule.onAllNodesWithTag("more_gst_report").assertCountEquals(0)
    }
}
