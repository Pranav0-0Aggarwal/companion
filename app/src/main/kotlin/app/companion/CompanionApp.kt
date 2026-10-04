package app.companion

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Process
import androidx.work.WorkManager
import app.companion.core.Classifier
import app.companion.core.RulesClassifier
import app.companion.data.Repo
import app.companion.data.Vault
import app.companion.system.Live

class Services(val app: Application) {
    val db by lazy { Vault.open(app) }
    val repo by lazy { Repo(db) }
    val classifier: Classifier = RulesClassifier()

    fun wipe() {
        WorkManager.getInstance(app).cancelAllWork()
        app.getSystemService(NotificationManager::class.java).cancelAll()
        db.close()
        Vault.wipe(app)
        Process.killProcess(Process.myPid())
    }
}

class CompanionApp : Application() {
    val sl by lazy { Services(this) }

    override fun onCreate() {
        super.onCreate()
        Live.boot(this)
    }
}

val Context.sl get() = (applicationContext as CompanionApp).sl
