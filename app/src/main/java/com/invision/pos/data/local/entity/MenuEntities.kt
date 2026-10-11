package com.invision.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Restaurant menu, separate from shop products. Added in version 3. */
@Entity(tableName = "menu_items", indices = [Index("section")])
data class MenuItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val section: String,
    val description: String?,
    @ColumnInfo(name = "price_minor") val priceMinor: Long,
    @ColumnInfo(name = "gst_rate_bp") val gstRateBasisPoints: Int,
    @ColumnInfo(name = "sac_code") val sacCode: String?,
    @ColumnInfo(name = "diet_type") val dietType: String?,
    val available: Boolean,
    @ColumnInfo(name = "deduct_ingredients") val deductIngredients: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Ingredient used by a dish, per serving, in the ingredient's base unit. Added in version 3. */
@Entity(
    tableName = "recipe_lines",
    foreignKeys = [
        ForeignKey(
            entity = MenuItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["menu_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["menu_item_id", "variant_id"], unique = true), Index("variant_id")],
)
data class RecipeLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "menu_item_id") val menuItemId: Long,
    @ColumnInfo(name = "variant_id") val variantId: Long,
    val quantity: Int,
)

data class MenuItemWithRecipe(
    @Embedded val item: MenuItemEntity,
    @Relation(parentColumn = "id", entityColumn = "menu_item_id")
    val recipe: List<RecipeLineEntity>,
)
