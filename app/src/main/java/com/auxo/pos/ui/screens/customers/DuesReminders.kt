// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.customers

import android.content.Context
import com.auxo.pos.R
import com.auxo.pos.core.locale.LocaleContext
import com.auxo.pos.core.receipt.WhatsAppShare
import com.auxo.pos.domain.customers.DuesReminder
import com.auxo.pos.domain.billing.Upi
import com.auxo.pos.domain.customers.ReminderShop
import com.auxo.pos.domain.customers.ReminderTone
import java.time.LocalDate
import java.time.ZoneId
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.core.format.MoneyFormatter

/** The reminder text for [account], in the shop's bill language: the customer's name, what they owe, the shop's name and (if valid) its UPI ID. */
internal fun reminderMessage(context: Context, account: CustomerAccount, shop: ReminderShop, formatter: MoneyFormatter): String {
    val resources = LocaleContext.wrap(context, shop.language).resources
    val age = DuesReminder.ageInDays(account.owingSinceMillis, LocalDate.now(), ZoneId.systemDefault())
    val body = when (DuesReminder.toneFor(age)) {
        ReminderTone.FRIENDLY -> R.string.reminder_body
        ReminderTone.FIRM -> R.string.reminder_body_firm
        ReminderTone.URGENT -> R.string.reminder_body_urgent
    }
    // A link that opens a UPI app with the shop's ID and the exact amount filled in.
    val payLink = shop.upiId?.let { Upi.billLink(it, shop.shopName, shop.shopName, account.balance, "") }
    return DuesReminder.message(
        body = resources.getString(body),
        upiLine = resources.getString(R.string.reminder_upi),
        customerName = account.customer.name,
        shop = shop,
        amountText = formatter.format(account.balance),
        payLinkLine = resources.getString(R.string.reminder_pay_link),
        payLink = payLink,
    )
}

/**
 * Opens WhatsApp with the reminder typed in, for the customer's number when it is a valid mobile number, otherwise
 * for the user to pick a chat. Nothing is sent: the user presses send. Returns false if nothing could open.
 */
internal fun openReminder(context: Context, account: CustomerAccount, shop: ReminderShop, formatter: MoneyFormatter): Boolean =
    WhatsAppShare.openText(
        context,
        DuesReminder.whatsAppNumber(account.customer.phone),
        reminderMessage(context, account, shop, formatter),
        context.getString(R.string.reminder_share_title),
    )
