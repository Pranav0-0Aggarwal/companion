package app.companion

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Process
import androidx.work.WorkManager
import app.companion.ai.BertScorer
import app.companion.chat.ChatEngine
import app.companion.ai.DecideScorer
import app.companion.ai.DocIngest
import app.companion.ai.Governor
import app.companion.ai.ModelJobs
import app.companion.ai.Models
import app.companion.ai.NuExtractor
import app.companion.ai.Pending
import app.companion.ai.Processing
import app.companion.ai.Scorers
import app.companion.ui.Proc
import app.companion.core.Merchant
import app.companion.core.NoExtractor
import app.companion.core.Planner
import app.companion.core.RulePlanner
import app.companion.core.Refine
import app.companion.core.RulesClassifier
import app.companion.data.Repo
import app.companion.data.Upgrade
import app.companion.data.Vault
import app.companion.ingest.Gmail
import app.companion.ingest.Hooks
import app.companion.ingest.Ingest
import app.companion.system.Alarms
import app.companion.system.Export
import app.companion.system.GTasks
import app.companion.system.Live
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class Services(val app: Application) {
    val db by lazy { Vault.open(app) }
    val repo by lazy { Repo(db, app) }
    val gov by lazy { Governor(app) }
    val scorer by lazy { Scorers(app, DecideScorer(app, gov), BertScorer(app, gov)) }
    val rules by lazy { RulesClassifier() }
    val refine by lazy { Refine(rules, NoExtractor, scorer::calibration, scorer) }
    val nux by lazy { NuExtractor(app, gov) }
    val pending by lazy { Pending(app, repo, refine, gov, scorer, nux) }
    val planner: Planner by lazy { RulePlanner() }
    val chat by lazy { ChatEngine(app, repo, gov) }
    val docs by lazy { DocIngest(repo, nux) }
    val hooks by lazy { Hooks(app, repo, { chat.foods }, docs) }
    val ingest by lazy { Ingest(app, repo, rules, pending, hooks) }
    val gmail by lazy { Gmail(repo, ingest) }
    val gtasks by lazy { GTasks(app, repo) }

    fun wipe() {
        runBlocking(Dispatchers.IO) { repo.allTasks().forEach { Alarms.cancel(app, it.id) } }
        WorkManager.getInstance(app).cancelAllWork()
        ModelJobs.kill(app)
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
        Merchant.source { assets.open("brands.json").use { it.readBytes().decodeToString() } }
        if (Process.isIsolated()) return
        Live.boot(this)
        Processing.boot(this)
        Proc.watch(this)
        Export.sweep(this)
        Upgrade.boot(this)
        Thread { Models.purge(this) }.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!Process.isIsolated()) sl.gov.trim(level)
    }
}

val Context.sl get() = (applicationContext as CompanionApp).sl
