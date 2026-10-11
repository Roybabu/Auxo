package com.invision.pos.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.CartValidator
import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.PaymentDraft
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.customers.Ledger
import com.invision.pos.domain.inventory.PickableItem
import com.invision.pos.domain.inventory.ReorderPlanner
import com.invision.pos.domain.menu.MenuItemForm
import com.invision.pos.domain.model.BatchWithProduct
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.CustomerAccount
import com.invision.pos.domain.model.LedgerEntry
import com.invision.pos.domain.model.LedgerEntryType
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.PurchaseOrder
import com.invision.pos.domain.model.PurchaseOrderLine
import com.invision.pos.domain.model.PurchaseOrderStatus
import com.invision.pos.domain.model.RecipeLine
import com.invision.pos.domain.model.StockBatch
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.model.StockMovementRecord
import com.invision.pos.domain.model.StockUnit
import com.invision.pos.domain.model.Supplier
import com.invision.pos.domain.purchasing.PurchaseLineError
import com.invision.pos.domain.purchasing.PurchaseLineForm
import com.invision.pos.domain.reports.ReportColumn
import com.invision.pos.domain.reports.ReportTable
import com.invision.pos.domain.reports.ReportValue
import com.invision.pos.domain.reports.Reports
import com.invision.pos.testutil.TestData
import com.invision.pos.ui.screens.billing.BillingActions
import com.invision.pos.ui.screens.billing.CartContent
import com.invision.pos.ui.screens.billing.PaymentContent
import com.invision.pos.ui.screens.customers.AccountPaymentContent
import com.invision.pos.ui.screens.customers.CustomerDetailContent
import com.invision.pos.ui.screens.customers.CustomerListContent
import com.invision.pos.ui.screens.menu.MenuEditorActions
import com.invision.pos.ui.screens.menu.MenuEditorContent
import com.invision.pos.ui.screens.menu.MenuListContent
import com.invision.pos.ui.screens.more.MoreActions
import com.invision.pos.ui.screens.more.MoreScreen
import com.invision.pos.viewmodel.ShopUiState
import com.invision.pos.ui.screens.purchasing.OrderEditorActions
import com.invision.pos.ui.screens.purchasing.PurchaseOrderDetailContent
import com.invision.pos.ui.screens.purchasing.PurchaseOrderEditorContent
import com.invision.pos.ui.screens.purchasing.ReceiveActions
import com.invision.pos.ui.screens.purchasing.ReceiveGoodsContent
import com.invision.pos.ui.screens.purchasing.SupplierListContent
import com.invision.pos.ui.screens.reports.ReportsActions
import com.invision.pos.ui.screens.reports.ReportsContent
import com.invision.pos.ui.screens.stock.StockAlertsActions
import com.invision.pos.ui.screens.stock.StockAlertsContent
import com.invision.pos.ui.screens.stock.StockMovementsContent
import com.invision.pos.ui.screens.stock.WriteOffForm
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.viewmodel.AccountPaymentUiState
import com.invision.pos.viewmodel.AlertsTab
import com.invision.pos.viewmodel.BillingStage
import com.invision.pos.viewmodel.BillingUiState
import com.invision.pos.viewmodel.CustomerDetailUiState
import com.invision.pos.viewmodel.CustomerListUiState
import com.invision.pos.viewmodel.ItemPickerState
import com.invision.pos.viewmodel.MenuDish
import com.invision.pos.viewmodel.MenuEditorUiState
import com.invision.pos.viewmodel.MenuListUiState
import com.invision.pos.viewmodel.MenuSection
import com.invision.pos.viewmodel.PurchaseOrderDetailUiState
import com.invision.pos.viewmodel.PurchaseOrderEditorUiState
import com.invision.pos.viewmodel.ReceiveGoodsUiState
import com.invision.pos.viewmodel.ReportFilters
import com.invision.pos.viewmodel.ReportsUiState
import com.invision.pos.viewmodel.StockAlertsUiState
import com.invision.pos.viewmodel.StockMovementsUiState
import com.invision.pos.viewmodel.SupplierListUiState
import com.invision.pos.viewmodel.WastageDialogState
import com.invision.pos.domain.reports.ReportPeriod
import com.invision.pos.domain.reports.PeriodPreset
import com.invision.pos.domain.reports.ReportType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Step 4 screens rendered with sample data, with screenshots in app/build/outputs/screenshots/. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h850dp-xhdpi")
class OperationsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = TestData.today
    private val formatter = MoneyFormatter()

    private fun rupees(minorUnits: Long): String = formatter.format(Money(minorUnits))

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { InvisionTheme { content() } }
        composeRule.waitForIdle()
    }

    private fun screenshot(name: String) {
        val bitmap = composeRule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val anita = Customer(id = 1, name = "Anita Das", phone = "9830012345", creditLimit = Money(500_000))
    private val ravi = Customer(id = 2, name = "Ravi Roy", phone = "9830054321")
    private val entries = listOf(
        LedgerEntry(id = 1, customerId = 1, type = LedgerEntryType.SALE, amount = Money(80_000), invoiceId = 3, invoiceNumber = "INV/26-27/00003", createdAtMillis = TestData.clock.millis()),
        LedgerEntry(id = 2, customerId = 1, type = LedgerEntryType.PAYMENT, amount = Money(-30_000), method = PaymentMethod.UPI, reference = "UTR88", createdAtMillis = TestData.clock.millis()),
    )

    @Test
    fun customerList_showsBalances_andOpensACustomer() {
        val opened = mutableListOf<Long>()
        val accounts = listOf(CustomerAccount(anita, Money(50_000), TestData.clock.millis()), CustomerAccount(ravi, Money.ZERO))
        show {
            CustomerListContent(
                state = CustomerListUiState(isLoading = false, accounts = accounts, customerCount = 2, totalDue = Money(50_000), owingCount = 1),
                onQueryChange = {}, onFilterChange = {}, onRetry = {}, onOpenCustomer = { opened += it }, onAddCustomer = {},
            )
        }
        composeRule.onNodeWithText("Owes ${rupees(50_000)}").assertIsDisplayed()
        composeRule.onNodeWithText("Settled").assertIsDisplayed()
        screenshot("40_customers")
        composeRule.onNodeWithText("Anita Das").performClick()
        assertEquals(listOf(1L), opened)
    }

    @Test
    fun customerDetail_showsBalanceAndStatement() {
        var paid = false
        val state = CustomerDetailUiState.Ready(
            account = CustomerAccount(anita, Money(50_000)),
            statement = Ledger.statement(entries).asReversed(),
            invoices = emptyList(),
            charged = Money(80_000),
            paid = Money(30_000),
        )
        show { CustomerDetailContent(state, onReceivePayment = { paid = true }, onOpenInvoice = {}) }
        composeRule.onNodeWithTag("customer_balance").assertIsDisplayed()
        composeRule.onNodeWithText("Credit limit ${rupees(500_000)}").assertIsDisplayed()
        composeRule.onNodeWithTag("customer_detail").performScrollToNode(hasText("Payment received"))
        composeRule.onNodeWithText("Payment received").assertIsDisplayed()
        screenshot("41_customer_detail")
        composeRule.onNodeWithTag("customer_detail").performScrollToNode(hasTestTag("customer_receive_payment"))
        composeRule.onNodeWithTag("customer_receive_payment").assertIsEnabled().performClick()
        assertEquals(true, paid)
    }

    @Test
    fun accountPayment_showsWhatIsDue() {
        show {
            AccountPaymentContent(
                state = AccountPaymentUiState(isLoading = false, account = CustomerAccount(anita, Money(50_000)), amount = "200", method = PaymentMethod.UPI),
                onAmountChange = {}, onMethodChange = {}, onReferenceChange = {}, onNoteChange = {}, onSave = {},
            )
        }
        composeRule.onNodeWithText("Owes ${rupees(50_000)}").assertIsDisplayed()
        composeRule.onNodeWithText("Balance after this payment: ${rupees(30_000)}").assertIsDisplayed()
        composeRule.onNodeWithText("UPI reference (optional)").assertIsDisplayed()
        screenshot("42_account_payment")
    }

    @Test
    fun supplierList_showsHiddenSuppliersWhenAsked() {
        show {
            SupplierListContent(
                state = SupplierListUiState(
                    isLoading = false,
                    showHidden = true,
                    suppliers = listOf(Supplier(id = 1, name = "Fresh Mills", phone = "033 2229 1234"), Supplier(id = 2, name = "Closed Co", active = false)),
                    activeCount = 1,
                    hiddenCount = 1,
                ),
                onQueryChange = {}, onShowHiddenChange = {}, onRetry = {}, onOpenSupplier = {}, onAddSupplier = {},
            )
        }
        composeRule.onNodeWithText("Hidden").assertIsDisplayed()
        composeRule.onNodeWithText("Show hidden (1)").assertIsDisplayed()
        screenshot("43_suppliers")
    }

    private val shirt = TestData.tShirt()
    private val flour = TestData.flour(grams = 500)

    @Test
    fun orderEditor_addsPickedItems_andShowsLineErrors() {
        val added = mutableListOf<PickableItem>()
        val lines = listOf(
            PurchaseLineForm.forVariant("a", shirt, shirt.variants[1], quantity = 3),
            PurchaseLineForm.forVariant("b", flour, flour.variants[0]),
        )
        show {
            PurchaseOrderEditorContent(
                state = PurchaseOrderEditorUiState(
                    isLoading = false,
                    suppliers = listOf(Supplier(id = 1, name = "Fresh Mills")),
                    supplierId = 1,
                    lines = lines,
                    picker = ItemPickerState("flour", listOf(PickableItem(flour, flour.variants[0]))),
                    lineErrors = mapOf("b" to setOf(PurchaseLineError.QUANTITY_REQUIRED)),
                    showErrors = true,
                ),
                actions = object : OrderEditorActions {
                    override fun onAddItem(item: PickableItem) {
                        added += item
                    }
                },
            )
        }
        composeRule.onNodeWithText("Fresh Mills").assertIsDisplayed()
        composeRule.onNodeWithText("500 g in stock").performClick()
        assertEquals("Wheat Flour", added.single().product.name)
        composeRule.onNodeWithTag("order_editor").performScrollToNode(hasText("Enter a quantity"))
        composeRule.onNodeWithText("Enter a quantity").assertIsDisplayed()
        composeRule.onNodeWithText("Quantity (kg)").assertIsDisplayed()
        screenshot("44_order_editor")
        composeRule.onNodeWithTag("order_editor").performScrollToNode(hasTestTag("order_save_send"))
        composeRule.onNodeWithTag("order_save_send").assertIsDisplayed()
    }

    @Test
    fun orderDetail_showsProgress_andActions() {
        val order = PurchaseOrder(
            id = 1, number = "PO/26-27/00001", supplierId = 1, supplierName = "Fresh Mills", status = PurchaseOrderStatus.PARTLY_RECEIVED,
            createdAtMillis = TestData.clock.millis(),
            lines = listOf(PurchaseOrderLine(id = 1, variantId = 41, productName = "Wheat Flour", variantLabel = "", stockUnit = StockUnit.GRAM, quantityOrdered = 10_000, quantityReceived = 4_000, unitCost = Money(4_000))),
        )
        var receive = false
        show {
            PurchaseOrderDetailContent(
                state = PurchaseOrderDetailUiState.Ready(order, emptyList(), Money(24_000)),
                isWorking = false, onMarkOrdered = {}, onReceive = { receive = true }, onCancel = {}, onOpenReceipt = {},
            )
        }
        composeRule.onNodeWithText("Partly received").assertIsDisplayed()
        composeRule.onNodeWithText("Still to arrive: ${rupees(24_000)}").assertIsDisplayed()
        composeRule.onNodeWithTag("order_detail").performScrollToNode(hasText("Received 4 kg of 10 kg"))
        composeRule.onNodeWithText("10 kg × ${rupees(4_000)} per kg").assertIsDisplayed()
        screenshot("45_order_detail")
        composeRule.onNodeWithTag("order_detail").performScrollToNode(hasTestTag("order_receive"))
        composeRule.onNodeWithTag("order_receive").performClick()
        assertEquals(true, receive)
    }

    @Test
    fun receiveGoods_fromAnOrder_asksForBatchAndExpiryOfFood() {
        val biscuits = TestData.biscuits()
        val line = PurchaseLineForm.forVariant("a", biscuits, biscuits.variants[0], quantity = 24).copy(orderLineId = 7, remaining = 24)
        val order = PurchaseOrder(id = 3, number = "PO/26-27/00003", supplierId = 1, supplierName = "Good Bake Distributors", status = PurchaseOrderStatus.ORDERED)
        show {
            ReceiveGoodsContent(
                state = ReceiveGoodsUiState(isLoading = false, order = order, supplierId = 1, lines = listOf(line), duplicateSupplierInvoice = true, supplierInvoice = "GB-1"),
                actions = object : ReceiveActions {},
            )
        }
        composeRule.onNodeWithText("From Good Bake Distributors").assertIsDisplayed()
        composeRule.onNodeWithText("This bill was already received from this supplier").assertIsDisplayed()
        composeRule.onNodeWithTag("receive_goods").performScrollToNode(hasTestTag("line_batch_a"))
        composeRule.onNodeWithText("Due: 24 pcs").assertIsDisplayed()
        composeRule.onNodeWithText("No expiry date").assertIsDisplayed()
        screenshot("46_receive_goods")
    }

    private val alertsState = StockAlertsUiState(
        isLoading = false,
        today = today,
        suggestions = ReorderPlanner.suggestions(listOf(shirt.copy(preferredSupplierId = 1), TestData.juice(stock = 0))),
        suppliers = listOf(Supplier(id = 1, name = "Roy Textiles")),
    )

    @Test
    fun stockAlerts_lowStock_canBeChosenForOrders() {
        val toggled = mutableListOf<Long>()
        var create = false
        show {
            StockAlertsContent(
                alertsState,
                object : StockAlertsActions {
                    override fun onToggle(variantId: Long) {
                        toggled += variantId
                    }

                    override fun onCreateDrafts() {
                        create = true
                    }
                },
            )
        }
        composeRule.onNodeWithText("Low stock (2)").assertIsDisplayed()
        composeRule.onNodeWithText("No preferred supplier").assertIsDisplayed()
        composeRule.onNodeWithText("Roy Textiles").assertIsDisplayed()
        screenshot("47_stock_alerts_low")
        composeRule.onNodeWithTag("suggestion_${TestData.juice().variants[0].id}").performClick()
        assertEquals(listOf(TestData.juice().variants[0].id), toggled)
        composeRule.onNodeWithTag("alerts_create_drafts").performClick()
        assertEquals(true, create)
    }

    @Test
    fun stockAlerts_expiry_offersWriteOff() {
        val expired = BatchWithProduct(
            StockBatch(id = 9, variantId = 21, batchNumber = "GB-1", expiryDate = today.minusDays(2), quantity = 6), 2, "Butter Biscuits", "200 g",
            ProductCategory.PACKAGED_FOOD, StockUnit.UNIT, Money(2_200),
        )
        val written = mutableListOf<BatchWithProduct>()
        show {
            StockAlertsContent(
                alertsState.copy(tab = AlertsTab.EXPIRY, batches = listOf(expired), expiredBatchCount = 1, expiredValue = Money(13_200)),
                object : StockAlertsActions {
                    override fun onWriteOff(batch: BatchWithProduct) {
                        written += batch
                    }
                },
            )
        }
        composeRule.onNodeWithText("Expired stock at cost (1 batch)").assertIsDisplayed()
        composeRule.onNodeWithText("Batch GB-1 · Expired ${com.invision.pos.ui.format.DateFormats.date(today.minusDays(2))}").assertIsDisplayed()
        screenshot("48_stock_alerts_expiry")
        composeRule.onNodeWithTag("write_off_9").performClick()
        assertEquals(listOf(expired), written)
    }

    @Test
    fun writeOffForm_showsReasonsAndErrors() {
        val batch = BatchWithProduct(StockBatch(id = 9, variantId = 41, expiryDate = today, quantity = 1_500), 4, "Wheat Flour", "", ProductCategory.INGREDIENT, StockUnit.GRAM, Money(4_500))
        show {
            WriteOffForm(
                state = WastageDialogState(batch, StockChangeReason.SPOILED, "2", error = com.invision.pos.domain.usecase.WastageError.MORE_THAN_IN_BATCH),
                onReasonChange = {}, onQuantityChange = {}, onNoteChange = {},
            )
        }
        composeRule.onNodeWithText("Quantity (kg)").assertIsDisplayed()
        composeRule.onNodeWithText("More than is left in this batch").assertIsDisplayed()
        composeRule.onNodeWithText("Spoiled").assertIsDisplayed()
        screenshot("49_write_off")
    }

    @Test
    fun stockMovements_showChangesWithUnitsAndReferences() {
        fun record(id: Long, change: Int, reason: StockChangeReason, reference: String?, unit: StockUnit = StockUnit.UNIT, name: String = "Cotton T-Shirt") = StockMovementRecord(
            movement = StockMovement(id = id, variantId = 11, quantityChange = change, reason = reason, createdAtMillis = TestData.clock.millis()),
            productId = 1, productName = name, variantLabel = if (unit == StockUnit.UNIT) "M · Navy" else "", category = ProductCategory.GARMENT,
            stockUnit = unit, batchNumber = null, reference = reference, unitCost = Money(25_000),
        )
        val records = listOf(
            record(1, -2, StockChangeReason.SOLD, "INV/26-27/00012"),
            record(2, 750, StockChangeReason.RECEIVED, "GRN/26-27/00004", StockUnit.GRAM, "Wheat Flour"),
            record(3, -1, StockChangeReason.DAMAGED, null),
        )
        show {
            StockMovementsContent(
                state = StockMovementsUiState(isLoading = false, movements = records, receivedCount = 1, outCount = 2, wastageValue = Money(25_000)),
                onRangeChange = {}, onGroupChange = {}, onCategoryChange = {}, onQueryChange = {}, onRetry = {}, onOpenProduct = {},
            )
        }
        composeRule.onNodeWithText("+750 g").assertIsDisplayed()
        composeRule.onNodeWithText("-2").assertIsDisplayed()
        composeRule.onNodeWithTag("movement_list").performScrollToNode(hasText("Damaged"))
        screenshot("50_stock_movements")
    }

    @Test
    fun menuList_showsServingsAndAvailability() {
        val toggled = mutableListOf<Pair<Long, Boolean>>()
        val paratha = MenuItem(id = 1, name = "Paratha", section = "Breakfast", price = Money(6_000), deductIngredients = true, recipe = listOf(RecipeLine(41, 120)))
        val idli = MenuItem(id = 2, name = "Idli", section = "Breakfast", price = Money(5_000), available = false)
        show {
            MenuListContent(
                state = MenuListUiState(isLoading = false, sections = listOf(MenuSection("Breakfast", listOf(MenuDish(paratha, 4), MenuDish(idli, null)))), dishCount = 2, unavailableCount = 1),
                onQueryChange = {}, onAvailableChange = { id, on -> toggled += id to on }, onRetry = {}, onOpenDish = {}, onAddDish = {},
            )
        }
        composeRule.onNodeWithText("Stock for 4 servings").assertIsDisplayed()
        composeRule.onNodeWithText("Not available").assertIsDisplayed()
        screenshot("51_menu")
        composeRule.onNodeWithTag("dish_available_2").performClick()
        assertEquals(listOf(2L to true), toggled)
    }

    @Test
    fun menuEditor_hasNoRecipe() {
        show {
            MenuEditorContent(
                state = MenuEditorUiState(
                    isLoading = false,
                    isNew = false,
                    sections = listOf("Breakfast", "Drinks"),
                    form = MenuItemForm(name = "Paratha", section = "Breakfast", price = "60"),
                ),
                actions = object : MenuEditorActions {},
            )
        }
        composeRule.onNodeWithText("Drinks").assertIsDisplayed() // section suggestion
        composeRule.onNodeWithText("Recipe (per serving)").assertDoesNotExist()
        composeRule.onNodeWithTag("menu_deduct").assertDoesNotExist()
        screenshot("52_menu_editor")
    }

    @Test
    fun reports_showTheTableWithTotals() {
        val table = Reports.stockValue(listOf(shirt, flour))
        show {
            ReportsContent(
                state = ReportsUiState(
                    filters = ReportFilters(type = ReportType.STOCK_VALUE),
                    period = ReportPeriod.of(PeriodPreset.THIS_MONTH, today),
                    isLoading = false,
                    table = table,
                ),
                actions = object : ReportsActions {},
            )
        }
        composeRule.onNodeWithText("Stock value").assertIsDisplayed()
        composeRule.onNodeWithText("3 rows").assertIsDisplayed()
        // Columns to the right are scrolled off screen but present.
        composeRule.onNodeWithText("Value at cost").assertExists()
        composeRule.onNodeWithText(rupees(2_250)).assertExists() // 500 g of flour at ₹45/kg
        screenshot("53_reports")
    }

    @Test
    fun reports_longTablesAreShownOnePageAtATime() {
        val rows = (1..250).map { listOf<ReportValue>(ReportValue.Text("Row $it")) }
        val table = ReportTable("Long report", listOf(ReportColumn("Name")), rows)
        show {
            ReportsContent(
                state = ReportsUiState(
                    filters = ReportFilters(type = ReportType.STOCK_VALUE),
                    period = ReportPeriod.of(PeriodPreset.THIS_MONTH, today),
                    isLoading = false,
                    table = table,
                ),
                actions = object : ReportsActions {},
            )
        }
        composeRule.onNodeWithText("250 rows").assertExists()
        composeRule.onNodeWithTag("report_page_position").performScrollTo().assertTextEquals("Rows 1\u2013100 of 250")
        composeRule.onNodeWithText("Row 100").assertExists()
        composeRule.onNodeWithText("Row 101").assertDoesNotExist()
        composeRule.onNodeWithTag("report_previous").assertIsNotEnabled()

        composeRule.onNodeWithTag("report_next").performClick()
        composeRule.onNodeWithTag("report_page_position").assertTextEquals("Rows 101\u2013200 of 250")
        composeRule.onNodeWithText("Row 101").assertExists()
        composeRule.onNodeWithText("Row 100").assertDoesNotExist()

        composeRule.onNodeWithTag("report_next").performClick()
        composeRule.onNodeWithTag("report_page_position").assertTextEquals("Rows 201\u2013250 of 250")
        composeRule.onNodeWithText("Row 250").assertExists()
        composeRule.onNodeWithTag("report_next").assertIsNotEnabled()
    }

    @Test
    fun moreScreen_leadsToEveryPage() {
        val opened = mutableListOf<String>()
        show {
            MoreScreen(
                shop = ShopUiState(restaurantMenu = true),
                actions = object : MoreActions {
                    override fun onOpenSuppliers() {
                        opened += "suppliers"
                    }

                    override fun onOpenReports() {
                        opened += "reports"
                    }
                },
            )
        }
        composeRule.onNodeWithText("Suppliers").performClick()
        composeRule.onNodeWithTag("more_list").performScrollToNode(hasText("Reports"))
        composeRule.onNodeWithText("Reports").performClick()
        assertEquals(listOf("suppliers", "reports"), opened)
        screenshot("54_more")
        composeRule.onNodeWithText("Restaurant menu").assertIsDisplayed()
    }

    @Test
    fun billing_quickMenu_addsDishesToTheBill() {
        val dosa = MenuItem(id = 5, name = "Masala Dosa", section = "Breakfast", price = Money(12_000))
        val tea = MenuItem(id = 6, name = "Masala Tea", section = "Drinks", price = Money(2_000))
        val cart = Cart().add(CartLine.forMenuItem(dosa, 2))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val added = mutableListOf<MenuItem>()
        show {
            CartContent(
                state = BillingUiState(
                    settings = TestData.settings, productsLoaded = true, productCount = 0, menu = listOf(dosa, tea),
                    cart = cart, totals = totals, check = CartValidator.check(cart, totals, true),
                ),
                actions = object : BillingActions {
                    override fun onAddMenuItem(item: MenuItem) {
                        added += item
                    }
                },
            )
        }
        composeRule.onNodeWithTag("quick_menu").assertIsDisplayed()
        composeRule.onNodeWithText("Drinks").assertIsDisplayed()
        composeRule.onNodeWithTag("dish_5").performClick()
        assertEquals(listOf(dosa), added)
        composeRule.onNodeWithText("Masala Dosa").assertIsDisplayed() // the cart line
        screenshot("55_billing_menu")
    }

    @Test
    fun billing_payment_canGoOnASavedCustomersAccount() {
        val cart = Cart(customer = CustomerDetails(name = anita.name, phone = anita.phone.orEmpty(), customerId = anita.id))
            .add(CartLine.from(shirt, shirt.variants[0]))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val state = BillingUiState(
            settings = TestData.settings, productsLoaded = true, productCount = 1, cart = cart, totals = totals,
            check = CartValidator.check(cart, totals, true), account = CustomerAccount(anita, Money(20_000)),
            stage = BillingStage.PAYMENT, payments = listOf(PaymentDraft(key = 0, method = PaymentMethod.CREDIT, amount = "499.00")),
        )
        show { PaymentContent(state, object : BillingActions {}) }
        composeRule.onNodeWithTag("method_CREDIT_0").assertIsDisplayed()
        composeRule.onNodeWithText("Added to Anita Das's account. Balance after this bill: ${rupees(69_900)}").assertIsDisplayed()
        screenshot("56_billing_on_account")
    }
}
