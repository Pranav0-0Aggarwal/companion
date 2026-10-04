package app.companion.ai

import app.companion.core.Cpu
import java.io.File

object Chip {
    val nux by lazy { runCatching { Cpu.nux(File("/proc/cpuinfo").readText()) }.getOrDefault(false) }
}
