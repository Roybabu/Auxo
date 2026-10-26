// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.customers

import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.domain.model.Language
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DuesReminderTest {

    private val body = "Hello %1\$s, a reminder from %2\$s: you owe %3\$s. Thank you!"
    private val upiLine = "Pay by UPI to %1\$s."
    private val shop = ReminderShop.of("Sample Mart", "samplemart@okaxis", Language.ENGLISH)

    @Test
    fun message_hasNameShopAndAmount_andTheUpiLineOnlyWithAValidUpiId() {
        val withUpi = DuesReminder.message(body, upiLine, "  Asha ", shop, "₹1,250.00")
        assertEquals("Hello Asha, a reminder from Sample Mart: you owe ₹1,250.00. Thank you!\nPay by UPI to samplemart@okaxis.", withUpi)

        val none = ReminderShop.of("Sample Mart", "", Language.ENGLISH)
        assertNull(none.upiId)
        assertFalse(DuesReminder.message(body, upiLine, "Asha", none, "₹1.00").contains("UPI"))
        assertNull(ReminderShop.of("Sample Mart", "not a upi id", Language.ENGLISH).upiId)
    }

    @Test
    fun message_neverMentionsAnythingBeyondTheAskedDetails() {
        val text = DuesReminder.message(body, upiLine, "Asha", shop, "₹10.00")
        assertFalse(text.contains("http"))
        assertEquals(2, text.lines().size)
    }

    @Test
    fun whatsAppNumber_isTheTenDigitMobileWithCountryCode() {
        assertEquals("919830012345", DuesReminder.whatsAppNumber("9830012345"))
        assertEquals("919830012345", DuesReminder.whatsAppNumber("+91 98300 12345"))
        assertEquals("919830012345", DuesReminder.whatsAppNumber("09830012345"))
        assertNull(DuesReminder.whatsAppNumber("033-2456789"))
        assertNull(DuesReminder.whatsAppNumber("12345"))
        assertNull(DuesReminder.whatsAppNumber(null))
        assertNull(DuesReminder.whatsAppNumber(""))
    }

    @Test
    fun whatsAppLink_encodesTheMessage_soSpacesNewlinesAndOtherScriptsSurvive() {
        val link = DuesReminder.whatsAppLink("919830012345", "Hi Asha\nPay ₹10 & थैंक्स")
        assertTrue(link.startsWith("https://wa.me/919830012345?text="))
        val text = link.substringAfter("?text=")
        assertFalse(text.contains(' ') || text.contains('\n') || text.contains('+') || text.contains('&'))
        assertEquals("Hi Asha\nPay ₹10 & थैंक्स", java.net.URLDecoder.decode(text.replace("%20", "+"), "UTF-8"))
    }

    @Test
    fun age_isWholeDaysSinceTheOldestUnpaidCharge() {
        val day = LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(11, DuesReminder.ageInDays(day + 3_600_000, LocalDate.of(2026, 10, 12), ZoneOffset.UTC))
        assertEquals(0, DuesReminder.ageInDays(day, LocalDate.of(2026, 10, 1), ZoneOffset.UTC))
        assertEquals(0, DuesReminder.ageInDays(day, LocalDate.of(2026, 9, 1), ZoneOffset.UTC)) // never negative
        assertNull(DuesReminder.ageInDays(null, LocalDate.of(2026, 10, 12), ZoneOffset.UTC))
    }

    private fun account(id: Long, name: String, balance: Long, since: Long?) =
        CustomerAccount(Customer(id = id, name = name), Money(balance), owingSinceMillis = since)

    private val accounts = listOf(
        account(1, "Asha", 500_00, since = 300),
        account(2, "Bimal", 900_00, since = 100),
        account(3, "Chitra", 900_00, since = 50),
        account(4, "Dev", 0, since = null),
        account(5, "Esha", -20_00, since = null),
        account(6, "Farid", 200_00, since = null),
    )

    @Test
    fun sortByAmount_isLargestFirst_ties_oldestFirst_andOnlyThoseWhoOwe() {
        assertEquals(listOf("Chitra", "Bimal", "Asha", "Farid"), DuesReminder.sortOwing(accounts, OwingSort.AMOUNT).map { it.customer.name })
    }

    @Test
    fun sortByAge_isOldestFirst_ties_largestFirst_andUnknownLast() {
        assertEquals(listOf("Chitra", "Bimal", "Asha", "Farid"), DuesReminder.sortOwing(accounts, OwingSort.AGE).map { it.customer.name })
        val tied = listOf(account(1, "Asha", 100_00, 10), account(2, "Bimal", 300_00, 10), account(3, "Chitra", 300_00, 10))
        assertEquals(listOf("Bimal", "Chitra", "Asha"), DuesReminder.sortOwing(tied, OwingSort.AGE).map { it.customer.name })
    }

    // Ledger.owingSince

    private var nextId = 1L
    private fun entry(type: LedgerEntryType, amount: Long, at: Long) =
        LedgerEntry(id = nextId++, customerId = 1, type = type, amount = Money(amount), createdAtMillis = at)

    @Test
    fun owingSince_isTheOldestChargeNotYetCovered() {
        val entries = listOf(
            entry(LedgerEntryType.SALE, 100_00, 10),
            entry(LedgerEntryType.SALE, 200_00, 20),
            entry(LedgerEntryType.PAYMENT, -100_00, 30),
        )
        assertEquals(20L, Ledger.owingSince(entries))
    }

    @Test
    fun owingSince_staysWithAPartlyPaidOldCharge() {
        val entries = listOf(entry(LedgerEntryType.SALE, 100_00, 10), entry(LedgerEntryType.SALE, 100_00, 20), entry(LedgerEntryType.PAYMENT, -50_00, 30))
        assertEquals(10L, Ledger.owingSince(entries))
    }

    @Test
    fun owingSince_isNullWhenNothingIsOwed() {
        assertNull(Ledger.owingSince(emptyList()))
        assertNull(Ledger.owingSince(listOf(entry(LedgerEntryType.SALE, 100_00, 10), entry(LedgerEntryType.PAYMENT, -100_00, 20))))
        assertNull(Ledger.owingSince(listOf(entry(LedgerEntryType.PAYMENT, -100_00, 20))))
    }

    @Test
    fun owingSince_countsAdvanceMoneyAgainstTheNextCharges_andOpeningBalances() {
        val advance = listOf(entry(LedgerEntryType.PAYMENT, -100_00, 5), entry(LedgerEntryType.SALE, 150_00, 10))
        assertEquals(10L, Ledger.owingSince(advance))
        val opening = listOf(entry(LedgerEntryType.OPENING_BALANCE, 300_00, 1), entry(LedgerEntryType.RETURN, -50_00, 40))
        assertEquals(1L, Ledger.owingSince(opening))
    }

    @Test
    fun tone_getsFirmerTheLongerTheMoneyIsOwed() {
        assertEquals(ReminderTone.FRIENDLY, DuesReminder.toneFor(null))
        assertEquals(ReminderTone.FRIENDLY, DuesReminder.toneFor(7))
        assertEquals(ReminderTone.FIRM, DuesReminder.toneFor(8))
        assertEquals(ReminderTone.FIRM, DuesReminder.toneFor(30))
        assertEquals(ReminderTone.URGENT, DuesReminder.toneFor(31))
    }

    @Test
    fun message_addsThePayLinkOnlyWhenThereIsAValidUpiId() {
        val link = "upi://pay?pa=samplemart%40okaxis&am=10.00"
        val text = DuesReminder.message(body, upiLine, "Asha", shop, "₹10.00", payLinkLine = "Pay now: %1\$s", payLink = link)
        assertTrue(text.endsWith("Pay now: $link"))
        val none = ReminderShop.of("Sample Mart", "not a upi id", Language.ENGLISH)
        assertFalse(DuesReminder.message(body, upiLine, "Asha", none, "₹10.00", payLinkLine = "Pay now: %1\$s", payLink = link).contains("Pay now"))
    }

    @Test
    fun owingSince_ignoresLoyaltyPointEntries() {
        val entries = listOf(
            entry(LedgerEntryType.POINTS_EARNED, 0, 1).copy(points = 10),
            entry(LedgerEntryType.SALE, 100_00, 50),
        )
        assertEquals(50L, Ledger.owingSince(entries))
    }
}
