package com.invision.pos.domain.gst

import com.invision.pos.domain.billing.SampleSales
import com.invision.pos.domain.model.BuyerDetails
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceLine
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Five documents for October 2026 with round amounts, so every total can be checked by hand.
 *
 *  A  sale  B2C intra-state  5%  HSN 6109 x2  taxable 1,000.00 CGST 25.00 SGST 25.00        value 1,050.00
 *  B  sale  B2B inter-state  12% HSN 6109 x1  taxable 2,000.00 IGST 240.00
 *                            5%  HSN 1905 x3  taxable   300.00 IGST  15.00                  value 2,555.00
 *  C  sale  B2C intra-state  12% HSN 6109 x1  taxable   500.00 CGST 30.00 SGST 30.00        value   560.00
 *  D  credit note for A (1 of 2): taxable 500.00 CGST 12.50 SGST 12.50                       value   525.00
 *  E  credit note for B (1 of 3 of the 5% line): taxable 100.00 IGST 5.00                    value   105.00
 */
object GstTestData {
    val zone: ZoneOffset = ZoneOffset.UTC
    const val GSTIN = "29AAAAA0000A1Z5"

    private val seller = SampleSales.shirtsAndBiscuits().seller

    private fun money(rupees: String) = Money.parseOrNull(rupees)!!

    private fun at(day: Int) = LocalDate.of(2026, 10, day).atTime(11, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun line(
        hsn: String,
        rate: Int,
        quantity: Int,
        taxable: String,
        cgst: String = "0",
        sgst: String = "0",
        igst: String = "0",
        name: String = "Item $hsn",
    ): InvoiceLine {
        val t = money(taxable)
        val c = money(cgst)
        val s = money(sgst)
        val i = money(igst)
        return InvoiceLine(
            variantId = 1, productName = name, variantLabel = "", barcode = "", hsnCode = hsn, quantity = quantity,
            unitPrice = Money.ZERO, mrp = null, discount = Money.ZERO, taxable = t, gstRateBasisPoints = rate,
            cgst = c, sgst = s, igst = i, total = t + c + s + i,
        )
    }

    private fun invoice(
        id: Long,
        number: String,
        type: InvoiceType,
        day: Int,
        buyer: BuyerDetails,
        interState: Boolean,
        lines: List<InvoiceLine>,
        original: String? = null,
        stateCode: String = "19",
    ): Invoice {
        fun sum(pick: (InvoiceLine) -> Money) = lines.fold(Money.ZERO) { acc, l -> acc + pick(l) }
        val total = sum { it.total }
        return Invoice(
            id = id, number = number, type = type, createdAtMillis = at(day), seller = seller, buyer = buyer,
            placeOfSupplyStateCode = stateCode, gstRegistered = true, pricesIncludeTax = false, interState = interState,
            lines = lines, subtotal = sum { it.taxable }, discountTotal = Money.ZERO, taxableTotal = sum { it.taxable },
            cgstTotal = sum { it.cgst }, sgstTotal = sum { it.sgst }, igstTotal = sum { it.igst },
            roundOff = Money.ZERO, grandTotal = total, originalInvoiceNumber = original,
        )
    }

    private val consumer = BuyerDetails(name = "Walk-in")
    private val business = BuyerDetails(name = "Das & Sons", gstin = GSTIN)

    val a = invoice(1, "INV/26-27/00001", InvoiceType.SALE, 3, consumer, false, listOf(line("6109", 500, 2, "1000", cgst = "25", sgst = "25")))
    val b = invoice(
        2, "INV/26-27/00002", InvoiceType.SALE, 5, business, true,
        listOf(line("6109", 1200, 1, "2000", igst = "240"), line("1905", 500, 3, "300", igst = "15")),
        stateCode = "29",
    )
    val c = invoice(3, "INV/26-27/00003", InvoiceType.SALE, 10, consumer, false, listOf(line("6109", 1200, 1, "500", cgst = "30", sgst = "30")))
    val d = invoice(4, "CN/26-27/00001", InvoiceType.RETURN, 12, consumer, false, listOf(line("6109", 500, 1, "500", cgst = "12.50", sgst = "12.50")), original = a.number)
    val e = invoice(5, "CN/26-27/00002", InvoiceType.RETURN, 14, business, true, listOf(line("1905", 500, 1, "100", igst = "5")), original = b.number, stateCode = "29")

    val all = listOf(a, b, c, d, e)
}
