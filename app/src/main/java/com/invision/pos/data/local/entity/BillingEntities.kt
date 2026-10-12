package com.invision.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A sale invoice or a credit note. Amounts are in paise. Invoices are never deleted. */
@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["number"], unique = true),
        Index("created_at"),
        Index("original_invoice_id"),
        Index("customer_id"),
    ],
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    /** [com.invision.pos.domain.model.InvoiceType] name. */
    val type: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "seller_name") val sellerName: String,
    @ColumnInfo(name = "seller_address") val sellerAddress: String,
    @ColumnInfo(name = "seller_phone") val sellerPhone: String,
    @ColumnInfo(name = "seller_gstin") val sellerGstin: String,
    @ColumnInfo(name = "seller_state_code") val sellerStateCode: String,
    @ColumnInfo(name = "buyer_name") val buyerName: String?,
    @ColumnInfo(name = "buyer_phone") val buyerPhone: String?,
    @ColumnInfo(name = "buyer_gstin") val buyerGstin: String?,
    @ColumnInfo(name = "place_of_supply") val placeOfSupply: String,
    @ColumnInfo(name = "gst_registered") val gstRegistered: Boolean,
    @ColumnInfo(name = "prices_include_tax") val pricesIncludeTax: Boolean,
    @ColumnInfo(name = "inter_state") val interState: Boolean,
    @ColumnInfo(name = "subtotal_minor") val subtotalMinor: Long,
    @ColumnInfo(name = "discount_minor") val discountMinor: Long,
    @ColumnInfo(name = "taxable_minor") val taxableMinor: Long,
    @ColumnInfo(name = "cgst_minor") val cgstMinor: Long,
    @ColumnInfo(name = "sgst_minor") val sgstMinor: Long,
    @ColumnInfo(name = "igst_minor") val igstMinor: Long,
    @ColumnInfo(name = "round_off_minor") val roundOffMinor: Long,
    @ColumnInfo(name = "grand_total_minor") val grandTotalMinor: Long,
    @ColumnInfo(name = "savings_minor") val savingsMinor: Long,
    @ColumnInfo(name = "original_invoice_id") val originalInvoiceId: Long?,
    @ColumnInfo(name = "original_invoice_number") val originalInvoiceNumber: String?,
    val note: String?,
    /** Customer account the invoice belongs to. Added in version 3. */
    @ColumnInfo(name = "customer_id") val customerId: Long? = null,
    /** Why the bill was discounted. Added in version 9. */
    @ColumnInfo(name = "discount_reason") val discountReason: String? = null,
    /** DINE_IN or TAKEAWAY for restaurant bills. Added in version 11. */
    @ColumnInfo(name = "service_type") val serviceType: String? = null,
    @ColumnInfo(name = "table_number") val tableNumber: String? = null,
)

@Entity(
    tableName = "invoice_lines",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        // Deleting a product keeps its invoice lines; the snapshot fields still describe it.
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("invoice_id"), Index("variant_id"), Index("original_line_id")],
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "invoice_id") val invoiceId: Long,
    @ColumnInfo(name = "variant_id") val variantId: Long?,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "variant_label") val variantLabel: String,
    val barcode: String,
    @ColumnInfo(name = "hsn_code") val hsnCode: String?,
    val quantity: Int,
    @ColumnInfo(name = "unit_price_minor") val unitPriceMinor: Long,
    @ColumnInfo(name = "mrp_minor") val mrpMinor: Long?,
    @ColumnInfo(name = "discount_minor") val discountMinor: Long,
    @ColumnInfo(name = "taxable_minor") val taxableMinor: Long,
    @ColumnInfo(name = "gst_rate_bp") val gstRateBasisPoints: Int,
    @ColumnInfo(name = "cgst_minor") val cgstMinor: Long,
    @ColumnInfo(name = "sgst_minor") val sgstMinor: Long,
    @ColumnInfo(name = "igst_minor") val igstMinor: Long,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    @ColumnInfo(name = "returned_quantity") val returnedQuantity: Int,
    /** On credit notes: the sale line being returned. */
    @ColumnInfo(name = "original_line_id") val originalLineId: Long?,
    /** Restaurant dish sold on this line. Added in version 3. */
    @ColumnInfo(name = "menu_item_id") val menuItemId: Long? = null,
    /** Why this line was discounted. Added in version 9. */
    @ColumnInfo(name = "discount_reason") val discountReason: String? = null,
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("invoice_id"), Index("created_at")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "invoice_id") val invoiceId: Long,
    /** [com.invision.pos.domain.model.PaymentMethod] name. */
    val method: String,
    /** [com.invision.pos.domain.model.PaymentKind] name. */
    val kind: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val reference: String?,
    @ColumnInfo(name = "tendered_minor") val tenderedMinor: Long?,
    @ColumnInfo(name = "change_minor") val changeMinor: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Single-row table holding store and billing settings. */
@Entity(tableName = "store_settings")
data class StoreSettingsEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "store_name") val storeName: String,
    val address: String,
    val phone: String,
    val gstin: String,
    @ColumnInfo(name = "state_code") val stateCode: String,
    @ColumnInfo(name = "gst_registered") val gstRegistered: Boolean,
    @ColumnInfo(name = "prices_include_tax") val pricesIncludeTax: Boolean,
    @ColumnInfo(name = "round_off") val roundOff: Boolean,
    @ColumnInfo(name = "invoice_prefix") val invoicePrefix: String,
    @ColumnInfo(name = "credit_note_prefix") val creditNotePrefix: String,
    @ColumnInfo(name = "upi_id") val upiId: String,
    @ColumnInfo(name = "upi_payee_name") val upiPayeeName: String,
    @ColumnInfo(name = "receipt_paper") val receiptPaper: String,
    @ColumnInfo(name = "receipt_footer") val receiptFooter: String,
    @ColumnInfo(name = "receipt_show_hsn") val receiptShowHsn: Boolean,
    @ColumnInfo(name = "receipt_show_tax_breakup") val receiptShowTaxBreakup: Boolean,
    @ColumnInfo(name = "receipt_show_savings") val receiptShowSavings: Boolean,
    @ColumnInfo(name = "receipt_show_customer") val receiptShowCustomer: Boolean,
    @ColumnInfo(name = "receipt_show_barcode") val receiptShowBarcode: Boolean,
    /** [com.invision.pos.domain.model.Language] tag the bill is printed in. Added in version 7; null means English. */
    @ColumnInfo(name = "receipt_language") val receiptLanguage: String? = null,
    /** Print the UPI payment QR on receipts. Added in version 8; existing shops get it on. */
    @ColumnInfo(name = "receipt_show_upi_qr") val receiptShowUpiQr: Boolean = true,
    /** Most an Employee may discount, in basis points. Added in version 9; 0 means none. */
    @ColumnInfo(name = "employee_discount_limit_bp") val employeeDiscountLimitBp: Int = 0,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

/** Next invoice or credit note number for one prefix and financial year. */
@Entity(tableName = "number_sequences")
data class NumberSequenceEntity(
    @PrimaryKey val name: String,
    @ColumnInfo(name = "next_value") val nextValue: Long,
)

data class InvoiceWithDetails(
    @Embedded val invoice: InvoiceEntity,
    @Relation(parentColumn = "id", entityColumn = "invoice_id")
    val lines: List<InvoiceLineEntity>,
    @Relation(parentColumn = "id", entityColumn = "invoice_id")
    val payments: List<PaymentEntity>,
)

data class InvoiceSummaryRow(
    val id: Long,
    val number: String,
    val type: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "buyer_name") val buyerName: String?,
    @ColumnInfo(name = "buyer_phone") val buyerPhone: String?,
    @ColumnInfo(name = "grand_total_minor") val grandTotalMinor: Long,
    @ColumnInfo(name = "item_count") val itemCount: Int,
)

data class PaymentWithInvoice(
    @Embedded val payment: PaymentEntity,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String,
)

data class SalesTotalsRow(
    @ColumnInfo(name = "net_minor") val netMinor: Long,
    @ColumnInfo(name = "bill_count") val billCount: Int,
)
