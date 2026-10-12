package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.entity.CustomerEntity
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.repository.RoomCustomerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.auxo.pos.testutil.TestData

@RunWith(AndroidJUnit4::class)
class OwingAgeRepositoryTest {

    private lateinit var database: AuxoDatabase
    private lateinit var customers: RoomCustomerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        customers = RoomCustomerRepository(database, TestData.clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun entry(customerId: Long, type: String, amount: Long, at: Long, points: Int = 0) {
        database.customerDao().insertEntry(
            LedgerEntryEntity(customerId = customerId, type = type, amountMinor = amount, invoiceId = null, method = null, reference = null, note = null, createdAt = at, points = points),
        )
    }

    @Test
    fun accounts_carryTheDateOfTheOldestUnpaidCharge() = runTest {
        val dao = database.customerDao()
        val asha = dao.insertCustomer(CustomerEntity(name = "Asha", phone = "9830012345", email = null, gstin = null, stateCode = null, address = null, creditLimitMinor = null, notes = null, createdAt = 1, updatedAt = 1))
        val bimal = dao.insertCustomer(CustomerEntity(name = "Bimal", phone = "9830054321", email = null, gstin = null, stateCode = null, address = null, creditLimitMinor = null, notes = null, createdAt = 1, updatedAt = 1))
        entry(asha, "SALE", 100_00, 10)
        entry(asha, "SALE", 200_00, 20)
        entry(asha, "PAYMENT", -100_00, 30)
        entry(asha, "POINTS_EARNED", 0, 31, points = 5)
        entry(bimal, "SALE", 100_00, 10)
        entry(bimal, "PAYMENT", -100_00, 20)

        val accounts = customers.observeAccounts().first().associateBy { it.customer.name }
        assertEquals(20L, accounts.getValue("Asha").owingSinceMillis)
        assertNull(accounts.getValue("Bimal").owingSinceMillis)
        assertEquals(200_00L, accounts.getValue("Asha").balance.minorUnits)
    }
}
