package com.invision.pos.data.repository

import com.invision.pos.domain.model.StoreOverview
import kotlinx.coroutines.flow.Flow

/** Source of store-level information for the rest of the app. */
interface StoreRepository {
    fun observeOverview(): Flow<StoreOverview>
}
