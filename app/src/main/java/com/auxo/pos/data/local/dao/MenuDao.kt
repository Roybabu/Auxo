package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.auxo.pos.data.local.entity.MenuItemEntity
import com.auxo.pos.data.local.entity.MenuItemWithRecipe
import com.auxo.pos.data.local.entity.RecipeLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MenuDao {

    @Transaction
    @Query("SELECT * FROM menu_items ORDER BY section COLLATE NOCASE, sort_order, name COLLATE NOCASE")
    abstract fun observeMenu(): Flow<List<MenuItemWithRecipe>>

    @Transaction
    @Query("SELECT * FROM menu_items WHERE id = :menuItemId")
    abstract suspend fun getItem(menuItemId: Long): MenuItemWithRecipe?

    @Transaction
    @Query("SELECT * FROM menu_items WHERE id IN (:menuItemIds)")
    abstract suspend fun getItems(menuItemIds: List<Long>): List<MenuItemWithRecipe>

    @Insert
    abstract suspend fun insertItem(item: MenuItemEntity): Long

    @Update
    abstract suspend fun updateItem(item: MenuItemEntity)

    @Query("UPDATE menu_items SET available = :available, updated_at = :nowMillis WHERE id = :menuItemId")
    abstract suspend fun setAvailable(menuItemId: Long, available: Boolean, nowMillis: Long)

    @Query("DELETE FROM menu_items WHERE id = :menuItemId")
    abstract suspend fun deleteItem(menuItemId: Long)

    @Insert
    abstract suspend fun insertRecipe(lines: List<RecipeLineEntity>)

    @Query("DELETE FROM recipe_lines WHERE menu_item_id = :menuItemId")
    abstract suspend fun deleteRecipe(menuItemId: Long)

    /** Saves a dish and replaces its recipe in one transaction. Returns the dish id. */
    @Transaction
    open suspend fun saveItem(item: MenuItemEntity, recipe: List<RecipeLineEntity>): Long {
        val id = if (item.id == 0L) {
            insertItem(item)
        } else {
            updateItem(item)
            deleteRecipe(item.id)
            item.id
        }
        if (recipe.isNotEmpty()) insertRecipe(recipe.map { it.copy(id = 0, menuItemId = id) })
        return id
    }
}
