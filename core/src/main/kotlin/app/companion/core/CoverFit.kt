package app.companion.core

enum class Focus { Out, Code, Meet, Due, Spent }

object Now {
    fun focus(out: Boolean, code: Boolean, meet: Boolean, dueDays: Int?, spent: Int): Focus = when {
        out -> Focus.Out
        code -> Focus.Code
        meet -> Focus.Meet
        dueDays != null && dueDays <= 0 -> Focus.Due
        else -> Focus.Spent
    }
}

object Reveal {
    fun of(appLock: Boolean, unlocked: Boolean, deviceLocked: Boolean, details: Boolean) = (!appLock || unlocked) && (!deviceLocked || details)
}

object Fit {
    const val W = 480
    const val H = 600

    fun cover(wDp: Int, hDp: Int) = wDp <= W && hDp <= H
}
