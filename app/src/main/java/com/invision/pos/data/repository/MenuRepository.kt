package com.invision.pos.data.repository

import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.local.toDomain
import com.invision.pos.data.local.toEntity
import com.invision.pos.domain.model.MenuItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

interface MenuRepository {
    /** Dishes by section, then sort order and name. */
    fun observeMenu(): Flow<List<MenuItem>>

    suspend fun getItem(menuItemId: Long): MenuItem?

    /** Saves a dish with its recipe and returns its id. */
    suspend fun saveItem(item: MenuItem): Long

    suspend fun setAvailable(menuItemId: Long, available: Boolean)

    /** Removes a dish. Past bills keep their copy of its name and price. */
    suspend fun deleteItem(menuItemId: Long)
}

class RoomMenuRepository(
    database: InvisionDatabase,
    private val clock: Clock,
) : MenuRepository {

    private val dao = database.menuDao()

    override fun observeMenu(): Flow<List<MenuItem>> = dao.observeMenu().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getItem(menuItemId: Long): MenuItem? = dao.getItem(menuItemId)?.toDomain()

    override suspend fun saveItem(item: MenuItem): Long {
        val now = clock.millis()
        val createdAt = if (item.id == 0L) now else dao.getItem(item.id)?.item?.createdAt
        return dao.saveItem(item.toEntity(now, createdAt), item.recipe.map { it.toEntity(item.id) })
    }

    override suspend fun setAvailable(menuItemId: Long, available: Boolean) {
        dao.setAvailable(menuItemId, available, clock.millis())
    }

    override suspend fun deleteItem(menuItemId: Long) {
        dao.deleteItem(menuItemId)
    }
}
