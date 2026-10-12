package com.auxo.pos.domain.model

/** A snapshot of the store's activity for the current day, shown on Home. */
data class StoreOverview(
    val storeName: String,
    val salesToday: Money,
    val billsToday: Int,
)
