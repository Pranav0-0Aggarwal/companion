package app.companion.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Profile::class, Item::class, ItemFts::class, Link::class, Card::class, Tally::class, Learned::class, Task::class],
    version = 1,
    exportSchema = false,
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
}
