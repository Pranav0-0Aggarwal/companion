package app.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.companion.core.Refile
import app.companion.core.Rules
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun profile(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun profileNow(): Profile?

    @Upsert
    suspend fun save(p: Profile)

    @Insert
    suspend fun add(i: Item): Long

    @Update
    suspend fun update(i: Item)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: Long): Item?

    @Query("SELECT * FROM items WHERE kind IN (:kinds) AND paise = :paise AND at BETWEEN :lo AND :hi")
    suspend fun near(kinds: List<String>, paise: Long, lo: Long, hi: Long): List<Item>

    @Query("SELECT * FROM items WHERE kind = 'Otp' AND code = :code AND at BETWEEN :lo AND :hi")
    suspend fun nearCode(code: String, lo: Long, hi: Long): List<Item>

    @Insert
    suspend fun link(l: Link)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun fold(f: Fold)

    @Query("DELETE FROM folds")
    suspend fun unfold()

    @Query("SELECT COALESCE(MAX(id), 0) FROM links")
    suspend fun topLink(): Long

    @Query(
        "SELECT EXISTS(SELECT 1 FROM links WHERE src = 'Sms' AND sender = :sender AND id <= :cap AND (at BETWEEN :lo1 AND :hi1 OR at BETWEEN :lo2 AND :hi2)) " +
            "OR EXISTS(SELECT 1 FROM folds WHERE sender = :sender AND (at BETWEEN :lo1 AND :hi1 OR at BETWEEN :lo2 AND :hi2))",
    )
    suspend fun seen(sender: String, lo1: Long, hi1: Long, lo2: Long, hi2: Long, cap: Long): Boolean

    @Query("SELECT * FROM links WHERE itemId IN (:ids) ORDER BY at")
    fun links(ids: List<Long>): Flow<List<Link>>

    @Query("SELECT * FROM items WHERE kind = 'Otp' AND expires > :now ORDER BY at DESC")
    fun otps(now: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE kind = 'Otp' AND expires > :now ORDER BY at DESC")
    suspend fun otpsNow(now: Long): List<Item>

    @Query("DELETE FROM items WHERE kind = 'Otp' AND expires <= :now")
    suspend fun sweep(now: Long): Int

    @Query("UPDATE items SET body = NULL WHERE body IS NOT NULL AND at < :before")
    suspend fun blank(before: Long): Int

    @Query("SELECT * FROM items WHERE kind IN ('Debit', 'Credit', 'CardSpend') ORDER BY at DESC LIMIT 3000")
    fun money(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE kind IN ('Bill', 'Statement') AND state != 'paid' ORDER BY due IS NULL, due, at DESC")
    fun bills(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE kind IN ('Bill', 'Statement') AND state != 'paid' ORDER BY due IS NULL, due, at DESC")
    suspend fun billsNow(): List<Item>

    @Query("SELECT * FROM items WHERE state = 'ask' AND kind != 'Spam' ORDER BY at DESC")
    fun asks(): Flow<List<Item>>

    @Query(
        "SELECT * FROM items WHERE kind != 'Otp' AND (state IN ('ask', 'check') OR at > :since) " +
            "ORDER BY CASE state WHEN 'ask' THEN 0 WHEN 'check' THEN 1 ELSE 2 END, at DESC LIMIT 200",
    )
    fun inbox(since: Long): Flow<List<Item>>

    @Query(
        "SELECT items.* FROM items JOIN items_fts ON items.id = items_fts.rowid " +
            "WHERE items_fts MATCH :q AND items.kind != 'Otp' ORDER BY items.at DESC LIMIT 100",
    )
    fun search(q: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE start IS NOT NULL AND start > :now AND at > :since AND (ping & 8) = 0 ORDER BY start LIMIT 5")
    fun suggested(now: Long, since: Long): Flow<List<Item>>

    @Query("UPDATE items SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: String)

    @Query("UPDATE items SET category = :category, state = 'settled' WHERE id = :id")
    suspend fun file(id: Long, category: String)

    @Query("UPDATE items SET kind = :kind, state = 'settled', code = NULL, expires = NULL WHERE id = :id")
    suspend fun retype(id: Long, kind: String)

    @Query("UPDATE items SET ping = :ping WHERE id = :id")
    suspend fun setPing(id: Long, ping: Int)

    @Query("SELECT * FROM cards ORDER BY id")
    fun cards(): Flow<List<Card>>

    @Insert
    suspend fun addCard(c: Card)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: Long)

    @Query("INSERT INTO tally(day, key, n) VALUES(:day, :key, 1) ON CONFLICT(day, key) DO UPDATE SET n = n + 1")
    suspend fun count(day: Long, key: String)

    @Query("SELECT * FROM tally WHERE day >= :from")
    fun tally(from: Long): Flow<List<Tally>>

    @Query("SELECT category FROM learned WHERE key = :key")
    suspend fun learned(key: String): String?

    @Upsert
    suspend fun learn(l: Learned)

    @Insert
    suspend fun correction(c: Correction)

    @Query("SELECT COUNT(*) FROM corrections WHERE at > :since")
    fun corrections(since: Long): Flow<Int>

    @Query(
        "SELECT (SELECT sender FROM links WHERE itemId = i.id ORDER BY at, id LIMIT 1) AS sender, i.title AS title, i.note AS note, i.body AS body, " +
            "c.task AS task, c.model AS model, c.modelProb AS prob, c.chosen AS chosen " +
            "FROM corrections c JOIN items i ON i.id = c.itemId ORDER BY c.at, c.id",
    )
    suspend fun exportRows(): List<ExportRow>

    @Query("SELECT * FROM rules WHERE hash = :hash AND count >= ${Rules.MIN}")
    suspend fun ruled(hash: String): List<TemplateRule>

    @Query("SELECT * FROM rules WHERE hash = :hash AND task = :task")
    suspend fun rule(hash: String, task: String): TemplateRule?

    @Upsert
    suspend fun putRule(r: TemplateRule)

    @Query(
        "SELECT r.hash AS hash, r.task AS task, r.label AS label, r.count AS count, " +
            "(SELECT title FROM items WHERE tpl = r.hash ORDER BY at DESC LIMIT 1) AS title FROM rules r ORDER BY r.count DESC, r.hash, r.task",
    )
    fun ruleRows(): Flow<List<RuleRow>>

    @Query("DELETE FROM rules WHERE hash = :hash AND task = :task")
    suspend fun deleteRule(hash: String, task: String)

    @Query("SELECT * FROM marks WHERE job = :job")
    suspend fun mark(job: String): Mark?

    @Upsert
    suspend fun putMark(m: Mark)

    @Query("UPDATE marks SET pos = :pos, done = :done, total = :total, moved = :moved, ask = :ask, skip = :skip WHERE job = :job")
    suspend fun step(job: String, pos: Long, done: Int, total: Int, moved: Int, ask: Int, skip: Int)

    @Query("UPDATE marks SET paused = :paused, why = NULL WHERE job = :job")
    suspend fun setPaused(job: String, paused: Boolean)

    @Query("UPDATE marks SET why = :why WHERE job = :job")
    suspend fun setWhy(job: String, why: String)

    @Query("DELETE FROM marks WHERE job = :job")
    suspend fun dropMark(job: String)

    @Query("SELECT COALESCE(MAX(id), 0) FROM items")
    suspend fun top(): Long

    @Query("SELECT COUNT(*) FROM items WHERE id > :after AND id <= :cap AND kind != 'Otp' AND src IN (${Refile.SOURCES}) AND (body IS NOT NULL OR src = 'Sms')")
    suspend fun stale(after: Long, cap: Long): Int

    @Query("SELECT * FROM items WHERE id > :after AND id <= :cap AND kind != 'Otp' AND src IN (${Refile.SOURCES}) AND (body IS NOT NULL OR src = 'Sms') ORDER BY id LIMIT :n")
    suspend fun staleBatch(after: Long, cap: Long, n: Int): List<Item>

    @Query("SELECT DISTINCT itemId FROM corrections WHERE itemId IN (:ids)")
    suspend fun corrected(ids: List<Long>): List<Long>

    @Query("SELECT DISTINCT hash FROM rules WHERE hash IN (:hashes) AND count >= ${Rules.MIN}")
    suspend fun taught(hashes: List<String>): List<String>

    @Query("SELECT itemId, sender FROM links WHERE itemId IN (:ids) ORDER BY at, id")
    suspend fun senders(ids: List<Long>): List<Sender>

    @Query("UPDATE items SET kind = :kind, tags = :tags, category = :category, state = :state, conf = :conf, model = :model, mprob = :mprob WHERE id = :id AND state != 'paid'")
    suspend fun refile(id: Long, kind: String, tags: String?, category: String?, state: String, conf: Float, model: String?, mprob: Float?)

    @Query("SELECT * FROM tasks WHERE gone = 0 ORDER BY done, remindAt IS NULL, remindAt, due IS NULL, due, id DESC")
    fun tasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE gone = 0 AND done = 0 AND remindAt IS NOT NULL AND remindAt > :now ORDER BY remindAt LIMIT 1")
    fun nextTask(now: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE gone = 0 AND done = 0 AND remindAt IS NOT NULL AND remindAt > :now ORDER BY remindAt")
    suspend fun pending(now: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Long): Task?

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<Task>

    @Insert
    suspend fun addTask(t: Task): Long

    @Update
    suspend fun updateTask(t: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun purgeTask(id: Long)
}
