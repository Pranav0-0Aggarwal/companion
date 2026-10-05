package app.companion.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, Item::class, ItemFts::class, Link::class, Fold::class, Card::class, Tally::class, Learned::class, Task::class, Correction::class, TemplateRule::class, Mark::class, SenderRule::class, Moved::class, Alias::class, Dismissed::class],
    version = 8,
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

private val fts = listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")

val Migrate2to3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fts.forEach { db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_items_fts_$it") }
        db.execSQL("DROP TABLE IF EXISTS `items_fts`")
        db.execSQL("ALTER TABLE `items` ADD COLUMN `body` TEXT")
        db.execSQL("ALTER TABLE `items` ADD COLUMN `tags` TEXT")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `keep` INTEGER NOT NULL DEFAULT 365")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `items_fts` USING FTS4(`title` TEXT NOT NULL, `merchant` TEXT, `note` TEXT NOT NULL, `bank` TEXT, `category` TEXT, `body` TEXT, content=`items`)")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_items_fts_BEFORE_UPDATE BEFORE UPDATE ON `items` BEGIN DELETE FROM `items_fts` WHERE `docid`=OLD.`rowid`; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_items_fts_BEFORE_DELETE BEFORE DELETE ON `items` BEGIN DELETE FROM `items_fts` WHERE `docid`=OLD.`rowid`; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_items_fts_AFTER_UPDATE AFTER UPDATE ON `items` BEGIN INSERT INTO `items_fts`(`docid`, `title`, `merchant`, `note`, `bank`, `category`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`merchant`, NEW.`note`, NEW.`bank`, NEW.`category`, NEW.`body`); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_items_fts_AFTER_INSERT AFTER INSERT ON `items` BEGIN INSERT INTO `items_fts`(`docid`, `title`, `merchant`, `note`, `bank`, `category`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`merchant`, NEW.`note`, NEW.`bank`, NEW.`category`, NEW.`body`); END")
        db.execSQL("INSERT INTO `items_fts`(`items_fts`) VALUES('rebuild')")
    }
}

val Migrate3to4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `model` TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS `marks` (`job` TEXT NOT NULL, `pos` INTEGER NOT NULL, `cap` INTEGER NOT NULL, `done` INTEGER NOT NULL, `total` INTEGER NOT NULL, `moved` INTEGER NOT NULL, `ask` INTEGER NOT NULL, `skip` INTEGER NOT NULL, `sha` TEXT, `battery` INTEGER NOT NULL, `paused` INTEGER NOT NULL, `why` TEXT, PRIMARY KEY(`job`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_src_sender_at` ON `links` (`src`, `sender`, `at`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `folds` (`sender` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`sender`, `at`))")
    }
}

val Migrate4to5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `items` ADD COLUMN `dup` INTEGER")
    }
}

val Migrate5to6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `items` ADD COLUMN `flow` TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sender_rules` (`sender` TEXT NOT NULL, `label` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`sender`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `moved` (`key` TEXT NOT NULL, PRIMARY KEY(`key`))")
    }
}

val Migrate6to7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `aliases` (`raw` TEXT NOT NULL, `name` TEXT NOT NULL, `auto` INTEGER NOT NULL, PRIMARY KEY(`raw`))")
    }
}

val Migrate7to8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `dismissed` (`key` TEXT NOT NULL, PRIMARY KEY(`key`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_tpl` ON `items` (`tpl`)")
    }
}
