package com.psd.floramind.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.psd.floramind.model.Question
import com.psd.floramind.model.StudyRecord

@Database(
    entities = [Question::class, StudyRecord::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)   // 添加这一行
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun studyRecordDao(): StudyRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "floramind.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}