package app.companion.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.companion.core.Eat
import app.companion.core.Meal
import app.companion.core.MealTimes
import app.companion.core.Nudge
import app.companion.core.WeightNudge
import app.companion.sl
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

object Nudges {
    private const val FOOD = "nudge"
    private const val WEIGH = "weigh"
    private const val MIN = 60_000L
    private const val RETRY = 6 * 3_600_000L

    fun schedule(c: Context, wait: Long = 0) {
        val req = OneTimeWorkRequestBuilder<NudgeWork>().setInitialDelay(wait, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(c).enqueueUniqueWork(FOOD, ExistingWorkPolicy.REPLACE, req)
    }

    fun weigh(c: Context, wait: Long = 0) {
        val req = OneTimeWorkRequestBuilder<WeightWork>().setInitialDelay(wait, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(c).enqueueUniqueWork(WEIGH, ExistingWorkPolicy.REPLACE, req)
    }

    fun boot(c: Context) {
        schedule(c)
        weigh(c)
    }

    internal fun gap(next: Long?, now: Long) = ((next ?: (now + RETRY)) - now).coerceAtLeast(MIN)
}

class NudgeWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val repo = c.sl.repo
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val day = today.toEpochDay()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val times = MealTimes.learn(repo.life.samples(now), now, zone)
        val logged = repo.life.eaten(today).filter { it.items.isNotEmpty() }.mapNotNull { e -> Meal.entries.firstOrNull { it.label == e.meal.slot }?.let { Eat.valueOf(it.name) } }.toSet()
        val paid = repo.life.foodPaid(start, now)
        val on = FoodPrefs.slots(c)
        val quiet = FoodPrefs.quiet(c)
        var nudged = FoodPrefs.nudged(c, day)
        try {
            val due = Nudge.due(now, zone, times, logged, paid, nudged, on, quiet)
            if (due != null) {
                FoodNotes.slot(c, due.name)
                FoodPrefs.nudge(c, day, due)
                nudged = nudged + due
            }
        } finally {
            Nudges.schedule(c, Nudges.gap(Nudge.next(now, zone, times, logged, paid, nudged, on, quiet), now))
        }
        return Result.success()
    }
}

class WeightWork(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        try {
            val week = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
            val last = c.sl.repo.life.weights(1).firstOrNull()?.at
            if (FoodPrefs.weight(c) && WeightNudge.due(now, zone, last, FoodPrefs.weighed(c) >= week, quiet = FoodPrefs.quiet(c))) {
                FoodNotes.weigh(c)
                FoodPrefs.weighed(c, LocalDate.now(zone).toEpochDay())
            }
        } finally {
            Nudges.weigh(c, Nudges.gap(WeightNudge.next(now, zone, quiet = FoodPrefs.quiet(c)), now))
        }
        return Result.success()
    }
}
