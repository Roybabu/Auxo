package com.invision.pos.domain.usecase

import com.invision.pos.data.repository.StoreRepository
import com.invision.pos.domain.model.StoreOverview
import kotlinx.coroutines.flow.Flow

/** Streams the current day's [StoreOverview]. */
class ObserveStoreOverviewUseCase(
    private val storeRepository: StoreRepository,
) {
    operator fun invoke(): Flow<StoreOverview> = storeRepository.observeOverview()
}
