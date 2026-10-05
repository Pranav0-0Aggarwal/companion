package app.companion.system

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import app.companion.data.MealItemRow
import app.companion.data.MealRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

class Burn(val steps: Long, val kcal: Double)

object Health {
    val perms = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    private fun client(c: Context): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(c) == HealthConnectClient.SDK_AVAILABLE) runCatching { HealthConnectClient.getOrCreate(c) }.getOrNull() else null

    fun available(c: Context) = HealthConnectClient.getSdkStatus(c) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(c: Context): Set<String> = try {
        client(c)?.permissionController?.getGrantedPermissions().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptySet()
    }

    private suspend fun <T> guarded(c: Context, need: String, f: suspend (HealthConnectClient) -> T): T? {
        val h = client(c) ?: return null
        return try {
            if (need !in h.permissionController.getGrantedPermissions()) null else f(h)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun type(slot: String) = when (slot) {
        "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
        "lunch" -> MealType.MEAL_TYPE_LUNCH
        "dinner" -> MealType.MEAL_TYPE_DINNER
        else -> MealType.MEAL_TYPE_SNACK
    }

    suspend fun meal(c: Context, m: MealRow, items: List<MealItemRow>): Boolean {
        if (items.isEmpty() || !FoodPrefs.health(c)) return false
        val zone = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(m.at))
        val at = Instant.ofEpochMilli(m.at)
        return guarded(c, HealthPermission.getWritePermission(NutritionRecord::class)) { h ->
            h.insertRecords(
                listOf(
                    NutritionRecord(
                        startTime = at,
                        startZoneOffset = zone,
                        endTime = at.plusSeconds(60),
                        endZoneOffset = zone,
                        energy = Energy.kilocalories(items.sumOf { it.kcal }),
                        protein = Mass.grams(items.sumOf { it.protein ?: 0.0 }),
                        totalCarbohydrate = Mass.grams(items.sumOf { it.carbs ?: 0.0 }),
                        totalFat = Mass.grams(items.sumOf { it.fat ?: 0.0 }),
                        mealType = type(m.slot),
                        metadata = Metadata.manualEntry("meal-${m.id}"),
                    ),
                ),
            )
            true
        } == true
    }

    suspend fun weight(c: Context, kg: Double, at: Long): Boolean {
        if (!FoodPrefs.health(c)) return false
        val t = Instant.ofEpochMilli(at)
        return guarded(c, HealthPermission.getWritePermission(WeightRecord::class)) { h ->
            h.insertRecords(listOf(WeightRecord(t, ZoneId.systemDefault().rules.getOffset(t), Mass.kilograms(kg), Metadata.manualEntry())))
            true
        } == true
    }

    suspend fun burn(c: Context, day: LocalDate): Burn? {
        if (!FoodPrefs.health(c)) return null
        val zone = ZoneId.systemDefault()
        val range = TimeRangeFilter.between(day.atStartOfDay(zone).toLocalDateTime(), day.plusDays(1).atStartOfDay(zone).toLocalDateTime())
        val need = HealthPermission.getReadPermission(StepsRecord::class)
        return guarded(c, need) { h ->
            val r = h.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range))
            Burn(r[StepsRecord.COUNT_TOTAL] ?: 0L, r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0)
        }
    }
}
