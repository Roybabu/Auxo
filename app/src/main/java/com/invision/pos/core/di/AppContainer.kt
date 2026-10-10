package com.invision.pos.core.di

import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.data.repository.InMemoryStoreRepository
import com.invision.pos.data.repository.StoreRepository
import com.invision.pos.domain.usecase.ObserveStoreOverviewUseCase

/**
 * Manual dependency container, created once per process by InvisionApplication.
 * Kept deliberately small; it can be replaced by a DI framework later.
 */
class AppContainer {
    val storeRepository: StoreRepository by lazy { InMemoryStoreRepository() }

    val observeStoreOverview: ObserveStoreOverviewUseCase by lazy {
        ObserveStoreOverviewUseCase(storeRepository)
    }

    val moneyFormatter: MoneyFormatter by lazy { MoneyFormatter() }
}
