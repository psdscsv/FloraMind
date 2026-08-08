package com.psd.floramind.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.psd.floramind.model.Question
import com.psd.floramind.model.StudyRecord

@Database(
    entities = [Question::class, StudyRecord::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun studyRecordDao(): StudyRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 迁移：从版本1到2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建新表（包含所有列，确保 NOT NULL 约束与实体类一致）
                db.execSQL("CREATE TABLE questions_new (" +
                        "packageName TEXT NOT NULL, " +
                        "id INTEGER NOT NULL, " +
                        "type INTEGER NOT NULL, " +   // 对应 QuestionType 非空
                        "stem TEXT NOT NULL, " +      // 题干非空
                        "options TEXT, " +
                        "correctAnswer TEXT, " +
                        "keywords TEXT, " +
                        "imageName TEXT, " +
                        "labels TEXT, " +
                        "PRIMARY KEY(packageName, id))")

                // 2. 迁移数据，为旧数据统一赋值 packageName = '内置题库'
                db.execSQL("INSERT INTO questions_new(" +
                        "packageName, id, type, stem, options, correctAnswer, keywords, imageName, labels) " +
                        "SELECT '内置题库', id, type, stem, options, correctAnswer, keywords, imageName, labels " +
                        "FROM questions")

                // 3. 删除旧表
                db.execSQL("DROP TABLE questions")

                // 4. 重命名新表
                db.execSQL("ALTER TABLE questions_new RENAME TO questions")

                // 5. 为 study_records 添加 packageName 列（默认值 '内置题库'）
                db.execSQL("ALTER TABLE study_records ADD COLUMN packageName TEXT NOT NULL DEFAULT '内置题库'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "floramind.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // 开发阶段：如果迁移失败则重建（可避免反复崩溃）
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}