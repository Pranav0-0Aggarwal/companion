package app.companion.core

import kotlin.math.roundToInt

object Targets {
    val levels = mapOf("sedentary" to 1.2, "light" to 1.375, "moderate" to 1.55, "active" to 1.725)

    fun kcal(kg: Double, cm: Int, age: Int, sex: String?, activity: String?): Int? {
        if (kg !in 25.0..300.0 || cm !in 100..250 || age !in 10..110) return null
        val male = sex?.trim()?.lowercase()?.startsWith("f") != true
        val bmr = 10 * kg + 6.25 * cm - 5 * age + if (male) 5 else -161
        return ((bmr * (levels[activity?.lowercase()] ?: levels.getValue("light")) / 10).roundToInt() * 10)
    }

    fun protein(kg: Double): Int = (kg * 1.2).roundToInt()
}
