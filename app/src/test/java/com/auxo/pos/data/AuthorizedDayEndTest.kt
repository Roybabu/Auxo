package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.audit.AuditAction
import com.auxo.pos.core.auth.FixedAuthorizer
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.PermissionDeniedException
import com.auxo.pos.core.auth.Role
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.CloseDayResult
import com.auxo.pos.data.repository.RoomDayEndRepository
import com.auxo.pos.data.security.AuthorizedDayEndRepository
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.RecordingAudit
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AuthorizedDayEndTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inner: RoomDayEndRepository
    private val audit = RecordingAudit()
    private val today: LocalDate get() = LocalDate.now(TestData.clock)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inner = RoomDayEndRepository(database, TestData.clock)
    }

    @After
    fun tearDown() = database.close()

    private fun repository(role: Role, extras: Set<Permission> = emptySet()) =
        AuthorizedDayEndRepository(inner, FixedAuthorizer(role, extras), audit)

    @Test
    fun theSuperAdmin_canCloseTheDay_andItIsLogged() = runTest {
        val result = repository(Role.SUPER_ADMIN).closeDay(today, Money(5_000), null, "ignored") as CloseDayResult.Closed
        assertEquals("Test Admin", result.closing.closedBy) // the signed-in person, not what the caller passed
        val line = audit.drafts.single()
        assertEquals(AuditAction.DAY_CLOSED, line.action)
        assertEquals(result.closing.id, line.entityId)
        assertTrue(line.summary.contains("counted 50.00"))
        assertTrue(line.summary.contains("difference 50.00"))
    }

    @Test
    fun anEmployeeGivenTheOption_canCloseTheDay() = runTest {
        val result = repository(Role.EMPLOYEE, setOf(Permission.CLOSE_DAY)).closeDay(today, Money.ZERO, null, "x")
        assertTrue(result is CloseDayResult.Closed)
        assertEquals("Test Employee", (result as CloseDayResult.Closed).closing.closedBy)
    }

    @Test
    fun anEmployeeWithoutTheOption_isRefusedEverywhere_andNothingIsSaved() = runTest {
        val repo = repository(Role.EMPLOYEE)
        assertDenied { repo.closeDay(today, Money.ZERO, null, "x") }
        assertDenied { repo.figuresFor(today) }
        assertDenied { repo.closingFor(today) }
        assertDenied { repo.getClosing(1) }
        assertDenied { repo.observeClosings().first() }
        assertTrue(inner.observeClosings().first().isEmpty())
        assertTrue(audit.drafts.isEmpty())
    }

    @Test
    fun aRefusedSecondClosing_isNotLoggedAgain() = runTest {
        val repo = repository(Role.SUPER_ADMIN)
        repo.closeDay(today, Money.ZERO, null, "x")
        repo.closeDay(today, Money(1_000), null, "x")
        assertEquals(1, audit.drafts.size)
    }

    private suspend fun assertDenied(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected the call to be refused")
        } catch (e: PermissionDeniedException) {
            assertEquals(Permission.CLOSE_DAY, e.permission)
        }
    }
}
