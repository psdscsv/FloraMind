package com.psd.floramind.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.psd.floramind.model.StudyRecord

@Dao
interface StudyRecordDao {
    @Insert
    suspend fun insert(record: StudyRecord)

    @Query("SELECT * FROM study_records WHERE questionId = :questionId ORDER BY timestamp DESC")
    suspend fun getRecordsForQuestion(questionId: Int): List<StudyRecord>

    @Query("SELECT * FROM study_records WHERE isCorrect = 0 ORDER BY timestamp DESC")
    suspend fun getMistakes(): List<StudyRecord>

    // ===== 新增：按题库包查询错题 =====
    @Query("SELECT * FROM study_records WHERE isCorrect = 0 AND packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getMistakesByPackage(packageName: String): List<StudyRecord>

    // 错题本一级栏目：所有存在错题记录的题库包
    @Query("SELECT DISTINCT packageName FROM study_records WHERE isCorrect = 0")
    suspend fun getDistinctMistakePackages(): List<String>

    // 某包错题数量
    @Query("SELECT COUNT(*) FROM study_records WHERE isCorrect = 0 AND packageName = :packageName")
    suspend fun getMistakeCountByPackage(packageName: String): Int

    // 删除单条错题记录
    @Query("DELETE FROM study_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 删除题库包时同步删除其所有记录
    @Query("DELETE FROM study_records WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}