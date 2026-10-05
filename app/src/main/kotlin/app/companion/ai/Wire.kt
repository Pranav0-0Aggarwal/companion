package app.companion.ai

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class Link : ServiceConnection {
    val ready = CountDownLatch(1)

    @Volatile var binder: IBinder? = null

    @Volatile var dead = false

    override fun onServiceConnected(n: ComponentName, b: IBinder) {
        binder = b
        ready.countDown()
    }

    override fun onServiceDisconnected(n: ComponentName) {
        dead = true
    }

    override fun onBindingDied(n: ComponentName) {
        dead = true
    }
}

internal fun ask(to: Messenger, what: Int, data: Bundle, secs: Long): Bundle? {
    val got = AtomicReference<Bundle?>()
    val latch = CountDownLatch(1)
    val back = Messenger(
        Handler(Looper.getMainLooper()) { m ->
            got.set(m.data)
            latch.countDown()
            true
        },
    )
    to.send(Message.obtain(null, what).apply { replyTo = back; this.data = data })
    return if (latch.await(secs, TimeUnit.SECONDS)) got.get() else null
}
