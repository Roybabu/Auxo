package com.auxo.pos.ui.format

import com.auxo.pos.domain.billing.BusinessTime
import androidx.compose.runtime.staticCompositionLocalOf
import com.auxo.pos.core.format.MoneyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Money formatter available to every screen. */
val LocalMoneyFormatter = staticCompositionLocalOf { MoneyFormatter() }

object DateFormats {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun date(date: LocalDate): String = dateFormatter.format(date)

    fun dateTime(epochMillis: Long, zone: ZoneId = BusinessTime.ZONE): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
