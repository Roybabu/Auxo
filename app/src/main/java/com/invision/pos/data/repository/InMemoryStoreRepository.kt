package com.invision.pos.data.repository

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.StoreOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporary in-memory implementation used until a local database
 * (data/local) is added. Values reset when the app process ends.
 */
class InMemoryStoreRepository(
    initial: StoreOverview = DEFAULT_OVERVIEW,
) : StoreRepository {

    private val overview = MutableStateFlow(initial)

    override fun observeOverview(): Flow<StoreOverview> = overview.asStateFlow()

    companion object {
        val DEFAULT_OVERVIEW = StoreOverview(
            storeName = "My Store",
            salesToday = Money.ZERO,
            billsToday = 0,
        )
    }
}
