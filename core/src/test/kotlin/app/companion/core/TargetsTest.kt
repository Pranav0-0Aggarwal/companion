package app.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TargetsTest {
    @Test
    fun mifflinWithActivity() {
        assertEquals(2630, Targets.kcal(75.0, 175, 30, "male", "moderate"))
        assertEquals(1820, Targets.kcal(60.0, 165, 30, "female", "light"))
    }

    @Test
    fun defaultsToLightAndMale() {
        assertEquals(Targets.kcal(70.0, 170, 40, "m", "light"), Targets.kcal(70.0, 170, 40, null, null))
    }

    @Test
    fun rejectsOutOfRange() {
        assertNull(Targets.kcal(10.0, 170, 30, null, null))
        assertNull(Targets.kcal(70.0, 20, 30, null, null))
        assertNull(Targets.kcal(70.0, 170, 3, null, null))
    }

    @Test
    fun proteinFromWeight() {
        assertEquals(84, Targets.protein(70.0))
    }
}
