package com.auxo.pos.domain.billing

import java.time.Clock
import java.time.ZoneId

/**
 * The store's time zone. Invoice dates, financial-year numbering and day boundaries
 * for reports are all decided here, so a phone set to another zone cannot change
 * which day or financial year a bill falls in.
 */
object BusinessTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun clock(): Clock = Clock.system(ZONE)
}
