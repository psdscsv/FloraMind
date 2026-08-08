package com.psd.floramind.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.psd.floramind.model.Question

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<Question>)

    @Query("SELECT * FROM questions")
    suspend fun getAll(): List<Question>

    @Query("SELECT * FROM questions WHERE packageName = :packageName AND id = :id")
    suspend fun getById(packageName: String, id: Int): Question?

    @Query("SELECT DISTINCT packageName FROM questions")
    suspend fun getAllPackageNames(): List<String>

    @Query("SELECT * FROM questions WHERE packageName = :packageName")
    suspend fun getQuestionsByPackage(packageName: String): List<Question>

    @Query("SELECT COUNT(*) FROM questions WHERE packageName = :packageName")
    suspend fun getQuestionCountByPackage(packageName: String): Int

    @Query("DELETE FROM questions WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}