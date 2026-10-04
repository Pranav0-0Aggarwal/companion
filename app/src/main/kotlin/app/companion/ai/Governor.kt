package app.companion.ai

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface Runner : AutoCloseable {
    val accel: String
    val alive: Boolean get() = true
    val mb: Long get() = -1
}

class LowMemory : Exception()

data class Live(val name: String, val accel: String, val mb: Long)

class Governor(private val app: Application) {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var spec: Spec? = null
    private var runner: Runner? = null
    private var idle: Job? = null
    val live = MutableStateFlow<Live?>(null)

    private fun pss() = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024L

    private fun roomy(): Boolean {
        val i = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(i)
        return !i.lowMemory && i.availMem >= 1_500L * 1024 * 1024
    }

    private fun drop() {
        runner?.close()
        runner = null
        spec = null
        live.value = null
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <R : Runner, T> run(s: Spec, make: () -> R, block: (R) -> T): T = lock.withLock {
        idle?.cancel()
        if (spec != s || runner?.alive == false) {
            drop()
            if (!roomy()) throw LowMemory()
            val before = pss()
            val r = make()
            runner = r
            spec = s
            live.value = Live(s.name, r.accel, r.mb.takeIf { it >= 0 } ?: (pss() - before).coerceAtLeast(0))
        }
        try {
            block(runner as R)
        } finally {
            idle = scope.launch {
                delay(s.idleMs)
                lock.withLock { if (spec == s) drop() }
            }
        }
    }

    fun trim(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            idle?.cancel()
            scope.launch { lock.withLock { drop() } }
        }
    }

    fun rssMb() = pss()
}
