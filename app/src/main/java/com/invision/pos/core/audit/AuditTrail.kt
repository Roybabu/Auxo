package com.invision.pos.core.audit

import androidx.room.withTransaction
import com.invision.pos.core.auth.Authorizer
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.Role
import com.invision.pos.core.auth.SessionUser
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.local.entity.AuditLogEntity
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** What happened. Stored as text, so a name can be added later without changing the database. */
enum class AuditAction {
    SIGNED_IN,
    SIGNED_OUT,
    EMPLOYEE_CREATED,
    EMPLOYEE_TURNED_ON,
    EMPLOYEE_TURNED_OFF,
    PASSWORD_RESET,
    EMPLOYEE_ACCESS_CHANGED,
    SALE_RECORDED,
    RETURN_RECORDED,
    PRODUCT_SAVED,
    PRODUCT_DELETED,
    STOCK_CHANGED,
    STOCK_WRITTEN_OFF,
    STOCK_COUNT_STARTED,
    STOCK_COUNT_POSTED,
    STOCK_COUNT_CANCELLED,
    CUSTOMER_SAVED,
    ACCOUNT_PAYMENT_RECEIVED,
    SUPPLIER_SAVED,
    ORDER_SAVED,
    ORDER_STATUS_CHANGED,
    GOODS_RECEIVED,
    MENU_ITEM_SAVED,
    MENU_ITEM_AVAILABILITY_CHANGED,
    MENU_ITEM_DELETED,
    SETTINGS_SAVED,
    BACKUP_CREATED,
    AUTO_BACKUP_SAVED,
    AUTO_BACKUP_FAILED,
    AUTO_BACKUP_SETTINGS_CHANGED,
    DAY_CLOSED,
    LOYALTY_CHANGED,
    RECOVERY_CODE_SET,
    PASSWORD_RESET_WITH_CODE,
    PIN_CHANGED,
    FINGERPRINT_CHANGED,
    AUTO_LOCK_CHANGED,
    PIN_LOCKED_OUT,
}

/** The record an action touched, as a short type name and an id. */
object AuditEntity {
    const val USER = "user"
    const val INVOICE = "invoice"
    const val PRODUCT = "product"
    const val VARIANT = "variant"
    const val BATCH = "batch"
    const val STOCK_COUNT = "stock_count"
    const val CUSTOMER = "customer"
    const val SUPPLIER = "supplier"
    const val PURCHASE_ORDER = "purchase_order"
    const val PURCHASE_RECEIPT = "purchase_receipt"
    const val MENU_ITEM = "menu_item"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val DAY_CLOSING = "day_closing"
    const val LOYALTY = "loyalty"
}

/** What to write down; the time and the person are added when it is saved. */
data class AuditDraft(
    val action: AuditAction,
    val entityType: String,
    val entityId: Long?,
    val summary: String,
)

data class AuditRecord(
    val id: Long,
    val createdAtMillis: Long,
    val actorId: Long,
    val actorName: String,
    val actorRole: Role?,
    val action: String,
    val entityType: String,
    val entityId: Long?,
    val summary: String,
)

/**
 * The activity log. It is kept in the same database as the records it describes, and [recording] writes the
 * log line in the same transaction as the change, so a change cannot be saved without its line (or the line
 * without the change).
 *
 * This is a record for the shop owner, not tamper-proof evidence: anyone with full access to the device's
 * storage can edit the database, and a restore from a backup brings back the log as it was at that backup.
 */
interface AuditTrail {
    /** Runs [block]; if [describe] returns a draft for its result, saves that line in the same transaction. */
    suspend fun <T> recording(block: suspend () -> T, describe: (T) -> AuditDraft?): T

    /** Saves one line on its own, for events that are not part of a database change. */
    suspend fun record(draft: AuditDraft, actor: SessionUser? = null)

    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<AuditRecord>>

    companion object {
        const val DEFAULT_LIMIT = 300
    }
}

class RoomAuditTrail(
    private val database: InvisionDatabase,
    private val clock: Clock,
    private val actor: Authorizer,
) : AuditTrail {

    private val dao = database.auditDao()

    override suspend fun <T> recording(block: suspend () -> T, describe: (T) -> AuditDraft?): T =
        database.withTransaction {
            val result = block()
            describe(result)?.let { insert(it) }
            result
        }

    override suspend fun record(draft: AuditDraft, actor: SessionUser?) {
        insert(draft, actor)
    }

    private suspend fun insert(draft: AuditDraft, namedActor: SessionUser? = null) {
        val user = namedActor ?: actor.user
        dao.insert(
            AuditLogEntity(
                createdAt = clock.millis(),
                actorId = user?.id ?: 0L,
                actorName = user?.name ?: UNKNOWN_ACTOR,
                actorRole = user?.role?.name.orEmpty(),
                action = draft.action.name,
                entityType = draft.entityType,
                entityId = draft.entityId,
                summary = draft.summary.take(SUMMARY_LIMIT),
            ),
        )
    }

    override fun observeRecent(limit: Int): Flow<List<AuditRecord>> = flow {
        // Only a person who may read the log gets the flow to start.
        actor.require(Permission.VIEW_ACTIVITY_LOG)
        emitAll(
            dao.observeRecent(limit).map { rows ->
            rows.map {
                AuditRecord(
                    id = it.id,
                    createdAtMillis = it.createdAt,
                    actorId = it.actorId,
                    actorName = it.actorName,
                    actorRole = runCatching { Role.valueOf(it.actorRole) }.getOrNull(),
                    action = it.action,
                    entityType = it.entityType,
                    entityId = it.entityId,
                    summary = it.summary,
                )
            }
        },
        )
    }

    private companion object {
        const val UNKNOWN_ACTOR = "Unknown"
        const val SUMMARY_LIMIT = 300
    }
}
