package com.invision.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.audit.AuditAction
import com.invision.pos.core.audit.RoomAuditTrail
import com.invision.pos.core.auth.Authorizer
import com.invision.pos.core.auth.FixedAuthorizer
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.PermissionDeniedException
import com.invision.pos.core.auth.Role
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.RoomStockCountRepository
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.data.security.AuthorizedInventoryRepository
import com.invision.pos.data.security.AuthorizedStockCountRepository
import com.invision.pos.domain.inventory.CountLine
import com.invision.pos.domain.inventory.CountReason
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.usecase.PostCountResult
import com.invision.pos.domain.usecase.PostStockCountUseCase
import com.invision.pos.testutil.DataIntegrity.count
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Stock counts on the real Room database: nothing changes until the count is posted, and posting is safe to repeat. */
@RunWith(AndroidJUnit4::class)
class StockCountDataTest {

    private lateinit var database: InvisionDatabase
    private val clock = TestData.clock

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private val admin = FixedAuthorizer(Role.SUPER_ADMIN)

    private fun inventory(auth: Authorizer = admin) =
        AuthorizedInventoryRepository(RoomInventoryRepository(database, clock), auth, RoomAuditTrail(database, clock, auth))

    private fun counts(auth: Authorizer = admin) =
        AuthorizedStockCountRepository(RoomStockCountRepository(database, clock), auth, RoomAuditTrail(database, clock, auth))

    private fun post(auth: Authorizer = admin) = PostStockCountUseCase(inventory(auth), counts(auth), clock)

    private suspend fun savedShirt(): Product {
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val id = (inventory().saveProduct(fresh) as SaveProductResult.Saved).productId
        return inventory().getProduct(id)!!
    }

    private suspend fun stockOf(variantId: Long): Int =
        inventory().observeProducts().first().flatMap { it.variants }.first { it.id == variantId }.stockQuantity

    private suspend fun auditActions() = RoomAuditTrail(database, clock, admin).observeRecent().first().map { it.action }

    @Test
    fun startingACount_makesOneOpenCount_andStartingAgainReturnsIt() = runTest {
        val first = counts().start()
        assertTrue(first.lines.isEmpty())
        assertEquals(first.id, counts().start().id)
        assertEquals(first.id, counts().observeOpen().first()!!.id)
        assertEquals(1, count(database, "stock_counts"))
    }

    @Test
    fun countedLines_areSaved_replacedAndRemoved_withoutTouchingStock() = runTest {
        val shirt = savedShirt()
        val m = shirt.variants[0].id
        val l = shirt.variants[1].id
        val movementsBefore = count(database, "stock_movements")
        val open = counts().start()

        assertTrue(counts().setLine(open.id, CountLine(m, 8, 5)))
        assertTrue(counts().setLine(open.id, CountLine(l, 1, 1)))
        assertTrue(counts().setLine(open.id, CountLine(m, 8, 6))) // counted again
        assertEquals(setOf(CountLine(m, 8, 6), CountLine(l, 1, 1)), counts().observeOpen().first()!!.lines.toSet())
        assertTrue(counts().removeLine(open.id, l))
        assertEquals(listOf(CountLine(m, 8, 6)), counts().observeOpen().first()!!.lines)

        assertEquals(8, stockOf(m))
        assertEquals(movementsBefore, count(database, "stock_movements"))
    }

    @Test
    fun cancellingACount_changesNoStock_andAllowsANewOne() = runTest {
        val shirt = savedShirt()
        val m = shirt.variants[0].id
        val open = counts().start()
        counts().setLine(open.id, CountLine(m, 8, 1))
        counts().cancel(open.id)

        assertNull(counts().observeOpen().first())
        assertEquals(8, stockOf(m))
        assertFalse(counts().setLine(open.id, CountLine(m, 8, 2))) // a cancelled count can't be edited
        assertTrue(counts().start().id != open.id)
        assertTrue(auditActions().containsAll(listOf(AuditAction.STOCK_COUNT_STARTED.name, AuditAction.STOCK_COUNT_CANCELLED.name)))
    }

    @Test
    fun posting_adjustsEachDifference_asACorrection_andClosesTheCount() = runTest {
        val shirt = savedShirt()
        val m = shirt.variants[0].id
        val l = shirt.variants[1].id
        val open = counts().start()
        counts().setLine(open.id, CountLine(m, 8, 5)) // 3 short
        counts().setLine(open.id, CountLine(l, 1, 4)) // 3 extra
        val current = counts().observeOpen().first()!!

        val result = post().invoke(current, CountReason.YEAR_END, "Godown 2")

        assertEquals(PostCountResult.Posted(adjusted = 2, matched = 0), result)
        assertEquals(5, stockOf(m))
        assertEquals(4, stockOf(l))
        assertNull(counts().observeOpen().first())
        val corrections = inventory().observeMovementHistory(0, Long.MAX_VALUE).first().filter { it.movement.reason == StockChangeReason.CORRECTION }
        assertEquals(setOf(-3, 3), corrections.map { it.movement.quantityChange }.toSet())
        assertTrue(corrections.all { it.movement.note == "Stock count · Year-end count · Godown 2" })
        val actions = auditActions()
        assertEquals(2, actions.count { it == AuditAction.STOCK_CHANGED.name })
        assertEquals(1, actions.count { it == AuditAction.STOCK_COUNT_POSTED.name })
    }

    @Test
    fun itemsThatMatch_changeNothing() = runTest {
        val shirt = savedShirt()
        val m = shirt.variants[0].id
        val open = counts().start()
        counts().setLine(open.id, CountLine(m, 8, 8))
        val movementsBefore = count(database, "stock_movements")

        val result = post().invoke(counts().observeOpen().first()!!, CountReason.ROUTINE, "")

        assertEquals(PostCountResult.Posted(adjusted = 0, matched = 1), result)
        assertEquals(movementsBefore, count(database, "stock_movements"))
        assertEquals(8, stockOf(m))
    }

    @Test
    fun salesMadeWhileCounting_arePreserved() = runTest {
        val shirt = savedShirt()
        val m = shirt.variants[0].id
        val open = counts().start()
        // Counted 6 on the shelf while the app said 8: two missing.
        counts().setLine(open.id, CountLine(m, 8, 6))
        // Before posting, one more is sold, so the app now says 7.
        inventory().recordStockChange(StockMovement(variantId = m, quantityChange = -1, reason = StockChangeReason.SOLD, createdAtMillis = clock.millis()))
        assertEquals(7, stockOf(m))

        post().invoke(counts().observeOpen().first()!!, CountReason.ROUTINE, "")

        // The two that were missing are removed from what is there now; the sold one is not taken off twice.
        assertEquals(5, stockOf(m))
    }

    @Test
    fun postingTwice_neverAdjustsTwice() = runTest {
        val shirt = savedShirt()
        val m = shirt.variants[0].id
        val open = counts().start()
        counts().setLine(open.id, CountLine(m, 8, 5))
        val snapshot = counts().observeOpen().first()!!

        assertTrue(post().invoke(snapshot, CountReason.ROUTINE, "") is PostCountResult.Posted)
        // A retry with the same count (a second tap, a restart) finds it closed and changes nothing.
        assertEquals(PostCountResult.NotOpen, post().invoke(snapshot, CountReason.ROUTINE, ""))
        assertEquals(5, stockOf(m))
        assertEquals(1, auditActions().count { it == AuditAction.STOCK_COUNT_POSTED.name })
    }

    @Test
    fun ifStockFellBelowTheDifference_thatItemStaysInTheCount_andTheRestArePosted() = runTest {
        val shirt = savedShirt()
        val m = shirt.variants[0].id
        val l = shirt.variants[1].id
        val open = counts().start()
        counts().setLine(open.id, CountLine(m, expected = 8, counted = 5)) // 3 short
        counts().setLine(open.id, CountLine(l, expected = 1, counted = 4)) // 3 extra
        // Seven are sold before posting, so only one is left and three can no longer be taken off.
        inventory().recordStockChange(StockMovement(variantId = m, quantityChange = -7, reason = StockChangeReason.SOLD, createdAtMillis = clock.millis()))

        val partial = post().invoke(counts().observeOpen().first()!!, CountReason.ROUTINE, "")

        assertEquals(PostCountResult.Partial(adjusted = 1, failed = listOf(m)), partial)
        assertEquals(4, stockOf(l))
        assertEquals(1, stockOf(m))
        val stillOpen = counts().observeOpen().first()!!
        assertEquals(listOf(m), stillOpen.lines.map { it.variantId }) // only the item to check again
        assertEquals(0, auditActions().count { it == AuditAction.STOCK_COUNT_POSTED.name })

        // Recounting it finishes the count.
        counts().setLine(stillOpen.id, CountLine(m, expected = 1, counted = 0))
        assertEquals(PostCountResult.Posted(adjusted = 1, matched = 0), post().invoke(counts().observeOpen().first()!!, CountReason.ROUTINE, ""))
        assertEquals(0, stockOf(m))
        assertNull(counts().observeOpen().first())
    }

    @Test
    fun onlyThoseWhoMayAdjustStock_canCount() = runTest {
        val employee = FixedAuthorizer(Role.EMPLOYEE)
        denied(Permission.ADJUST_STOCK) { counts(employee).start() }
        denied(Permission.ADJUST_STOCK) { counts(employee).observeOpen().first() }
        denied(Permission.ADJUST_STOCK) { counts(employee).setLine(1, CountLine(1, 1, 1)) }
        denied(Permission.ADJUST_STOCK) { counts(employee).removeLine(1, 1) }
        denied(Permission.ADJUST_STOCK) { counts(employee).cancel(1) }
        denied(Permission.ADJUST_STOCK) { counts(employee).markPosted(1, CountReason.ROUTINE, "", 0) }
        denied(Permission.ADJUST_STOCK) { counts(FixedAuthorizer(null)).start() }
        assertEquals(0, count(database, "stock_counts"))

        // An Employee the Super Admin allowed to adjust stock can count.
        val allowed = FixedAuthorizer(Role.EMPLOYEE, setOf(Permission.ADJUST_STOCK))
        assertEquals(1L, counts(allowed).start().id)
    }

    private suspend fun denied(permission: Permission, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $permission to be required")
        } catch (e: PermissionDeniedException) {
            assertEquals(permission, e.permission)
        }
    }
}
