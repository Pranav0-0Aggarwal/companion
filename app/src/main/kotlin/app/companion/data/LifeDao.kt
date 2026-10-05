package app.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class SlotAt(val slot: String, val at: Long)

@Dao
interface LifeDao {
    @Insert
    suspend fun addMeal(m: MealRow): Long

    @Update
    suspend fun updateMeal(m: MealRow)

    @Insert
    suspend fun addMealItems(i: List<MealItemRow>)

    @Query("DELETE FROM meal_items WHERE meal_id = :id")
    suspend fun clearMeal(id: Long)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun dropMeal(id: Long)

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun meal(id: Long): MealRow?

    @Query("SELECT * FROM meals WHERE day = :day ORDER BY at")
    suspend fun mealsOn(day: Long): List<MealRow>

    @Query("SELECT * FROM meals WHERE day BETWEEN :a AND :b ORDER BY at")
    fun mealsBetween(a: Long, b: Long): Flow<List<MealRow>>

    @Query("SELECT * FROM meal_items WHERE meal_id IN (:ids) ORDER BY id")
    suspend fun mealItems(ids: List<Long>): List<MealItemRow>

    @Query("SELECT * FROM meal_items WHERE meal_id IN (SELECT id FROM meals WHERE day BETWEEN :a AND :b) ORDER BY id")
    fun itemsBetween(a: Long, b: Long): Flow<List<MealItemRow>>

    @Query("SELECT slot, at FROM meals WHERE at >= :since AND (src != 'order' OR EXISTS (SELECT 1 FROM meal_items WHERE meal_id = meals.id))")
    suspend fun mealTimes(since: Long): List<SlotAt>

    @Query("SELECT at FROM items WHERE kind IN ('Debit', 'CardSpend') AND category = 'food' AND dup IS NULL AND at BETWEEN :lo AND :hi ORDER BY at")
    suspend fun foodPaid(lo: Long, hi: Long): List<Long>

    @Query("SELECT * FROM meals WHERE src = 'order' AND at = :at LIMIT 1")
    suspend fun orderAt(at: Long): MealRow?

    @Query("SELECT * FROM meals WHERE src = 'order' AND NOT EXISTS (SELECT 1 FROM meal_items WHERE meal_id = meals.id) ORDER BY at DESC")
    suspend fun bareOrders(): List<MealRow>

    @Upsert
    suspend fun putSku(s: SkuRow)

    @Query("SELECT * FROM skus")
    suspend fun skus(): List<SkuRow>

    @Query("DELETE FROM skus WHERE `key` = :key")
    suspend fun dropSku(key: String)

    @Upsert
    suspend fun putWeight(w: WeightRow)

    @Query("SELECT * FROM weights ORDER BY day DESC LIMIT :n")
    suspend fun weights(n: Int): List<WeightRow>

    @Query("SELECT * FROM weights ORDER BY day DESC LIMIT :n")
    fun weightFlow(n: Int): Flow<List<WeightRow>>

    @Insert
    suspend fun addTrip(t: TripRow): Long

    @Update
    suspend fun updateTrip(t: TripRow)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun trip(id: Long): TripRow?

    @Query("SELECT * FROM trips ORDER BY start DESC")
    fun trips(): Flow<List<TripRow>>

    @Query("SELECT * FROM trips ORDER BY start DESC")
    suspend fun tripsNow(): List<TripRow>

    @Query("SELECT * FROM trips WHERE active = 1 ORDER BY start DESC LIMIT 1")
    suspend fun activeTrip(): TripRow?

    @Query("SELECT * FROM trips WHERE name LIKE '%' || :name || '%' ORDER BY start DESC LIMIT 1")
    suspend fun tripNamed(name: String): TripRow?

    @Query("UPDATE trips SET active = 0 WHERE active = 1")
    suspend fun deactivate()

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun dropTrip(id: Long)

    @Upsert
    suspend fun tag(t: TripItemRow)

    @Query("DELETE FROM trip_items WHERE trip_id = :trip AND item_id = :item")
    suspend fun untag(trip: Long, item: Long)

    @Query("SELECT * FROM trip_items WHERE trip_id = :trip")
    suspend fun shares(trip: Long): List<TripItemRow>

    @Query("SELECT items.* FROM items JOIN trip_items ON trip_items.item_id = items.id WHERE trip_items.trip_id = :trip ORDER BY items.at")
    suspend fun tripItems(trip: Long): List<Item>

    @Query(
        "SELECT * FROM items WHERE kind IN ('Debit', 'CardSpend') AND dup IS NULL AND at BETWEEN :lo AND :hi " +
            "AND id NOT IN (SELECT item_id FROM trip_items) ORDER BY at",
    )
    suspend fun untagged(lo: Long, hi: Long): List<Item>

    @Query("SELECT * FROM items WHERE kind = 'Travel' AND at >= :since ORDER BY at")
    suspend fun travel(since: Long): List<Item>

    @Insert
    suspend fun addDoc(d: DocRow): Long

    @Update
    suspend fun updateDoc(d: DocRow)

    @Query("SELECT * FROM docs WHERE id = :id")
    suspend fun doc(id: Long): DocRow?

    @Query("SELECT * FROM docs ORDER BY expires IS NULL, expires, at DESC")
    fun docs(): Flow<List<DocRow>>

    @Query("SELECT * FROM docs ORDER BY expires IS NULL, expires, at DESC")
    suspend fun docsNow(): List<DocRow>

    @Query("SELECT * FROM docs WHERE expires IS NOT NULL ORDER BY expires")
    suspend fun expiring(): List<DocRow>

    @Query("DELETE FROM docs WHERE id = :id")
    suspend fun dropDoc(id: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addReply(r: ReplyRow): Long

    @Query("SELECT * FROM replies ORDER BY id")
    suspend fun replies(): List<ReplyRow>

    @Query("DELETE FROM replies WHERE id = :id")
    suspend fun dropReply(id: Long)
}
