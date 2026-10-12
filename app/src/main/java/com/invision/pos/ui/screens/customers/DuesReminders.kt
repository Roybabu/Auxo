package com.invision.pos.ui.screens.customers

import android.content.Context
import com.invision.pos.R
import com.invision.pos.core.locale.LocaleContext
import com.invision.pos.core.receipt.WhatsAppShare
import com.invision.pos.domain.customers.DuesReminder
import com.invision.pos.domain.customers.ReminderShop
import com.invision.pos.domain.model.CustomerAccount
import com.invision.pos.core.format.MoneyFormatter

/** The reminder text for [account], in the shop's bill language: the customer's name, what they owe, the shop's name and (if valid) its UPI ID. */
internal fun reminderMessage(context: Context, account: CustomerAccount, shop: ReminderShop, formatter: MoneyFormatter): String {
    val resources = LocaleContext.wrap(context, shop.language).resources
    return DuesReminder.message(
        body = resources.getString(R.string.reminder_body),
        upiLine = resources.getString(R.string.reminder_upi),
        customerName = account.customer.name,
        shop = shop,
        amountText = formatter.format(account.balance),
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
