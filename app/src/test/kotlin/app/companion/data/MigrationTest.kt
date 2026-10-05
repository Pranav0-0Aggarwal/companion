package app.companion.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.companion.core.Json

class MigrationTest {
    private val fresh = setOf("meals", "meal_items", "skus", "weights", "trips", "trip_items", "docs", "replies")
    private val old = "id first call city payDay budget sms notif mail wa ig lock done vip hist imported cal gtasks since keep model".split(' ')

    private fun ran(): List<String> {
        val out = mutableListOf<String>()
        val db = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, m, a ->
            if (m.name == "execSQL") out.add(a!![0] as String)
            null
        } as SupportSQLiteDatabase
        Migrate8to9.migrate(db)
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun entities() = (Json.obj(File("schemas/app.companion.data.Db/9.json").readText())["database"] as Map<String, Any?>)
        .let { (it["entities"] as List<Map<String, Any?>>).associateBy { e -> e["tableName"] as String } }

    @Test
    fun newTablesAndIndicesMatchEntities() {
        val sql = ran().toSet()
        val e = entities()
        fresh.forEach { t ->
            val m = e.getValue(t)
            assertTrue((m["createSql"] as String).replace("\${TABLE_NAME}", t) in sql, t)
            (m["indices"] as? List<*> ?: emptyList<Any>()).forEach { i -> assertTrue(((i as Map<*, *>)["createSql"] as String).replace("\${TABLE_NAME}", t) in sql, t) }
        }
    }

    @Test
    fun profileColumnsMatchEntity() {
        val added = ran().filter { it.startsWith("ALTER TABLE `profile`") }.map { Regex("`(\\w+)` \\w+$").find(it)!!.groupValues[1] }
        val want = Regex("`(\\w+)` [A-Z]+").findAll((entities().getValue("profile")["createSql"] as String).substringAfter('(')).map { it.groupValues[1] }.toList()
        assertEquals(want, old + added)
    }

    @Test
    fun onlyAdditiveStatements() {
        val sql = ran()
        assertEquals(18, sql.size)
        assertTrue(sql.all { it.startsWith("ALTER TABLE `profile` ADD COLUMN") || it.startsWith("CREATE TABLE IF NOT EXISTS") || it.startsWith("CREATE INDEX IF NOT EXISTS") })
    }
}
