package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.Rounding
import com.invision.pos.domain.model.basisPointsFor

/** How tax is applied to a bill. */
data class TaxMode(
    /** False for stores not registered for GST: no tax is charged. */
    val gstRegistered: Boolean = true,
    /** True when selling prices already include GST (usual for retail and MRP items). */
    val pricesIncludeTax: Boolean = true,
    /** True for supplies to another state: IGST instead of CGST + SGST. */
    val interState: Boolean = false,
    /** Round the payable total to the nearest rupee. */
    val roundOff: Boolean = true,
)

data class LineAmounts(
    val variantId: Long,
    val gross: Money,
    val lineDiscount: Money,
    val billDiscountShare: Money,
    /** What the customer pays for the line before any tax is added (exclusive) or including tax (inclusive). */
    val net: Money,
    val rateBasisPoints: Int,
    val taxable: Money,
    val cgst: Money,
    val sgst: Money,
    val igst: Money,
    val total: Money,
) {
    val tax: Money get() = cgst + sgst + igst
    val discount: Money get() = lineDiscount + billDiscountShare
}

data class TaxSummaryRow(
    val rateBasisPoints: Int,
    val taxable: Money,
    val cgst: Money,
    val sgst: Money,
    val igst: Money,
)

data class BillTotals(
    val lines: List<LineAmounts>,
    val subtotal: Money,
    val lineDiscountTotal: Money,
    val billDiscountTotal: Money,
    val taxableTotal: Money,
    val cgstTotal: Money,
    val sgstTotal: Money,
    val igstTotal: Money,
    val totalBeforeRounding: Money,
    /** Signed adjustment to reach a whole rupee. */
    val roundOff: Money,
    val grandTotal: Money,
    /** Difference between MRP and what the customer pays, for lines with an MRP. */
    val savings: Money,
    val taxSummary: List<TaxSummaryRow>,
) {
    val discountTotal: Money get() = lineDiscountTotal + billDiscountTotal
    val taxTotal: Money get() = cgstTotal + sgstTotal + igstTotal

    companion object {
        val EMPTY = BillTotals(
            emptyList(), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
            Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, emptyList(),
        )
    }
}

/**
 * Works out discounts, GST and totals for a cart. All arithmetic is in paise.
 *
 * Order: line discounts, then the bill discount shared across lines in proportion
 * to their value, then GST per line on the discounted value. Lines without a GST
 * rate are taxed at 0% here; the cart validator blocks checkout for them.
 */
object BillCalculator {

    fun calculate(cart: Cart, mode: TaxMode): BillTotals {
        if (cart.lines.isEmpty()) return BillTotals.EMPTY

        val gross = cart.lines.map { it.unitPrice * it.quantity }
        val lineDiscounts = cart.lines.mapIndexed { i, line -> line.discount?.amountOn(gross[i]) ?: Money.ZERO }
        val afterLineDiscount = gross.mapIndexed { i, value -> value - lineDiscounts[i] }
        val base = afterLineDiscount.fold(Money.ZERO) { acc, m -> acc + m }
        val billDiscount = cart.billDiscount?.amountOn(base) ?: Money.ZERO
        val shares = allocate(billDiscount, afterLineDiscount)

        val lines = cart.lines.mapIndexed { i, line ->
            val net = afterLineDiscount[i] - shares[i]
            val rate = when {
                !mode.gstRegistered -> 0
                line.gstRate == null -> 0
                else -> {
                    val perPiece = Money(Rounding.halfUp(net.minorUnits, line.quantity.toLong()))
                    line.gstRate.basisPointsFor(perPiece, mode.pricesIncludeTax)
                }
            }
            lineAmounts(line.variantId, gross[i], lineDiscounts[i], shares[i], net, rate, mode)
        }

        val totalBeforeRounding = sum(lines.map { it.total })
        val roundOff = if (mode.roundOff) roundToRupee(totalBeforeRounding) - totalBeforeRounding else Money.ZERO
        val grandTotal = totalBeforeRounding + roundOff

        val mrpLines = cart.lines.indices.filter { cart.lines[it].mrp != null }
        val savings = if (mode.pricesIncludeTax || !mode.gstRegistered) {
            val mrpTotal = sum(mrpLines.map { cart.lines[it].mrp!! * cart.lines[it].quantity })
            val paid = sum(mrpLines.map { lines[it].total })
            (mrpTotal - paid).takeIf { it > Money.ZERO } ?: Money.ZERO
        } else {
            Money.ZERO
        }

        return BillTotals(
            lines = lines,
            subtotal = sum(gross),
            lineDiscountTotal = sum(lineDiscounts),
            billDiscountTotal = billDiscount,
            taxableTotal = sum(lines.map { it.taxable }),
            cgstTotal = sum(lines.map { it.cgst }),
            sgstTotal = sum(lines.map { it.sgst }),
            igstTotal = sum(lines.map { it.igst }),
            totalBeforeRounding = totalBeforeRounding,
            roundOff = roundOff,
            grandTotal = grandTotal,
            savings = savings,
            taxSummary = taxSummary(lines),
        )
    }

    /** Tax on one line. [net] is the value after all discounts. */
    fun lineAmounts(
        variantId: Long,
        gross: Money,
        lineDiscount: Money,
        billShare: Money,
        net: Money,
        rateBasisPoints: Int,
        mode: TaxMode,
    ): LineAmounts {
        val taxable: Money
        val tax: Money
        if (mode.pricesIncludeTax) {
            taxable = Money(Rounding.halfUp(net.minorUnits * 10_000, 10_000L + rateBasisPoints))
            tax = net - taxable
        } else {
            taxable = net
            tax = Money(Rounding.halfUp(net.minorUnits * rateBasisPoints, 10_000))
        }
        val (cgst, sgst, igst) = splitTax(tax, mode.interState)
        return LineAmounts(
            variantId = variantId,
            gross = gross,
            lineDiscount = lineDiscount,
            billDiscountShare = billShare,
            net = net,
            rateBasisPoints = rateBasisPoints,
            taxable = taxable,
            cgst = cgst,
            sgst = sgst,
            igst = igst,
            total = taxable + tax,
        )
    }

    /** CGST and SGST are half each (CGST takes the odd paisa); inter-state supplies are all IGST. */
    fun splitTax(tax: Money, interState: Boolean): Triple<Money, Money, Money> =
        if (interState) {
            Triple(Money.ZERO, Money.ZERO, tax)
        } else {
            val cgst = Money((tax.minorUnits + 1) / 2)
            Triple(cgst, tax - cgst, Money.ZERO)
        }

    /**
     * Splits [amount] across [weights] in proportion, in whole paise, so the parts
     * add up exactly. Leftover paise go to the largest remainders (earlier lines on ties).
     */
    fun allocate(amount: Money, weights: List<Money>): List<Money> {
        val total = weights.sumOf { it.minorUnits }
        if (amount.isZero || total == 0L) return weights.map { Money.ZERO }
        val exact = weights.map { it.minorUnits.toBigInteger() * amount.minorUnits.toBigInteger() }
        val totalBig = total.toBigInteger()
        val floors = exact.map { (it / totalBig).toLong() }.toMutableList()
        var left = amount.minorUnits - floors.sum()
        val order = exact.indices.sortedWith(compareByDescending<Int> { exact[it] % totalBig }.thenBy { it })
        for (index in order) {
            if (left <= 0) break
            floors[index] = floors[index] + 1
            left--
        }
        return floors.map { Money(it) }
    }

    /** Nearest whole rupee, half up. */
    fun roundToRupee(amount: Money): Money {
        val minor = amount.minorUnits
        val rupees = Math.floorDiv(minor, 100L)
        val paise = Math.floorMod(minor, 100L)
        return Money((rupees + if (paise >= 50) 1 else 0) * 100)
    }

    private fun taxSummary(lines: List<LineAmounts>): List<TaxSummaryRow> =
        lines.groupBy { it.rateBasisPoints }
            .toSortedMap()
            .map { (rate, group) ->
                TaxSummaryRow(
                    rateBasisPoints = rate,
                    taxable = sum(group.map { it.taxable }),
                    cgst = sum(group.map { it.cgst }),
                    sgst = sum(group.map { it.sgst }),
                    igst = sum(group.map { it.igst }),
                )
            }

    private fun sum(values: List<Money>): Money = values.fold(Money.ZERO) { acc, m -> acc + m }
}
