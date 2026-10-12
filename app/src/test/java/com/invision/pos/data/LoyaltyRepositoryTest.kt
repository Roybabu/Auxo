package com.invision.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.audit.AuditAction
import com.invision.pos.core.auth.FixedAuthorizer
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.PermissionDeniedException
import com.invision.pos.core.auth.Role
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.local.entity.LoyaltySettingsEntity
import com.invision.pos.data.repository.RoomLoyaltyRepository
import com.invision.pos.data.security.AuthorizedLoyaltyRepository
import com.invision.pos.domain.loyalty.LoyaltyRules
import com.invision.pos.domain.model.Money
import com.invision.pos.testutil.RecordingAudit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoyaltyRepositoryTest {

    private lateinit var database: InvisionDatabase
    private lateinit var inner: RoomLoyaltyRepository
    private val audit = RecordingAudit()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java).allowMainThreadQueries().build()
        inner = RoomLoyaltyRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private fun repository(role: Role, extras: Set<Permission> = emptySet()) =
        AuthorizedLoyaltyRepository(inner, FixedAuthorizer(role, extras), audit)

    private val rules = LoyaltyRules(true, Money(5_000), Money(50))

    @Test
    fun aNewShop_hasLoyaltyOff() = runTest {
        assertEquals(LoyaltyRules.OFF, inner.getRules())
        assertEquals(false, inner.observeRules().first().enabled)
    }

    @Test
    fun savedRules_areReadBack_andCanBeChanged() = runTest {
        inner.saveRules(rules)
        assertEquals(rules, inner.getRules())
        inner.saveRules(rules.copy(enabled = false))
        assertEquals(false, inner.getRules().enabled)
        assertEquals(Money(5_000), inner.getRules().spendPerPoint) // the numbers are kept while it is off
    }

    @Test
    fun aRowWithUnusableNumbers_readsAsOff() = runTest {
        database.customerDao().upsertLoyalty(LoyaltySettingsEntity(enabled = true, spendPerPointMinor = 0, pointValueMinor = 100))
        assertEquals(LoyaltyRules.OFF, inner.getRules())
    }

    @Test
    fun theSuperAdmin_savesRules_andItIsLogged() = runTest {
        repository(Role.SUPER_ADMIN).saveRules(rules)
        assertEquals(rules, inner.getRules())
        val line = audit.drafts.single()
        assertEquals(AuditAction.LOYALTY_CHANGED, line.action)
        assertTrue(line.summary.contains("on"))
        assertTrue(line.summary.contains("50.00"))
    }

    @Test
    fun anEmployee_canReadTheRules_butNeverChangeThem_evenWithEveryOption() = runTest {
        inner.saveRules(rules)
        val employee = repository(Role.EMPLOYEE, com.invision.pos.core.auth.PermissionOptions.all.toSet())
        assertEquals(rules, employee.getRules())
        assertEquals(rules, employee.observeRules().first())
        try {
            employee.saveRules(LoyaltyRules.OFF)
            fail("Expected a refusal")
        } catch (e: PermissionDeniedException) {
            assertEquals(Permission.MANAGE_LOYALTY, e.permission)
        }
        assertEquals(rules, inner.getRules())
        assertTrue(audit.drafts.isEmpty())
    }
}
