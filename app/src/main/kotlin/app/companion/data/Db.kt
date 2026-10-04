package app.companion.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, Item::class, ItemFts::class, Link::class, Card::class, Tally::class, Learned::class, Task::class, Correction::class, TemplateRule::class],
    version = 2,
    exportSchema = false,
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
}

val Migrate1to2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `items` ADD COLUMN `tpl` TEXT")
        db.execSQL("ALTER TABLE `items` ADD COLUMN `model` TEXT")
        db.execSQL("ALTER TABLE `items` ADD COLUMN `mprob` REAL")
        db.execSQL("CREATE TABLE IF NOT EXISTS `corrections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, `at` INTEGER NOT NULL, `task` TEXT NOT NULL, `model` TEXT, `modelProb` REAL, `chosen` TEXT NOT NULL, `src` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_corrections_itemId` ON `corrections` (`itemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_corrections_at` ON `corrections` (`at`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `rules` (`hash` TEXT NOT NULL, `task` TEXT NOT NULL, `label` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`hash`, `task`))")
    }
}
