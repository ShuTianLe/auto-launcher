package com.stl.autolauncher.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY COALESCE(nextTriggerAtMillis, 9223372036854775807) ASC, hour ASC, minute ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE nextTriggerAtMillis IS NOT NULL AND nextTriggerAtMillis <= :nowMillis AND enabled = 1 ORDER BY nextTriggerAtMillis ASC")
    suspend fun getDueTasks(nowMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    fun observeById(taskId: Long): Flow<TaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity): Long

    @Query("UPDATE tasks SET enabled = :enabled, updatedAtMillis = :updatedAtMillis WHERE id = :taskId")
    suspend fun setEnabled(taskId: Long, enabled: Boolean, updatedAtMillis: Long)

    @Query(
        """
        UPDATE tasks
        SET nextTriggerAtMillis = :nextTriggerAtMillis,
            scheduledDate = :scheduledDate,
            scheduledOffsetMinutes = :scheduledOffsetMinutes,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :taskId
        """
    )
    suspend fun updateSchedule(
        taskId: Long,
        nextTriggerAtMillis: Long?,
        scheduledDate: String?,
        scheduledOffsetMinutes: Int?,
        updatedAtMillis: Long,
    )

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: Long)
}

@Dao
interface TaskSkipDateDao {
    @Query("SELECT * FROM task_skip_dates WHERE taskId = :taskId ORDER BY date ASC")
    fun observeByTaskId(taskId: Long): Flow<List<TaskSkipDateEntity>>

    @Query("SELECT * FROM task_skip_dates WHERE date >= :startDate ORDER BY date ASC, taskId ASC")
    fun observeFromDate(startDate: String): Flow<List<TaskSkipDateEntity>>

    @Query("SELECT date FROM task_skip_dates WHERE taskId = :taskId ORDER BY date ASC")
    suspend fun getDatesByTaskId(taskId: Long): List<String>

    @Query("SELECT COUNT(*) FROM task_skip_dates WHERE taskId = :taskId AND date = :date")
    suspend fun countByTaskAndDate(taskId: Long, date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skipDate: TaskSkipDateEntity)

    @Query("DELETE FROM task_skip_dates WHERE taskId = :taskId AND date = :date")
    suspend fun delete(taskId: Long, date: String)

    @Query("DELETE FROM task_skip_dates WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Long)

    @Query("DELETE FROM task_skip_dates WHERE date < :beforeDate")
    suspend fun prunePastDates(beforeDate: String)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM execution_logs ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExecutionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExecutionLogEntity)

    @Query(
        "DELETE FROM execution_logs WHERE id NOT IN (SELECT id FROM execution_logs ORDER BY createdAtMillis DESC LIMIT :keepCount)"
    )
    suspend fun prune(keepCount: Int)
}

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holiday_entries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): HolidayEntryEntity?

    @Query("SELECT COUNT(*) FROM holiday_entries WHERE date >= :startDate AND date <= :endDate")
    suspend fun countInRange(startDate: String, endDate: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<HolidayEntryEntity>)
}

@Database(
    entities = [TaskEntity::class, TaskSkipDateEntity::class, ExecutionLogEntity::class, HolidayEntryEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class AutoLauncherDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskSkipDateDao(): TaskSkipDateDao
    abstract fun logDao(): LogDao
    abstract fun holidayDao(): HolidayDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_skip_dates` (
                        `taskId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`, `date`),
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_skip_dates_taskId` ON `task_skip_dates` (`taskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_skip_dates_date` ON `task_skip_dates` (`date`)")
            }
        }

        fun build(context: Context): AutoLauncherDatabase {
            return Room.databaseBuilder(
                context,
                AutoLauncherDatabase::class.java,
                "auto_launcher.db",
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
