package app.companion.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "meals", indices = [Index("day")])
data class MealRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val slot: String,
    val at: Long,
    val src: String,
    val note: String? = null,
)

@Entity(
    tableName = "meal_items",
    foreignKeys = [ForeignKey(MealRow::class, ["id"], ["meal_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("meal_id")],
)
data class MealItemRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_id") val mealId: Long,
    val name: String,
    val brand: String? = null,
    val qty: Double,
    val unit: String,
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val source: String,
    val conf: Double,
)

@Entity(tableName = "skus")
data class SkuRow(
    @PrimaryKey val key: String,
    val brand: String? = null,
    val name: String,
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val per: String = "serving",
    val updated: Long,
)

@Entity(tableName = "weights")
data class WeightRow(@PrimaryKey val day: Long, val kg: Double, val at: Long)

@Entity(tableName = "trips")
data class TripRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val start: Long,
    val end: Long,
    val budget: Long? = null,
    val currency: String? = null,
    val active: Boolean = false,
)

@Entity(
    tableName = "trip_items",
    primaryKeys = ["trip_id", "item_id"],
    foreignKeys = [
        ForeignKey(TripRow::class, ["id"], ["trip_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Item::class, ["id"], ["item_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("item_id")],
)
data class TripItemRow(
    @ColumnInfo(name = "trip_id") val tripId: Long,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val share: Double = 1.0,
    val inr: Long? = null,
)

@Entity(tableName = "docs", indices = [Index("expires")])
class DocRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val fields: ByteArray,
    val expires: Long? = null,
    val photo: String? = null,
    val src: String,
    val at: Long,
    val mask: String? = null,
    val sent: String? = null,
)

@Entity(tableName = "replies")
data class ReplyRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: String,
    val ref: Long? = null,
    val slot: String? = null,
    val text: String,
)
