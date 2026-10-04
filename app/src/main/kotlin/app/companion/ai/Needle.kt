package app.companion.ai

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import app.companion.core.Plan
import app.companion.core.Planner
import app.companion.core.RulePlanner
import app.companion.core.Validator
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

object NeedleWire {
    const val LOAD = 1
    const val PLAN = 2
    const val REPLY = 3
    const val MODEL = "model"
    const val TEXT = "text"
    const val SCHEMA = "schema"
    const val JSON = "json"
    const val ERROR = "error"

    private fun tool(name: String, desc: String, vararg props: String) =
        """{"name":"$name","description":"$desc","parameters":{"type":"object","properties":{${props.joinToString(",") { "\"$it\":{\"type\":\"string\"}" }}}}}"""

    val schema = listOf(
        tool("sumSpend", "Total spent, optional category merchant last4, between start and end (YYYY-MM-DD)", "category", "merchant", "last4", "start", "end"),
        tool("listTxns", "List transactions, same filters, limit up to 50", "category", "merchant", "last4", "start", "end", "limit"),
        tool("listBills", "List unpaid bills due between start and end", "start", "end"),
        tool("topMerchants", "Top merchants by spend between start and end", "start", "end", "n"),
        tool("createReminder", "Create a reminder at a local date time like 2026-10-05T09:00", "title", "at"),
        tool("createEvent", "Create a calendar event with local date times", "title", "start", "end"),
    ).joinToString(",", "[", "]")
}

class NeedleService : Service() {
    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { m ->
            m.replyTo?.send(Message.obtain(null, NeedleWire.REPLY).apply { data = Bundle().apply { putString(NeedleWire.ERROR, "not installed") } })
            true
        },
    )

    override fun onBind(intent: Intent): IBinder = messenger.binder
}

class NeedleClient private constructor(private val c: Context, private val conn: ServiceConnection, private val to: Messenger) : Runner {
    override val accel = "CPU"

    fun plan(text: String): String? {
        val latch = CountDownLatch(1)
        var out: String? = null
        val back = Messenger(
            Handler(Looper.getMainLooper()) { m ->
                out = m.data.getString(NeedleWire.JSON)
                latch.countDown()
                true
            },
        )
        to.send(
            Message.obtain(null, NeedleWire.PLAN).apply {
                replyTo = back
                data = Bundle().apply {
                    putString(NeedleWire.TEXT, text)
                    putString(NeedleWire.SCHEMA, NeedleWire.schema)
                }
            },
        )
        latch.await(5, TimeUnit.SECONDS)
        return out
    }

    override fun close() = c.unbindService(conn)

    companion object {
        fun open(c: Context): NeedleClient {
            val latch = CountDownLatch(1)
            var binder: IBinder? = null
            val conn = object : ServiceConnection {
                override fun onServiceConnected(n: ComponentName, b: IBinder) {
                    binder = b
                    latch.countDown()
                }

                override fun onServiceDisconnected(n: ComponentName) = Unit
            }
            check(c.bindService(Intent(c, NeedleService::class.java), conn, Context.BIND_AUTO_CREATE))
            if (!latch.await(3, TimeUnit.SECONDS)) {
                c.unbindService(conn)
                error("bind")
            }
            return NeedleClient(c, conn, Messenger(binder))
        }
    }
}

class NeedlePlanner(private val c: Context, private val gov: Governor, private val rules: Planner = RulePlanner()) : Planner {
    override fun plan(text: String, now: Long): Plan {
        val base = rules.plan(text, now)
        if (base.explicit && base.queries.isNotEmpty() || !Models.installed(c, Models.needle)) return base
        val json = try {
            runBlocking { gov.run(Models.needle, { NeedleClient.open(c) }) { it.plan(text) } }
        } catch (_: Exception) {
            null
        } ?: return base
        val qs = Validator(now, ZoneId.of("Asia/Kolkata")).parse(json)
        return if (qs.isEmpty()) base else Plan(qs, true)
    }
}
