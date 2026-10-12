package com.auxo.pos.data.repository

import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.entity.LoyaltySettingsEntity
import com.auxo.pos.domain.loyalty.LoyaltyRules
import com.auxo.pos.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The shop's loyalty rules. Anyone billing can read them; only the Super Admin can change them. */
interface LoyaltyRepository {
    fun observeRules(): Flow<LoyaltyRules>

    suspend fun getRules(): LoyaltyRules

    suspend fun saveRules(rules: LoyaltyRules)
}

class RoomLoyaltyRepository(database: AuxoDatabase) : LoyaltyRepository {
    private val dao = database.customerDao()

    override fun observeRules(): Flow<LoyaltyRules> = dao.observeLoyalty().map { it.toRules() }

    override suspend fun getRules(): LoyaltyRules = dao.getLoyalty().toRules()

    override suspend fun saveRules(rules: LoyaltyRules) {
        dao.upsertLoyalty(
            LoyaltySettingsEntity(
                enabled = rules.enabled,
                spendPerPointMinor = rules.spendPerPoint.minorUnits,
                pointValueMinor = rules.pointValue.minorUnits,
            ),
        )
    }
}

/** No saved row means loyalty has never been set up: it is off. A row with unusable numbers also reads as off. */
internal fun LoyaltySettingsEntity?.toRules(): LoyaltyRules {
    if (this == null) return LoyaltyRules.OFF
    if (spendPerPointMinor <= 0 || pointValueMinor <= 0) return LoyaltyRules.OFF
    return LoyaltyRules(enabled, Money(spendPerPointMinor), Money(pointValueMinor))
}
