package app.companion.bridge

import android.app.Application
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Process

object Shell {
    private const val PKG = "com.android.shell"

    fun context(): Context {
        val at = Class.forName("android.app.ActivityThread")
        val thread = at.getMethod("systemMain").invoke(null)
        val bd = Class.forName("android.app.ActivityThread\$AppBindData")
        val bind = bd.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val info = android.content.pm.ApplicationInfo().apply { packageName = PKG }
        bd.getDeclaredField("appInfo").apply { isAccessible = true }.set(bind, info)
        at.getDeclaredField("mBoundApplication").apply { isAccessible = true }.set(thread, bind)
        val sys = at.getMethod("getSystemContext").invoke(thread) as Context
        val ctx = object : ContextWrapper(sys) {
            override fun getPackageName() = PKG
            override fun getOpPackageName() = PKG
            override fun getAttributionSource() = AttributionSource.Builder(Process.myUid()).setPackageName(PKG).build()
        }
        val app = Application()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply { isAccessible = true }.invoke(app, ctx)
        at.getDeclaredField("mInitialApplication").apply { isAccessible = true }.set(thread, app)
        return ctx
    }

    fun audio(ctx: Context): AudioManager = AudioManager::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }.newInstance(ctx)
}
