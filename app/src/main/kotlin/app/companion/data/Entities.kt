package app.companion.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import app.companion.core.Progress
import app.companion.core.Refile
import app.companion.core.Source

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val first: String = "",
    val call: String = "",
    val city: String = "",
    val payDay: Int? = null,
    val budget: Long? = null,
    val sms: Boolean = true,
    val notif: Boolean = true,
    val mail: Boolean = false,
    val wa: Boolean = true,
    val ig: Boolean = false,
    val lock: Boolean = false,
    val done: Boolean = false,
    val vip: String = "",
    val hist: String? = null,
    val imported: Boolean = false,
    val cal: Long? = null,
    val gtasks: Boolean = false,
    val since: String? = null,
    @ColumnInfo(defaultValue = "365") val keep: Int = 365,
    val model: String? = null,
) {
    val name get() = call.ifBlank { first }
    val vips get() = vip.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun on(s: Source) = when (s) {
        Source.Sms -> sms
        Source.Notif -> notif
        Source.Mail -> mail
        Source.Wa -> wa
        Source.Ig -> ig
    }
}

@Entity(tableName = "items", indices = [Index("kind"), Index("at"), Index("state")])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val at: Long,
    val src: String,
    val title: String,
    val paise: Long = 0,
    val currency: String = "INR",
    val last4: String? = null,
    val bank: String? = null,
    val merchant: String? = null,
    val mode: String? = null,
    val category: String? = null,
    val due: Long? = null,
    val minPaise: Long? = null,
    val code: String? = null,
    val expires: Long? = null,
    val note: String = "",
    val state: String,
    val conf: Float = 1f,
    val ping: Int = 0,
    val start: Long? = null,
    val end: Long? = null,
    val tpl: String? = null,
    val model: String? = null,
    val mprob: Float? = null,
    val body: String? = null,
    val tags: String? = null,
    val dup: Long? = null,
    val flow: String? = null,
)

@Fts4(contentEntity = Item::class)
@Entity(tableName = "items_fts")
data class ItemFts(
    val title: String,
    val merchant: String?,
    val note: String,
    val bank: String?,
    val category: String?,
    val body: String?,
)

@Entity(
    tableName = "links",
    foreignKeys = [ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index("src", "sender", "at")],
)
data class Link(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val src: String,
    val sender: String,
    val at: Long,
)

@Entity(tableName = "folds", primaryKeys = ["sender", "at"])
data class Fold(val sender: String, val at: Long)

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bank: String,
    val nick: String,
    val last4: String,
    val stmtDay: Int,
    val dueDay: Int,
    val limit: Long?,
)

@Entity(tableName = "tally", primaryKeys = ["day", "key"])
data class Tally(val day: Long, @ColumnInfo(name = "key") val key: String, val n: Int)

@Entity(tableName = "learned")
data class Learned(@PrimaryKey val key: String, val category: String)

@Entity(tableName = "corrections", indices = [Index("itemId"), Index("at")])
data class Correction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val at: Long,
    val task: String,
    val model: String?,
    val modelProb: Float?,
    val chosen: String,
    val src: String,
)

@Entity(tableName = "rules", primaryKeys = ["hash", "task"])
data class TemplateRule(val hash: String, val task: String, val label: String, val count: Int)

data class RuleRow(val hash: String, val task: String, val label: String, val count: Int, val hits: Int, val title: String?, val note: String?, val body: String?, val sender: String?)

@Entity(tableName = "sender_rules")
data class SenderRule(@PrimaryKey val sender: String, val label: String, val count: Int)

@Entity(tableName = "aliases")
data class Alias(@PrimaryKey val raw: String, val name: String, val auto: Boolean)

@Entity(tableName = "moved")
data class Moved(@PrimaryKey val key: String)

data class ExportRow(val sender: String?, val title: String, val note: String, val body: String?, val task: String, val model: String?, val prob: Float?, val chosen: String)

@Entity(tableName = "tasks", indices = [Index("done"), Index("remindAt")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val due: Long? = null,
    val remindAt: Long? = null,
    val repeat: String? = null,
    val done: Boolean = false,
    val ref: String? = null,
    val gid: String? = null,
    val upd: Long = 0,
    val gone: Boolean = false,
)

@Entity(tableName = "marks")
data class Mark(
    @PrimaryKey val job: String,
    val pos: Long,
    val cap: Long,
    val done: Int,
    val total: Int,
    val moved: Int,
    val ask: Int,
    val skip: Int,
    val sha: String?,
    val battery: Boolean,
    val paused: Boolean,
    val why: String? = null,
) {
    val held get() = paused || why != null

    fun progress() = Progress(pos, cap, done, total, moved, ask, skip, sha)
}

fun Progress.mark(job: String, battery: Boolean, paused: Boolean = false) = Mark(job, pos, cap, done, total, moved, ask, skip, sha, battery, paused)

data class Sender(val itemId: Long, val sender: String)

object Src {
    const val ASK = "ask"
    const val EDIT = "edit"
    const val NOT_OTP = "not_otp"
    const val NOT_SPAM = "not_spam"
}

object State {
    const val ASK = Refile.ASK
    const val CHECK = Refile.CHECK
    const val SETTLED = Refile.SETTLED
    const val PAID = Refile.PAID
}
