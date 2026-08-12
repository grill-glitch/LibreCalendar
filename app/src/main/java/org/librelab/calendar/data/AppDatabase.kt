package org.librelab.calendar.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** 事件重复规则 */
enum class RepeatRule(val label: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    YEARLY("每年");

    companion object {
        fun fromName(name: String?): RepeatRule =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** 提醒时间偏移(分钟, 负数=提前) */
enum class ReminderOffset(val minutes: Int, val label: String) {
    NONE(0, "不提醒"),
    AT_TIME(0, "事件开始时"),
    MIN_5(-5, "提前 5 分钟"),
    MIN_15(-15, "提前 15 分钟"),
    MIN_30(-30, "提前 30 分钟"),
    HOUR_1(-60, "提前 1 小时"),
    HOUR_2(-120, "提前 2 小时"),
    DAY_1(-1440, "提前 1 天"),
    DAY_2(-2880, "提前 2 天"),
    WEEK_1(-10080, "提前 1 周");

    companion object {
        fun fromMinutes(minutes: Int): ReminderOffset =
            entries.firstOrNull { it.minutes == minutes } ?: NONE
    }
}

@Entity(tableName = "events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val location: String = "",
    /** 开始时间(epoch millis, 本地时区) */
    val startTime: Long,
    /** 结束时间(epoch millis) */
    val endTime: Long,
    /** 是否全天事件 */
    val allDay: Boolean = false,
    /** 重复规则 (RepeatRule.name) */
    val repeat: String = RepeatRule.NONE.name,
    /** 提醒偏移(分钟) */
    val reminderMinutes: Int = ReminderOffset.NONE.minutes,
    /** 事件颜色索引 (遗留字段, 新逻辑用 customColor) */
    val colorIndex: Int = 0,
    /** 自定义颜色 (ARGB, null=未设置→动态取色) */
    val customColor: Int? = null,
    /** 关联标签 id (null=无标签; 标签有颜色则优先于 customColor) */
    val labelId: Long? = null,
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),
)

/** 标签: 名称 + 颜色 + 图标 (图标为 Material Symbols 名) */
@Entity(tableName = "labels")
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB 颜色; 有颜色时事件继承该颜色 */
    val color: Int? = null,
    /** 图标资源名 (Material Symbols outlined, 如 "work"), null=无图标 */
    val icon: String? = null,
    /** 排序 (越小越靠前) */
    val sortOrder: Int = 0,
)

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): CalendarEvent?

    @Query("SELECT * FROM events WHERE startTime >= :from AND startTime < :to ORDER BY startTime")
    fun observeRange(from: Long, to: Long): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM events WHERE startTime >= :from AND startTime < :to ORDER BY startTime")
    suspend fun getRange(from: Long, to: Long): List<CalendarEvent>

    @Query("SELECT * FROM events ORDER BY startTime")
    fun observeAll(): Flow<List<CalendarEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEvent): Long

    @Query("UPDATE events SET title=:title, description=:description, location=:location, startTime=:startTime, endTime=:endTime, allDay=:allDay, repeat=:repeat, reminderMinutes=:reminderMinutes, colorIndex=:colorIndex, customColor=:customColor, labelId=:labelId WHERE id=:id")
    suspend fun update(
        id: Long, title: String, description: String, location: String,
        startTime: Long, endTime: Long, allDay: Boolean, repeat: String,
        reminderMinutes: Int, colorIndex: Int, customColor: Int?, labelId: Long?,
    )

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Label>>

    @Query("SELECT * FROM labels ORDER BY sortOrder, id")
    suspend fun getAll(): List<Label>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: Label): Long

    @Query("UPDATE labels SET name=:name, color=:color, icon=:icon, sortOrder=:sortOrder WHERE id=:id")
    suspend fun update(id: Long, name: String, color: Int?, icon: String?, sortOrder: Int)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [CalendarEvent::class, Label::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun labelDao(): LabelDao

    companion object {
        /** v1 → v2: events 加 customColor/labelId 列, 新建 labels 表 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN customColor INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN labelId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS labels (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "color INTEGER, " +
                        "icon TEXT, " +
                        "sortOrder INTEGER NOT NULL)"
                )
            }
        }
    }
}
